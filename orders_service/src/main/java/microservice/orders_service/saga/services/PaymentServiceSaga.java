package microservice.orders_service.saga.services;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentServiceSaga {
    // Lưu trữ số dư ví của khách hàng (CustomerId -> Balance)
    private final Map<String, BigDecimal> customerWallets = new ConcurrentHashMap<>();

    public PaymentServiceSaga() {
        // Nạp dữ liệu mẫu
        customerWallets.put("CUST-001", new BigDecimal("1000000")); // 1,000,000 VND
        customerWallets.put("CUST-002", new BigDecimal("50000"));   // 50,000 VND

        registerListeners();
    }

    public void setCustomerBalance(String customerId, BigDecimal balance) {
        customerWallets.put(customerId, balance);
    }

    public BigDecimal getCustomerBalance(String customerId) {
        return customerWallets.getOrDefault(customerId, BigDecimal.ZERO);
    }

    private void registerListeners() {
        // 1. Lắng nghe OrderCreatedEvent từ Order Service
        EventBroker.subscribe("order-created-topic", event -> {
            if (event instanceof OrderCreatedEvent) {
                handleOrderCreated((OrderCreatedEvent) event);
            }
        });

        // 2. Lắng nghe CompensatePaymentEvent (Yêu cầu hoàn tiền từ Order Service)
        EventBroker.subscribe("compensate-payment-topic", event -> {
            if (event instanceof CompensatePaymentEvent) {
                handleCompensatePayment((CompensatePaymentEvent) event);
            }
        });
    }

    private void handleOrderCreated(OrderCreatedEvent event) {
        System.out.println("[PAYMENT SERVICE] Nhận OrderCreatedEvent cho đơn hàng: " + event.getOrderId());
        BigDecimal currentBalance = getCustomerBalance(event.getCustomerId());

        if (currentBalance.compareTo(event.getTotalAmount()) >= 0) {
            // Trừ tiền
            BigDecimal newBalance = currentBalance.subtract(event.getTotalAmount());
            customerWallets.put(event.getCustomerId(), newBalance);
            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);

            System.out.println("[PAYMENT SERVICE] Trừ tiền THÀNH CÔNG cho khách hàng " + event.getCustomerId()
                    + " | Trừ: " + event.getTotalAmount() + " | Số dư mới: " + newBalance);

            // Bắn PaymentSuccessEvent
            PaymentSuccessEvent paymentSuccessEvent = PaymentSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .paymentId(paymentId)
                    .amountDeducted(event.getTotalAmount())
                    .shippingAddress(event.getShippingAddress())
                    .timestamp(System.currentTimeMillis())
                    .build();

            EventBroker.publish("payment-success-topic", paymentSuccessEvent);
        } else {
            System.out.println("[PAYMENT SERVICE] Khách hàng " + event.getCustomerId() + " KHÔNG ĐỦ TIỀN thanh toán đơn hàng " + event.getOrderId());
            // Có thể phát PaymentFailedEvent nếu cần
        }
    }

    private void handleCompensatePayment(CompensatePaymentEvent event) {
        System.out.println("[PAYMENT SERVICE] [COMPENSATION] Nhận CompensatePaymentEvent cho đơn hàng: "
                + event.getOrderId() + " | Lý do: " + event.getReason());

        // Hoàn tiền lại cho khách hàng
        BigDecimal currentBalance = getCustomerBalance(event.getCustomerId());
        BigDecimal newBalance = currentBalance.add(event.getRefundAmount());
        customerWallets.put(event.getCustomerId(), newBalance);

        String refundId = "REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[PAYMENT SERVICE] HOÀN TIỀN THÀNH CÔNG cho khách " + event.getCustomerId()
                + " | Hoàn lại: " + event.getRefundAmount() + " | Số dư sau hoàn: " + newBalance);

        // Bắn RefundSuccessEvent
        RefundSuccessEvent refundSuccessEvent = RefundSuccessEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .refundId(refundId)
                .refundedAmount(event.getRefundAmount())
                .timestamp(System.currentTimeMillis())
                .build();

        EventBroker.publish("refund-success-topic", refundSuccessEvent);
    }
}
