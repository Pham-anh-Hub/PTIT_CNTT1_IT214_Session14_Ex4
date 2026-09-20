package microservice.shippingservice.service;

import microservice.shippingservice.event.PaymentSuccessEvent;
import microservice.shippingservice.event.ShippingFailedEvent;
import microservice.shippingservice.event.ShippingSuccessEvent;
import microservice.shippingservice.model.Waybill;
import microservice.shippingservice.publisher.ShippingEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShippingService {

    private final Map<String, Waybill> waybillRepository = new ConcurrentHashMap<>();
    private boolean simulateTimeoutMode = false;

    @Autowired
    private ShippingEventPublisher eventPublisher;

    public void setSimulateTimeoutMode(boolean simulateTimeoutMode) {
        this.simulateTimeoutMode = simulateTimeoutMode;
    }

    public Waybill getWaybillByOrderId(String orderId) {
        return waybillRepository.get(orderId);
    }

    public void processPaymentSuccess(PaymentSuccessEvent event) {
        System.out.println("[SHIPPING-SERVICE] Nhận PaymentSuccessEvent cho đơn hàng: " + event.getOrderId()
                + " | Địa chỉ: " + event.getShippingAddress());

        if (simulateTimeoutMode) {
            System.out.println("[SHIPPING-SERVICE] ⚠️ GIẢ LẬP TIMEOUT / NGHỄN MẠNG: Shipping Service không phản hồi cho đơn hàng " + event.getOrderId());
            return;
        }

        String address = event.getShippingAddress() != null ? event.getShippingAddress().toUpperCase() : "";

        if (address.contains("UNSUPPORTED") || address.contains("FAIL") || address.contains("KHONG_HO_TRO")) {
            System.out.println("[SHIPPING-SERVICE] ❌ Địa chỉ KHÔNG ĐƯỢC HỖ TRỢ! Giao hàng thất bại cho đơn: " + event.getOrderId());

            ShippingFailedEvent failedEvent = ShippingFailedEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .reason("Địa chỉ giao hàng nằm ngoài vùng phục vụ (Unsupported Area)")
                    .timestamp(System.currentTimeMillis())
                    .build();

            eventPublisher.publishShippingFailed(failedEvent);
        } else {
            String waybillCode = "WB-" + UUID.randomUUID().toString().substring(0, 8);
            Waybill waybill = Waybill.builder()
                    .waybillCode(waybillCode)
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .shippingAddress(event.getShippingAddress())
                    .carrierName("Giao Hàng Nhanh (GHN)")
                    .status("CREATED")
                    .createdAt(System.currentTimeMillis())
                    .build();

            waybillRepository.put(event.getOrderId(), waybill);

            System.out.println("[SHIPPING-SERVICE] ✅ Tạo vận đơn THÀNH CÔNG! Mã vận đơn: " + waybillCode + " | Đơn hàng: " + event.getOrderId());

            ShippingSuccessEvent successEvent = ShippingSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .waybillCode(waybillCode)
                    .carrierName("Giao Hàng Nhanh (GHN)")
                    .timestamp(System.currentTimeMillis())
                    .build();

            eventPublisher.publishShippingSuccess(successEvent);
        }
    }
}
