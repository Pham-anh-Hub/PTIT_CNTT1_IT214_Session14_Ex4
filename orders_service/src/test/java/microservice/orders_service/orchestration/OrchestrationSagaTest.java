package microservice.orders_service.orchestration;

import microservice.orders_service.saga.events.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class OrchestrationSagaTest {

    private VoucherServiceOrchestrated voucherService;
    private PaymentServiceOrchestrated paymentService;
    private ShippingServiceOrchestrated shippingService;
    private OrderOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        voucherService = new VoucherServiceOrchestrated();
        paymentService = new PaymentServiceOrchestrated();
        shippingService = new ShippingServiceOrchestrated();

        orchestrator = new OrderOrchestrator(voucherService, paymentService, shippingService);

        paymentService.setCustomerBalance("CUST-555", new BigDecimal("500000"));
    }

    @Test
    @DisplayName("Kịch bản 1: Orchestration Saga - Áp dụng Voucher thành công (Happy Path)")
    void testHappyPathWithVoucher() {
        OrderOrchestrator.SagaOrderState saga = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("200000"),
                "123 Le Loi, HCMC",
                "SUMMER50"
        );

        assertEquals(OrderStatus.COMPLETED, saga.getStatus());
        assertEquals(new BigDecimal("50000"), saga.getDiscountAmount());
        assertEquals(new BigDecimal("150000"), saga.getFinalAmount());
        assertNotNull(saga.getPaymentId());
        assertNotNull(saga.getWaybillCode());
        assertEquals(new BigDecimal("350000"), paymentService.getCustomerBalance("CUST-555"));
    }

    @Test
    @DisplayName("Kịch bản 2: Orchestration Saga - Voucher bị hết hạn/không hợp lệ")
    void testExpiredVoucherFailure() {
        OrderOrchestrator.SagaOrderState saga = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("300000"),
                "456 Nguyen Hue, HCMC",
                "EXPIRED100"
        );

        assertEquals(OrderStatus.CANCELLED, saga.getStatus());
        assertTrue(saga.getFailureReason().contains("Lỗi Voucher"));
    }

    @Test
    @DisplayName("Kịch bản 3: Orchestration Saga - Lỗi Thanh toán -> Bù trừ Nhả Voucher")
    void testPaymentFailureVoucherCompensation() {
        int usageBefore = voucherService.getRemainingUsage("SUMMER50");

        OrderOrchestrator.SagaOrderState saga = orchestrator.executeOrderSaga(
                "POOR-USER",
                new BigDecimal("200000"),
                "789 Dien Bien Phu, HCMC",
                "SUMMER50"
        );

        int usageAfter = voucherService.getRemainingUsage("SUMMER50");

        assertEquals(OrderStatus.CANCELLED, saga.getStatus());
        assertTrue(saga.getFailureReason().contains("Lỗi Thanh toán"));
        assertEquals(usageBefore, usageAfter, "Lượt sử dụng Voucher phải được bù trừ hoàn trả lại!");
    }

    @Test
    @DisplayName("Kịch bản 4: Orchestration Saga - Lỗi Giao hàng -> Bù trừ Reverse Order (Hoàn tiền + Nhả Voucher)")
    void testShippingFailureReverseCompensation() {
        BigDecimal balanceBefore = paymentService.getCustomerBalance("CUST-555");
        int usageBefore = voucherService.getRemainingUsage("SUMMER50");

        OrderOrchestrator.SagaOrderState saga = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("200000"),
                "UNSUPPORTED - Đảo Xa",
                "SUMMER50"
        );

        BigDecimal balanceAfter = paymentService.getCustomerBalance("CUST-555");
        int usageAfter = voucherService.getRemainingUsage("SUMMER50");

        assertEquals(OrderStatus.CANCELLED, saga.getStatus());
        assertTrue(saga.getFailureReason().contains("Lỗi Vận chuyển"));
        assertEquals(balanceBefore, balanceAfter, "Số dư phải được bù trừ hoàn lại 100%!");
        assertEquals(usageBefore, usageAfter, "Lượt sử dụng Voucher phải được hoàn lại!");
    }
}
