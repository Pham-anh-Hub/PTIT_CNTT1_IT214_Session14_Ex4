package microservice.orders_service.saga.services;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OrderServiceSaga {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRecord {
        private String orderId;
        private String customerId;
        private BigDecimal totalAmount;
        private String shippingAddress;
        private OrderStatus status;
        private String paymentId;
        private String waybillCode;
        private long createdAt;
        private long updatedAt;
    }

    private final Map<String, OrderRecord> orderRepository = new ConcurrentHashMap<>();

    public OrderServiceSaga() {
        registerListeners();
    }

    public OrderRecord getOrder(String orderId) {
        return orderRepository.get(orderId);
    }

    public OrderRecord createOrder(String customerId, BigDecimal totalAmount, String shippingAddress) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        OrderRecord order = OrderRecord.builder()
                .orderId(orderId)
                .customerId(customerId)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepository.put(orderId, order);

        System.out.println("[ORDER SERVICE] 1. Khởi tạo đơn hàng: " + orderId + " | Khách: " + customerId
                + " | Số tiền: " + totalAmount + " | Trạng thái: " + OrderStatus.PENDING);

        // Bắn OrderCreatedEvent
        OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .customerId(customerId)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .timestamp(now)
                .build();

        EventBroker.publish("order-created-topic", createdEvent);
        return order;
    }

    private void registerListeners() {
        // 1. Lắng nghe PaymentSuccessEvent từ Payment Service
        EventBroker.subscribe("payment-success-topic", event -> {
            if (event instanceof PaymentSuccessEvent) {
                handlePaymentSuccess((PaymentSuccessEvent) event);
            }
        });

        // 2. Lắng nghe ShippingSuccessEvent từ Shipping Service
        EventBroker.subscribe("shipping-success-topic", event -> {
            if (event instanceof ShippingSuccessEvent) {
                handleShippingSuccess((ShippingSuccessEvent) event);
            }
        });

        // 3. Lắng nghe ShippingFailedEvent từ Shipping Service
        EventBroker.subscribe("shipping-failed-topic", event -> {
            if (event instanceof ShippingFailedEvent) {
                handleShippingFailed((ShippingFailedEvent) event);
            }
        });

        // 4. Lắng nghe RefundSuccessEvent từ Payment Service
        EventBroker.subscribe("refund-success-topic", event -> {
            if (event instanceof RefundSuccessEvent) {
                handleRefundSuccess((RefundSuccessEvent) event);
            }
        });
    }

    private void handlePaymentSuccess(PaymentSuccessEvent event) {
        OrderRecord order = orderRepository.get(event.getOrderId());
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.WAITING_FOR_SHIPPING);
            order.setPaymentId(event.getPaymentId());
            order.setUpdatedAt(System.currentTimeMillis());
            System.out.println("[ORDER SERVICE] Nhận PaymentSuccessEvent | Cập nhật đơn " + order.getOrderId()
                    + " -> Trạng thái: " + OrderStatus.WAITING_FOR_SHIPPING);
        }
    }

    private void handleShippingSuccess(ShippingSuccessEvent event) {
        OrderRecord order = orderRepository.get(event.getOrderId());
        if (order != null) {
            // Kiểm tra Độc định (Idempotency Check)
            if (order.getStatus() == OrderStatus.WAITING_FOR_SHIPPING) {
                order.setStatus(OrderStatus.COMPLETED);
                order.setWaybillCode(event.getWaybillCode());
                order.setUpdatedAt(System.currentTimeMillis());
                System.out.println("[ORDER SERVICE] ✅ Nhận ShippingSuccessEvent | Đơn hàng " + order.getOrderId()
                        + " THÀNH CÔNG HOÀN TẤT -> Trạng thái: " + OrderStatus.COMPLETED);
            } else {
                System.out.println("[ORDER SERVICE] ⚠️ IDEMPOTENCY WARNING: Nhận ShippingSuccessEvent trễ cho đơn "
                        + order.getOrderId() + " nhưng trạng thái hiện tại là: " + order.getStatus() + ". Bỏ qua sự kiện này!");
            }
        }
    }

    private void handleShippingFailed(ShippingFailedEvent event) {
        OrderRecord order = orderRepository.get(event.getOrderId());
        if (order != null) {
            System.out.println("[ORDER SERVICE] ❌ Nhận ShippingFailedEvent cho đơn " + order.getOrderId()
                    + " | Lý do: " + event.getReason());

            order.setStatus(OrderStatus.SHIPPING_FAILED);
            order.setUpdatedAt(System.currentTimeMillis());

            // Phát sự kiện bù trừ CompensatePaymentEvent gửi tới Payment Service
            triggerPaymentCompensation(order, "SHIPPING_FAILED: " + event.getReason());
        }
    }

    private void handleRefundSuccess(RefundSuccessEvent event) {
        OrderRecord order = orderRepository.get(event.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(System.currentTimeMillis());
            System.out.println("[ORDER SERVICE] 🛑 Nhận RefundSuccessEvent | Đã hoàn tiền " + event.getRefundedAmount()
                    + " | Đơn hàng " + order.getOrderId() + " ĐÃ HỦY THÀNH CÔNG -> Trạng thái: " + OrderStatus.CANCELLED);
        }
    }

    /**
     * Kích hoạt xử lý Timeout 30s cho bước Giao hàng
     */
    public void triggerShippingTimeout(String orderId) {
        OrderRecord order = orderRepository.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.WAITING_FOR_SHIPPING) {
            System.out.println("\n[ORDER SERVICE] ⏱️ TIMEOUT ALERT! Đã quá 30s không nhận phản hồi từ Shipping Service cho đơn: " + orderId);
            order.setStatus(OrderStatus.SHIPPING_TIMED_OUT);
            order.setUpdatedAt(System.currentTimeMillis());

            // Tự động kích hoạt bù trừ hoàn tiền
            triggerPaymentCompensation(order, "SHIPPING_SERVICE_TIMEOUT_30S");
        }
    }

    private void triggerPaymentCompensation(OrderRecord order, String reason) {
        order.setStatus(OrderStatus.COMPENSATING_PAYMENT);
        order.setUpdatedAt(System.currentTimeMillis());

        System.out.println("[ORDER SERVICE] 🔄 BẮT ĐẦU QUY TRÌNH BÙ TRỪ: Phát CompensatePaymentEvent cho đơn hàng "
                + order.getOrderId() + " | Hoàn lại số tiền: " + order.getTotalAmount());

        CompensatePaymentEvent compensateEvent = CompensatePaymentEvent.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .refundAmount(order.getTotalAmount())
                .reason(reason)
                .timestamp(System.currentTimeMillis())
                .build();

        EventBroker.publish("compensate-payment-topic", compensateEvent);
    }
}
