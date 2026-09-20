package microservice.orders_service.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.orders_service.saga.events.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderOrchestrator {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SagaOrderState {
        private String orderId;
        private String customerId;
        private BigDecimal originalAmount;
        private String voucherCode;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;
        private String shippingAddress;
        private OrderStatus status;
        private String paymentId;
        private String waybillCode;
        private String failureReason;
    }

    private final Map<String, SagaOrderState> sagaRepository = new ConcurrentHashMap<>();

    @Autowired
    private VoucherServiceOrchestrated voucherService;

    @Autowired
    private PaymentServiceOrchestrated paymentService;

    @Autowired
    private ShippingServiceOrchestrated shippingService;

    public OrderOrchestrator(VoucherServiceOrchestrated voucherService,
                             PaymentServiceOrchestrated paymentService,
                             ShippingServiceOrchestrated shippingService) {
        this.voucherService = voucherService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
    }

    public SagaOrderState getSagaState(String orderId) {
        return sagaRepository.get(orderId);
    }

    /**
     * BỘ ĐIỀU PHỐI ORCHESTRATION SAGA QUẢN LÝ LUỒNG ĐẶT HÀNG TÍCH HỢP VOUCHER
     */
    public SagaOrderState executeOrderSaga(String customerId, BigDecimal originalAmount, String shippingAddress, String voucherCode) {
        String orderId = "ORD-ORCH-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("\n[ORCHESTRATOR] 🚀 Bắt đầu Orchestration Saga cho đơn: " + orderId + " | Khách: " + customerId
                + " | Số tiền gốc: " + originalAmount + " VNĐ | Voucher: " + (voucherCode != null ? voucherCode : "Không"));

        SagaOrderState sagaState = SagaOrderState.builder()
                .orderId(orderId)
                .customerId(customerId)
                .originalAmount(originalAmount)
                .voucherCode(voucherCode)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PENDING)
                .build();

        sagaRepository.put(orderId, sagaState);

        // ---------------------------------------------------------------------
        // BƯỚC 1: Gọi Voucher Service (Kiểm tra & Khóa Voucher)
        // ---------------------------------------------------------------------
        System.out.println("[ORCHESTRATOR] STEP 1: Gọi Voucher Service để kiểm tra & áp dụng Voucher...");
        VoucherServiceOrchestrated.ApplyVoucherResult voucherResult = voucherService.applyVoucher(voucherCode, originalAmount);

        if (!voucherResult.isSuccess()) {
            System.out.println("[ORCHESTRATOR] ❌ STEP 1 LỖI: " + voucherResult.getMessage());
            sagaState.setStatus(OrderStatus.CANCELLED);
            sagaState.setFailureReason("Lỗi Voucher: " + voucherResult.getMessage());
            return sagaState;
        }

        sagaState.setDiscountAmount(voucherResult.getDiscountAmount());
        sagaState.setFinalAmount(voucherResult.getFinalAmount());
        sagaState.setStatus(OrderStatus.WAITING_FOR_SHIPPING); // Voucher OK, chuyển bước tiếp

        // ---------------------------------------------------------------------
        // BƯỚC 2: Gọi Payment Service (Trừ tiền thanh toán với số tiền sau giảm giá)
        // ---------------------------------------------------------------------
        System.out.println("[ORCHESTRATOR] STEP 2: Gọi Payment Service để trừ số tiền " + sagaState.getFinalAmount() + " VNĐ...");
        PaymentServiceOrchestrated.PaymentResult paymentResult = paymentService.processPayment(customerId, sagaState.getFinalAmount());

        if (!paymentResult.isSuccess()) {
            System.out.println("[ORCHESTRATOR] ❌ STEP 2 LỖI: " + paymentResult.getMessage());
            sagaState.setFailureReason("Lỗi Thanh toán: " + paymentResult.getMessage());

            // THỰC HIỆN BÙ TRỪ (COMPENSATION): Nhả lại Voucher
            System.out.println("[ORCHESTRATOR] 🔄 BẮT ĐẦU CHUỖI BÙ TRỪ (COMPENSATION CHAIN)...");
            voucherService.releaseVoucherCompensation(voucherCode);

            sagaState.setStatus(OrderStatus.CANCELLED);
            return sagaState;
        }

        sagaState.setPaymentId(paymentResult.getPaymentId());

        // ---------------------------------------------------------------------
        // BƯỚC 3: Gọi Shipping Service (Kiểm tra địa chỉ & Tạo vận đơn)
        // ---------------------------------------------------------------------
        System.out.println("[ORCHESTRATOR] STEP 3: Gọi Shipping Service để tạo vận đơn...");
        ShippingServiceOrchestrated.ShippingResult shippingResult = shippingService.createShipment(orderId, shippingAddress);

        if (!shippingResult.isSuccess()) {
            System.out.println("[ORCHESTRATOR] ❌ STEP 3 LỖI: " + shippingResult.getMessage());
            sagaState.setFailureReason("Lỗi Vận chuyển: " + shippingResult.getMessage());

            // THỰC HIỆN CHUỖI BÙ TRỪ REVERSE ORDER COMPENSATION (Hoàn tiền ➔ Nhả Voucher)
            System.out.println("[ORCHESTRATOR] 🔄 BẮT ĐẦU CHUỖI BÙ TRỪ THEO THỨ TỰ NGƯỢC (REVERSE COMPENSATION CHAIN)...");
            
            // 3.a) Hoàn tiền Payment
            paymentService.refundPaymentCompensation(customerId, sagaState.getFinalAmount());
            
            // 3.b) Nhả Voucher
            voucherService.releaseVoucherCompensation(voucherCode);

            sagaState.setStatus(OrderStatus.CANCELLED);
            return sagaState;
        }

        sagaState.setWaybillCode(shippingResult.getWaybillCode());

        // ---------------------------------------------------------------------
        // BƯỚC 4: Hoàn tất đơn hàng (COMPLETED)
        // ---------------------------------------------------------------------
        sagaState.setStatus(OrderStatus.COMPLETED);
        System.out.println("[ORCHESTRATOR] 🎉 HOÀN TẤT ORCHESTRATION SAGA! Đơn hàng " + orderId + " -> COMPLETED");
        return sagaState;
    }
}
