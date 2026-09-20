package microservice.orders_service.orchestration;

import microservice.orders_service.saga.events.OrderStatus;

import java.math.BigDecimal;

public class OrchestrationSagaRunner {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("   MÔ PHỎNG GIẢI PHÁP ORCHESTRATION SAGA TÍCH HỢP VOUCHER & BÙ TRỪ       ");
        System.out.println("==========================================================================");

        VoucherServiceOrchestrated voucherService = new VoucherServiceOrchestrated();
        PaymentServiceOrchestrated paymentService = new PaymentServiceOrchestrated();
        ShippingServiceOrchestrated shippingService = new ShippingServiceOrchestrated();

        OrderOrchestrator orchestrator = new OrderOrchestrator(voucherService, paymentService, shippingService);

        // Nạp số dư cho tài khoản
        paymentService.setCustomerBalance("CUST-555", new BigDecimal("500000")); // 500,000 VNĐ

        // ---------------------------------------------------------------------
        // KỊCH BẢN 1: HAPPY PATH VỚI VOUCHER HỢP LỆ
        // ---------------------------------------------------------------------
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 1: ÁP VOUCHER HỢP LỆ (SUMMER50) -> THÀNH CÔNG TRỌN VẸN");
        System.out.println("--------------------------------------------------------------------------");

        OrderOrchestrator.SagaOrderState saga1 = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("200000"), // Tiền gốc 200,000 VNĐ
                "123 Lê Lợi, HCMC",
                "SUMMER50"                // Giảm 50,000 VNĐ -> Cần trả 150,000 VNĐ
        );

        System.out.println("\nRESULT 1: Status = " + saga1.getStatus()
                + " | Tiền gốc = " + saga1.getOriginalAmount()
                + " | Giảm giá = " + saga1.getDiscountAmount()
                + " | Tiền thanh toán = " + saga1.getFinalAmount()
                + " | PaymentId = " + saga1.getPaymentId()
                + " | WaybillCode = " + saga1.getWaybillCode()
                + " | Số dư ví còn lại = " + paymentService.getCustomerBalance("CUST-555") + " VNĐ");

        // ---------------------------------------------------------------------
        // KỊCH BẢN 2: VOUCHER HẾT HẠN / KHÔNG HỢP LỆ
        // ---------------------------------------------------------------------
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 2: ÁP VOUCHER HẾT HẠN (EXPIRED100) -> HỦY ĐƠN LẬP TỨC (KHÔNG BÙ TRỪ)");
        System.out.println("--------------------------------------------------------------------------");

        OrderOrchestrator.SagaOrderState saga2 = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("300000"),
                "456 Nguyễn Huệ, HCMC",
                "EXPIRED100"
        );

        System.out.println("\nRESULT 2: Status = " + saga2.getStatus() + " | Lý do lỗi = " + saga2.getFailureReason());

        // ---------------------------------------------------------------------
        // KỊCH BẢN 3: THANH TOÁN THẤT BẠI ➔ BÙ TRỪ NHẢ LẠI VOUCHER
        // ---------------------------------------------------------------------
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 3: KHÔNG ĐỦ TIỀN THANH TOÁN -> BÙ TRỪ NHẢ LẠI VOUCHER -> HỦY ĐƠN");
        System.out.println("--------------------------------------------------------------------------");

        int usageBefore = voucherService.getRemainingUsage("SUMMER50");

        OrderOrchestrator.SagaOrderState saga3 = orchestrator.executeOrderSaga(
                "POOR-USER",             // Ví có 10,000 VNĐ
                new BigDecimal("200000"),// Tiền gốc 200,000 VNĐ - Giảm 50k = 150,000 VNĐ (Không đủ tiền trả!)
                "789 Điện Biên Phủ, HCMC",
                "SUMMER50"
        );

        int usageAfter = voucherService.getRemainingUsage("SUMMER50");

        System.out.println("\nRESULT 3: Status = " + saga3.getStatus()
                + " | Lý do = " + saga3.getFailureReason()
                + " | Lượt dùng Voucher trước đó: " + usageBefore
                + " | Lượt dùng Voucher sau bù trừ: " + usageAfter + " (Đã được hoàn trả!)");

        // ---------------------------------------------------------------------
        // KỊCH BẢN 4: GIAO HÀNG THẤT BẠI ➔ BÙ TRỪ HOÀN TIỀN + NHẢ VOUCHER
        // ---------------------------------------------------------------------
        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("KỊCH BẢN 4: GIAO HÀNG LỖI ĐỊA CHỈ -> BÙ TRỪ HOÀN TIỀN + NHẢ VOUCHER -> HỦY ĐƠN");
        System.out.println("--------------------------------------------------------------------------");

        BigDecimal balanceBefore = paymentService.getCustomerBalance("CUST-555");
        int voucherUsageBefore = voucherService.getRemainingUsage("SUMMER50");

        OrderOrchestrator.SagaOrderState saga4 = orchestrator.executeOrderSaga(
                "CUST-555",
                new BigDecimal("200000"), // Tiền gốc 200,000 VNĐ - Giảm 50k = 150,000 VNĐ
                "UNSUPPORTED - Đảo Xa",
                "SUMMER50"
        );

        BigDecimal balanceAfter = paymentService.getCustomerBalance("CUST-555");
        int voucherUsageAfter = voucherService.getRemainingUsage("SUMMER50");

        System.out.println("\nRESULT 4: Status = " + saga4.getStatus()
                + " | Lý do = " + saga4.getFailureReason()
                + " | Số dư ví trước đặt: " + balanceBefore + " VNĐ"
                + " | Số dư ví sau bù trừ hoàn tiền: " + balanceAfter + " VNĐ (Đã được hoàn 100%!)"
                + " | Lượt dùng Voucher trước: " + voucherUsageBefore
                + " | Lượt dùng Voucher sau bù trừ: " + voucherUsageAfter + " (Đã nhả lại!)");

        System.out.println("\n==========================================================================");
        System.out.println("   MÔ PHỎNG ORCHESTRATION SAGA HOÀN THÀNH XẮC XUẤT 100%!                  ");
        System.out.println("==========================================================================");
    }
}
