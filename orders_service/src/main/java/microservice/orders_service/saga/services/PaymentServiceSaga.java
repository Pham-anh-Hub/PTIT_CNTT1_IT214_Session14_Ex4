package microservice.orders_service.saga.services;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentServiceSaga {
    private final Map<String, BigDecimal> customerWallets = new ConcurrentHashMap<>();

    public PaymentServiceSaga() {
        customerWallets.put("CUST-001", new BigDecimal("1000000"));
        customerWallets.put("CUST-002", new BigDecimal("50000"));
        registerListeners();
    }

    public void setCustomerBalance(String customerId, BigDecimal balance) {
        customerWallets.put(customerId, balance);
    }

    public BigDecimal getCustomerBalance(String customerId) {
        return customerWallets.getOrDefault(customerId, BigDecimal.ZERO);
    }

    private void registerListeners() {
        // Lắng nghe VoucherAppliedEvent (tiếp sau khi Voucher Service tính giảm giá xong)
        EventBroker.subscribe("voucher-applied-topic", event -> {
            if (event instanceof VoucherAppliedEvent voucherEvent) {
                handleVoucherApplied(voucherEvent);
            }
        });

        // Lắng nghe CompensatePaymentEvent (Yêu cầu hoàn tiền từ Order Service)
        EventBroker.subscribe("compensate-payment-topic", event -> {
            if (event instanceof CompensatePaymentEvent compensateEvent) {
                handleCompensatePayment(compensateEvent);
            }
        });
    }

    private void handleVoucherApplied(VoucherAppliedEvent event) {
        System.out.println("[PAYMENT-SAGA] Nhận VoucherAppliedEvent cho đơn: " + event.getOrderId()
                + " | Số tiền sau giảm giá: " + event.getFinalAmount());

        BigDecimal currentBalance = getCustomerBalance(event.getCustomerId());

        if (currentBalance.compareTo(event.getFinalAmount()) >= 0) {
            BigDecimal newBalance = currentBalance.subtract(event.getFinalAmount());
            customerWallets.put(event.getCustomerId(), newBalance);
            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);

            System.out.println("[PAYMENT-SAGA] Trừ tiền THÀNH CÔNG khách " + event.getCustomerId()
                    + " | Số tiền trừ: " + event.getFinalAmount() + " | Số dư còn lại: " + newBalance);

            PaymentSuccessEvent paymentSuccessEvent = PaymentSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .paymentId(paymentId)
                    .amountDeducted(event.getFinalAmount())
                    .shippingAddress(event.getShippingAddress())
                    .timestamp(System.currentTimeMillis())
                    .build();

            EventBroker.publish("payment-success-topic", paymentSuccessEvent);
        } else {
            System.out.println("[PAYMENT-SAGA] Khách " + event.getCustomerId() + " KHÔNG ĐỦ TIỀN thanh toán đơn " + event.getOrderId());
        }
    }

    private void handleCompensatePayment(CompensatePaymentEvent event) {
        System.out.println("[PAYMENT-SAGA] [COMPENSATION] Hoàn tiền đơn: " + event.getOrderId() + " | Lý do: " + event.getReason());

        BigDecimal currentBalance = getCustomerBalance(event.getCustomerId());
        BigDecimal newBalance = currentBalance.add(event.getRefundAmount());
        customerWallets.put(event.getCustomerId(), newBalance);

        String refundId = "REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[PAYMENT-SAGA] HOÀN TIỀN THÀNH CÔNG khách " + event.getCustomerId()
                + " | Số tiền hoàn: " + event.getRefundAmount() + " | Số dư mới: " + newBalance);

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
