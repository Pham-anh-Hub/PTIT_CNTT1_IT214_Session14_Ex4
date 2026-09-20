package microservice.orders_service.service;

import microservice.orders_service.model.OrderModel;
import microservice.orders_service.publisher.OrderEventPublisher;
import microservice.orders_service.saga.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final Map<String, OrderModel> orderRepository = new ConcurrentHashMap<>();

    @Autowired
    private OrderEventPublisher eventPublisher;

    public OrderModel getOrder(String orderId) {
        return orderRepository.get(orderId);
    }

    public OrderModel createOrder(String customerId, BigDecimal totalAmount, String shippingAddress) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        OrderModel order = OrderModel.builder()
                .orderId(orderId)
                .customerId(customerId)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepository.put(orderId, order);

        System.out.println("[ORDERS-SERVICE] Khởi tạo đơn: " + orderId + " | Trạng thái: " + OrderStatus.PENDING);

        OrderCreatedEvent createdEvent = OrderCreatedEvent.builder()
                .orderId(orderId)
                .customerId(customerId)
                .totalAmount(totalAmount)
                .shippingAddress(shippingAddress)
                .timestamp(now)
                .build();

        eventPublisher.publishOrderCreated(createdEvent);
        return order;
    }

    public void processPaymentSuccess(PaymentSuccessEvent event) {
        OrderModel order = orderRepository.get(event.getOrderId());
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.WAITING_FOR_SHIPPING);
            order.setPaymentId(event.getPaymentId());
            order.setUpdatedAt(System.currentTimeMillis());
            System.out.println("[ORDERS-SERVICE] Nhận PaymentSuccessEvent | Cập nhật đơn " + order.getOrderId() + " -> WAITING_FOR_SHIPPING");
        }
    }

    public void processShippingSuccess(ShippingSuccessEvent event) {
        OrderModel order = orderRepository.get(event.getOrderId());
        if (order != null) {
            if (order.getStatus() == OrderStatus.WAITING_FOR_SHIPPING) {
                order.setStatus(OrderStatus.COMPLETED);
                order.setWaybillCode(event.getWaybillCode());
                order.setUpdatedAt(System.currentTimeMillis());
                System.out.println("[ORDERS-SERVICE] ✅ Nhận ShippingSuccessEvent | Đơn " + order.getOrderId() + " HOÀN TẤT -> COMPLETED");
            } else {
                System.out.println("[ORDERS-SERVICE] ⚠️ IDEMPOTENCY: Nhận ShippingSuccessEvent trễ cho đơn " + order.getOrderId()
                        + " hiện có status " + order.getStatus() + ". Bỏ qua sự kiện!");
            }
        }
    }

    public void processShippingFailed(ShippingFailedEvent event) {
        OrderModel order = orderRepository.get(event.getOrderId());
        if (order != null) {
            System.out.println("[ORDERS-SERVICE] ❌ Nhận ShippingFailedEvent | Lý do: " + event.getReason());
            order.setStatus(OrderStatus.SHIPPING_FAILED);
            order.setUpdatedAt(System.currentTimeMillis());
            triggerPaymentCompensation(order, "SHIPPING_FAILED: " + event.getReason());
        }
    }

    public void processRefundSuccess(RefundSuccessEvent event) {
        OrderModel order = orderRepository.get(event.getOrderId());
        if (order != null) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(System.currentTimeMillis());
            System.out.println("[ORDERS-SERVICE] 🛑 Nhận RefundSuccessEvent | Đơn " + order.getOrderId() + " ĐÃ HỦY THÀNH CÔNG -> CANCELLED");
        }
    }

    public void triggerShippingTimeout(String orderId) {
        OrderModel order = orderRepository.get(orderId);
        if (order != null && order.getStatus() == OrderStatus.WAITING_FOR_SHIPPING) {
            System.out.println("\n[ORDERS-SERVICE] ⏱️ TIMEOUT 30S! Quá thời gian giao hàng cho đơn: " + orderId);
            order.setStatus(OrderStatus.SHIPPING_TIMED_OUT);
            order.setUpdatedAt(System.currentTimeMillis());
            triggerPaymentCompensation(order, "SHIPPING_SERVICE_TIMEOUT_30S");
        }
    }

    private void triggerPaymentCompensation(OrderModel order, String reason) {
        order.setStatus(OrderStatus.COMPENSATING_PAYMENT);
        order.setUpdatedAt(System.currentTimeMillis());

        System.out.println("[ORDERS-SERVICE] 🔄 BẮT ĐẦU BÙ TRỪ: Phát CompensatePaymentEvent cho đơn " + order.getOrderId());

        CompensatePaymentEvent compensateEvent = CompensatePaymentEvent.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .refundAmount(order.getTotalAmount())
                .reason(reason)
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.publishCompensatePayment(compensateEvent);
    }
}
