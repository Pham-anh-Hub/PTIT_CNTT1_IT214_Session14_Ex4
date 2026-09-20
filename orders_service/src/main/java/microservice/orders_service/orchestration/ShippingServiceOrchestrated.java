package microservice.orders_service.orchestration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ShippingServiceOrchestrated {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingResult {
        private boolean success;
        private String waybillCode;
        private String carrierName;
        private String message;
    }

    /**
     * Bước 3 trong Orchestration: Tạo vận đơn giao hàng
     */
    public ShippingResult createShipment(String orderId, String shippingAddress) {
        if (shippingAddress != null && (shippingAddress.toUpperCase().contains("UNSUPPORTED") || shippingAddress.toUpperCase().contains("FAIL"))) {
            System.out.println("  [SHIPPING SERVICE] ❌ Địa chỉ KHÔNG ĐƯỢC HỖ TRỢ! Giao hàng thất bại cho đơn: " + orderId);
            return ShippingResult.builder()
                    .success(false)
                    .message("Địa chỉ giao hàng nằm ngoài khu vực hỗ trợ!")
                    .build();
        }

        String waybillCode = "WB-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("  [SHIPPING SERVICE] ✅ Tạo vận đơn THÀNH CÔNG! Mã vận đơn: " + waybillCode + " cho đơn: " + orderId);

        return ShippingResult.builder()
                .success(true)
                .waybillCode(waybillCode)
                .carrierName("Giao Hàng Nhanh (GHN)")
                .message("Tạo vận đơn thành công")
                .build();
    }
}
