package microservice.orders_service.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoucherServiceOrchestrated {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoucherInfo {
        private String code;
        private BigDecimal discountAmount;
        private BigDecimal minOrderAmount;
        private int remainingUsage;
        private boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyVoucherResult {
        private boolean success;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;
        private String message;
    }

    private final Map<String, VoucherInfo> voucherDatabase = new ConcurrentHashMap<>();

    public VoucherServiceOrchestrated() {
        voucherDatabase.put("SUMMER50", VoucherInfo.builder()
                .code("SUMMER50")
                .discountAmount(new BigDecimal("50000")) // Giảm 50,000 VNĐ
                .minOrderAmount(new BigDecimal("100000")) // Đơn tối thiểu 100,000 VNĐ
                .remainingUsage(10)
                .active(true)
                .build());

        voucherDatabase.put("EXPIRED100", VoucherInfo.builder()
                .code("EXPIRED100")
                .discountAmount(new BigDecimal("100000"))
                .minOrderAmount(new BigDecimal("200000"))
                .remainingUsage(0) // Hết lượt dùng
                .active(false)
                .build());
    }

    /**
     * Bước 1 trong Orchestration: Kiểm tra & Áp dụng Voucher
     */
    public ApplyVoucherResult applyVoucher(String voucherCode, BigDecimal originalAmount) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return ApplyVoucherResult.builder()
                    .success(true)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(originalAmount)
                    .message("Không sử dụng Voucher")
                    .build();
        }

        VoucherInfo voucher = voucherDatabase.get(voucherCode.toUpperCase());
        if (voucher == null || !voucher.isActive()) {
            return ApplyVoucherResult.builder()
                    .success(false)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(originalAmount)
                    .message("Voucher không tồn tại hoặc đã bị khóa!")
                    .build();
        }

        if (voucher.getRemainingUsage() <= 0) {
            return ApplyVoucherResult.builder()
                    .success(false)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(originalAmount)
                    .message("Voucher đã hết lượt sử dụng!")
                    .build();
        }

        if (originalAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            return ApplyVoucherResult.builder()
                    .success(false)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(originalAmount)
                    .message("Đơn hàng chưa đạt giá trị tối thiểu " + voucher.getMinOrderAmount() + " VNĐ để dùng Voucher này!")
                    .build();
        }

        // Trừ số lượt sử dụng voucher (Reserve voucher)
        voucher.setRemainingUsage(voucher.getRemainingUsage() - 1);
        BigDecimal discount = voucher.getDiscountAmount();
        BigDecimal finalAmount = originalAmount.subtract(discount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        System.out.println("  [VOUCHER SERVICE] ✅ Áp dụng Voucher " + voucherCode + " THÀNH CÔNG! Giảm: "
                + discount + " VNĐ | Tổng tiền mới: " + finalAmount + " VNĐ | Lượt dùng còn lại: " + voucher.getRemainingUsage());

        return ApplyVoucherResult.builder()
                .success(true)
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .message("Khóa và áp dụng Voucher thành công")
                .build();
    }

    /**
     * Bù trừ (Compensation): Hoàn trả lại lượt dùng Voucher khi thanh toán hoặc giao hàng bị lỗi
     */
    public void releaseVoucherCompensation(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) return;

        VoucherInfo voucher = voucherDatabase.get(voucherCode.toUpperCase());
        if (voucher != null) {
            voucher.setRemainingUsage(voucher.getRemainingUsage() + 1);
            System.out.println("  [VOUCHER SERVICE] 🔄 [COMPENSATION] Đã hoàn trả lại 1 lượt dùng cho Voucher: "
                    + voucherCode + " | Lượt dùng hiện tại: " + voucher.getRemainingUsage());
        }
    }

    public int getRemainingUsage(String voucherCode) {
        VoucherInfo v = voucherDatabase.get(voucherCode.toUpperCase());
        return v != null ? v.getRemainingUsage() : 0;
    }
}
