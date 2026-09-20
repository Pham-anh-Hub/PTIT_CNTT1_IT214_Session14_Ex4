package microservice.orders_service.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentServiceOrchestrated {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentResult {
        private boolean success;
        private String paymentId;
        private BigDecimal amountPaid;
        private String message;
    }

    private final Map<String, BigDecimal> customerWallets = new ConcurrentHashMap<>();

    public PaymentServiceOrchestrated() {
        customerWallets.put("CUST-555", new BigDecimal("500000")); // Số dư 500,000 VNĐ
        customerWallets.put("POOR-USER", new BigDecimal("10000"));  // Số dư 10,000 VNĐ
    }

    public void setCustomerBalance(String customerId, BigDecimal balance) {
        customerWallets.put(customerId, balance);
    }

    public BigDecimal getCustomerBalance(String customerId) {
        return customerWallets.getOrDefault(customerId, BigDecimal.ZERO);
    }

    /**
     * Bước 2 trong Orchestration: Trừ tiền ví thanh toán
     */
    public PaymentResult processPayment(String customerId, BigDecimal amountToPay) {
        BigDecimal balance = getCustomerBalance(customerId);
        if (balance.compareTo(amountToPay) < 0) {
            System.out.println("  [PAYMENT SERVICE] ❌ Trừ tiền THẤT BẠI cho khách " + customerId
                    + " | Cần: " + amountToPay + " VNĐ | Số dư ví: " + balance + " VNĐ");
            return PaymentResult.builder()
                    .success(false)
                    .amountPaid(BigDecimal.ZERO)
                    .message("Số dư tài khoản không đủ để thanh toán!")
                    .build();
        }

        BigDecimal newBalance = balance.subtract(amountToPay);
        customerWallets.put(customerId, newBalance);
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);

        System.out.println("  [PAYMENT SERVICE] ✅ Trừ tiền THÀNH CÔNG cho khách " + customerId
                + " | Đã trừ: " + amountToPay + " VNĐ | Số dư mới: " + newBalance + " VNĐ");

        return PaymentResult.builder()
                .success(true)
                .paymentId(paymentId)
                .amountPaid(amountToPay)
                .message("Thanh toán thành công")
                .build();
    }

    /**
     * Bù trừ (Compensation): Hoàn lại tiền cho khách hàng
     */
    public void refundPaymentCompensation(String customerId, BigDecimal amountToRefund) {
        if (amountToRefund == null || amountToRefund.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal balance = getCustomerBalance(customerId);
        BigDecimal newBalance = balance.add(amountToRefund);
        customerWallets.put(customerId, newBalance);

        System.out.println("  [PAYMENT SERVICE] 🔄 [COMPENSATION] Hoàn tiền THÀNH CÔNG cho khách " + customerId
                + " | Số tiền hoàn lại: " + amountToRefund + " VNĐ | Số dư mới: " + newBalance + " VNĐ");
    }
}
