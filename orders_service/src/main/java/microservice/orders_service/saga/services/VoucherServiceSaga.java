package microservice.orders_service.saga.services;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.OrderCreatedEvent;
import microservice.orders_service.saga.events.VoucherAppliedEvent;

import java.math.BigDecimal;

public class VoucherServiceSaga {

    public VoucherServiceSaga() {
        EventBroker.subscribe("order-created-topic", event -> {
            if (event instanceof OrderCreatedEvent orderCreated) {
                handleOrderCreated(orderCreated);
            }
        });
    }

    private void handleOrderCreated(OrderCreatedEvent event) {
        System.out.println("[VOUCHER-SAGA] Nhận OrderCreatedEvent cho đơn: " + event.getOrderId());
        
        // Tính giảm giá (ví dụ 50,000 VNĐ nếu tiền > 100,000 VNĐ)
        BigDecimal discount = BigDecimal.ZERO;
        if (event.getTotalAmount().compareTo(new BigDecimal("100000")) >= 0) {
            discount = new BigDecimal("50000");
        }
        BigDecimal finalAmount = event.getTotalAmount().subtract(discount);

        System.out.println("[VOUCHER-SAGA] Tính giảm giá xong! Giảm: " + discount + " VNĐ | Tổng tiền mới: " + finalAmount + " VNĐ");

        VoucherAppliedEvent appliedEvent = VoucherAppliedEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .discountAmount(discount)
                .finalAmount(finalAmount)
                .shippingAddress(event.getShippingAddress())
                .timestamp(System.currentTimeMillis())
                .build();

        EventBroker.publish("voucher-applied-topic", appliedEvent);
    }
}
