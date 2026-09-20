package microservice.orders_service.saga;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.OrderStatus;
import microservice.orders_service.saga.events.ShippingSuccessEvent;
import microservice.orders_service.saga.services.OrderServiceSaga;
import microservice.orders_service.saga.services.PaymentServiceSaga;
import microservice.orders_service.saga.services.ShippingServiceSaga;

import java.math.BigDecimal;

public class SimulationRunner {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("   MÔ PHỎNG VŨ ĐIỆU CHOREOGRAPHY SAGA (ORDER -> PAYMENT -> SHIPPING)      ");
        System.out.println("==========================================================================");

        runScenario1_HappyPath();
        runScenario2_ShippingFailed();
        runScenario3_ShippingTimeout();

        System.out.println("\n==========================================================================");
        System.out.println("   HOÀN THÀNH TẤT CẢ KỊCH BẢN MÔ PHỎNG CHOREOGRAPHY SAGA SUCCESSFUL!     ");
        System.out.println("==========================================================================");
    }

    private static void runScenario1_HappyPath() {
        EventBroker.clearAll();
        PaymentServiceSaga paymentService = new PaymentServiceSaga();
        ShippingServiceSaga shippingService = new ShippingServiceSaga();
        OrderServiceSaga orderService = new OrderServiceSaga();

        paymentService.setCustomerBalance("CUST-001", new BigDecimal("1000000"));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 1: THÀNH CÔNG (HAPPY PATH)");
        System.out.println("--------------------------------------------------------------------------");

        OrderServiceSaga.OrderRecord order = orderService.createOrder(
                "CUST-001",
                new BigDecimal("200000"),
                "123 Đường Lê Lợi, Quận 1, TP. Hồ Chí Minh"
        );

        OrderServiceSaga.OrderRecord result = orderService.getOrder(order.getOrderId());
        System.out.println("\nRESULT 1: Status = " + result.getStatus()
                + " | PaymentId = " + result.getPaymentId()
                + " | WaybillCode = " + result.getWaybillCode()
                + " | Customer Balance = " + paymentService.getCustomerBalance("CUST-001") + " VNĐ");
    }

    private static void runScenario2_ShippingFailed() {
        EventBroker.clearAll();
        PaymentServiceSaga paymentService = new PaymentServiceSaga();
        ShippingServiceSaga shippingService = new ShippingServiceSaga();
        OrderServiceSaga orderService = new OrderServiceSaga();

        paymentService.setCustomerBalance("CUST-001", new BigDecimal("1000000"));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 2: THẤT BẠI KHI GIAO HÀNG (SHIPPING FAILED -> REFUND -> CANCEL)");
        System.out.println("--------------------------------------------------------------------------");

        OrderServiceSaga.OrderRecord order = orderService.createOrder(
                "CUST-001",
                new BigDecimal("300000"),
                "UNSUPPORTED - Đảo Xa, Khu vực chưa hỗ trợ giao"
        );

        OrderServiceSaga.OrderRecord result = orderService.getOrder(order.getOrderId());
        System.out.println("\nRESULT 2: Status = " + result.getStatus()
                + " | Customer Balance (After Refund) = " + paymentService.getCustomerBalance("CUST-001") + " VNĐ");
    }

    private static void runScenario3_ShippingTimeout() {
        EventBroker.clearAll();
        PaymentServiceSaga paymentService = new PaymentServiceSaga();
        ShippingServiceSaga shippingService = new ShippingServiceSaga();
        OrderServiceSaga orderService = new OrderServiceSaga();

        paymentService.setCustomerBalance("CUST-001", new BigDecimal("1000000"));
        shippingService.setSimulateTimeoutMode(true); // Bật chế độ giả lập timeout

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 3: TIMEOUT 30 SÂY KHI GIAO HÀNG (TIMEOUT -> REFUND -> CANCEL)");
        System.out.println("--------------------------------------------------------------------------");

        OrderServiceSaga.OrderRecord order = orderService.createOrder(
                "CUST-001",
                new BigDecimal("400000"),
                "456 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh"
        );

        System.out.println("Lúc này Order ở trạng thái WAITING_FOR_SHIPPING...");
        System.out.println("Kích hoạt Timeout Scheduler sau 30s...");
        orderService.triggerShippingTimeout(order.getOrderId());

        OrderServiceSaga.OrderRecord result = orderService.getOrder(order.getOrderId());
        System.out.println("\nRESULT 3: Status = " + result.getStatus()
                + " | Customer Balance (After Refund) = " + paymentService.getCustomerBalance("CUST-001") + " VNĐ");

        System.out.println("\n--- Thử nghiệm gửi ShippingSuccessEvent tới TRỄ sau Timeout ---");
        ShippingSuccessEvent lateEvent = ShippingSuccessEvent.builder()
                .orderId(order.getOrderId())
                .waybillCode("WB-LATE-999")
                .carrierName("GHN")
                .timestamp(System.currentTimeMillis())
                .build();
        EventBroker.publish("shipping-success-topic", lateEvent);

        System.out.println("RESULT 3 (Idempotency Check): Status vẫn là = " + orderService.getOrder(order.getOrderId()).getStatus());
    }
}
