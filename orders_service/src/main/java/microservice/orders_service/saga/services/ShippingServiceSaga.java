package microservice.orders_service.saga.services;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.PaymentSuccessEvent;
import microservice.orders_service.saga.events.ShippingFailedEvent;
import microservice.orders_service.saga.events.ShippingSuccessEvent;

import java.util.UUID;

public class ShippingServiceSaga {

    private boolean simulateTimeoutMode = false;

    public ShippingServiceSaga() {
        registerListeners();
    }

    public void setSimulateTimeoutMode(boolean simulateTimeoutMode) {
        this.simulateTimeoutMode = simulateTimeoutMode;
    }

    private void registerListeners() {
        // Lắng nghe PaymentSuccessEvent từ Payment Service
        EventBroker.subscribe("payment-success-topic", event -> {
            if (event instanceof PaymentSuccessEvent) {
                handlePaymentSuccess((PaymentSuccessEvent) event);
            }
        });
    }

    private void handlePaymentSuccess(PaymentSuccessEvent event) {
        System.out.println("[SHIPPING SERVICE] Nhận PaymentSuccessEvent cho đơn hàng: " + event.getOrderId()
                + " | Địa chỉ giao: " + event.getShippingAddress());

        // Trường hợp Giả lập Timeout (Shipping Service bị nghẽn/treo không gửi phản hồi)
        if (simulateTimeoutMode) {
            System.out.println("[SHIPPING SERVICE] ⚠️ GIẢ LẬP TIMEOUT / NGHỄN MẠNG: Shipping Service không gửi phản hồi cho đơn hàng " + event.getOrderId());
            return;
        }

        // Kiểm tra địa chỉ giao hàng
        String address = event.getShippingAddress() != null ? event.getShippingAddress().toUpperCase() : "";

        if (address.contains("UNSUPPORTED") || address.contains("FAIL") || address.contains("KHONG_HO_TRO")) {
            // ĐỊA CHỈ KHÔNG HỖ TRỢ -> Gửi ShippingFailedEvent
            System.out.println("[SHIPPING SERVICE] ❌ Địa chỉ KHÔNG ĐƯỢC HỖ TRỢ! Giao hàng thất bại cho đơn: " + event.getOrderId());

            ShippingFailedEvent failedEvent = ShippingFailedEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .reason("Địa chỉ giao hàng không nằm trong khu vực phục vụ (Unsupported Area)")
                    .timestamp(System.currentTimeMillis())
                    .build();

            EventBroker.publish("shipping-failed-topic", failedEvent);
        } else {
            // TẠO VẬN ĐƠN THÀNH CÔNG -> Gửi ShippingSuccessEvent
            String waybillCode = "WB-" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("[SHIPPING SERVICE] ✅ Tạo vận đơn THÀNH CÔNG! Mã vận đơn: " + waybillCode + " | Đơn hàng: " + event.getOrderId());

            ShippingSuccessEvent successEvent = ShippingSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .waybillCode(waybillCode)
                    .carrierName("Giao Hàng Nhanh (GHN)")
                    .timestamp(System.currentTimeMillis())
                    .build();

            EventBroker.publish("shipping-success-topic", successEvent);
        }
    }
}
