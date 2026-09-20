package microservice.orders_service.saga;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.OrderStatus;
import microservice.orders_service.saga.events.ShippingSuccessEvent;
import microservice.orders_service.saga.services.OrderServiceSaga;
import microservice.orders_service.saga.services.PaymentServiceSaga;
import microservice.orders_service.saga.services.ShippingServiceSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class ChoreographySagaTest {

    private OrderServiceSaga orderService;
    private PaymentServiceSaga paymentService;
    private ShippingServiceSaga shippingService;

    @BeforeEach
    void setUp() {
        EventBroker.clearAll();
        paymentService = new PaymentServiceSaga();
        shippingService = new ShippingServiceSaga();
        orderService = new OrderServiceSaga();

        paymentService.setCustomerBalance("CUST-001", new BigDecimal("1000000")); // 1,000,000 VNĐ
    }

    @Test
    @DisplayName("Kịch bản 1: Luồng xử lý thành công (Happy Path)")
    void testHappyPath() {
        System.out.println("\n========================================================");
        System.out.println("TEST 1: CHOREOGRAPHY SAGA - HAPPY PATH (THÀNH CÔNG)");
        System.out.println("========================================================");

        BigDecimal orderAmount = new BigDecimal("200000");
        String address = "123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh";

        OrderServiceSaga.OrderRecord order = orderService.createOrder("CUST-001", orderAmount, address);

        // Kiểm tra kết quả
        OrderServiceSaga.OrderRecord finalOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.COMPLETED, finalOrder.getStatus(), "Trạng thái cuối cùng của đơn hàng phải là COMPLETED");
        assertNotNull(finalOrder.getPaymentId(), "Mã thanh toán không được null");
        assertNotNull(finalOrder.getWaybillCode(), "Mã vận đơn không được null");
        assertEquals(new BigDecimal("800000"), paymentService.getCustomerBalance("CUST-001"), "Số dư khách hàng còn lại phải là 800,000 VNĐ");

        System.out.println("\n>>> KẾT QUẢ TEST 1: PASSED - Đơn hàng đã COMPLETED, Tiền đã trừ, Vận đơn đã tạo thành công!");
    }

    @Test
    @DisplayName("Kịch bản 2: Luồng bù trừ khi giao hàng thất bại (Shipping Failed)")
    void testShippingFailedCompensationPath() {
        System.out.println("\n========================================================");
        System.out.println("TEST 2: CHOREOGRAPHY SAGA - SHIPPING FAILED & COMPENSATION");
        System.out.println("========================================================");

        BigDecimal orderAmount = new BigDecimal("300000");
        String unsupportedAddress = "UNSUPPORTED - Đảo Xa, Khu vực chưa hỗ trợ giao";

        OrderServiceSaga.OrderRecord order = orderService.createOrder("CUST-001", orderAmount, unsupportedAddress);

        // Kiểm tra kết quả
        OrderServiceSaga.OrderRecord finalOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.CANCELLED, finalOrder.getStatus(), "Trạng thái cuối cùng của đơn hàng phải là CANCELLED do bù trừ thành công");
        assertEquals(new BigDecimal("1000000"), paymentService.getCustomerBalance("CUST-001"), "Số dư khách hàng phải được hoàn đủ 1,000,000 VNĐ");

        System.out.println("\n>>> KẾT QUẢ TEST 2: PASSED - Giao hàng thất bại -> Tự động hoàn tiền -> Hủy đơn hàng thành công!");
    }

    @Test
    @DisplayName("Kịch bản 3: Luồng bù trừ khi Shipping Service bị Timeout 30 giây")
    void testShippingTimeoutCompensationPath() {
        System.out.println("\n========================================================");
        System.out.println("TEST 3: CHOREOGRAPHY SAGA - SHIPPING TIMEOUT 30S & COMPENSATION");
        System.out.println("========================================================");

        shippingService.setSimulateTimeoutMode(true); // Bật chế độ giả lập Shipping Service treo/timeout

        BigDecimal orderAmount = new BigDecimal("400000");
        String address = "456 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh";

        OrderServiceSaga.OrderRecord order = orderService.createOrder("CUST-001", orderAmount, address);

        // Lúc này tiền đã trừ, nhưng Shipping chưa phản hồi -> Đơn ở trạng thái WAITING_FOR_SHIPPING
        OrderServiceSaga.OrderRecord intermediateOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.WAITING_FOR_SHIPPING, intermediateOrder.getStatus());
        assertEquals(new BigDecimal("600000"), paymentService.getCustomerBalance("CUST-001"));

        // Giả lập sau 30s Timeout Scheduler phát hiện và kích hoạt bù trừ
        orderService.triggerShippingTimeout(order.getOrderId());

        // Kiểm tra kết quả sau timeout
        OrderServiceSaga.OrderRecord finalOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.CANCELLED, finalOrder.getStatus(), "Đơn hàng phải bị CANCELLED sau timeout & hoàn tiền");
        assertEquals(new BigDecimal("1000000"), paymentService.getCustomerBalance("CUST-001"), "Số dư phải được hoàn lại 1,000,000 VNĐ");

        // Giả lập sự kiện Shipping ngắt mạng gửi tới TRỄ sau khi đơn đã hủy -> Kiểm tra Idempotency
        System.out.println("\n--- Thử nghiệm gửi sự kiện ShippingSuccessEvent tới TRỄ sau Timeout ---");
        ShippingSuccessEvent lateEvent = ShippingSuccessEvent.builder()
                .orderId(order.getOrderId())
                .waybillCode("WB-LATE-123")
                .carrierName("GHN")
                .timestamp(System.currentTimeMillis())
                .build();
        EventBroker.publish("shipping-success-topic", lateEvent);

        // Xác minh trạng thái đơn vẫn là CANCELLED không bị thay đổi đè lên
        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(order.getOrderId()).getStatus());

        System.out.println("\n>>> KẾT QUẢ TEST 3: PASSED - Timeout 30s kích hoạt hoàn tiền + Hủy đơn + Đảm bảo tính Idempotent thành công!");
    }
}
