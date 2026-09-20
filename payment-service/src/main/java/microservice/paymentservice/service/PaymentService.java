package microservice.paymentservice.service;

import microservice.paymentservice.event.CompensatePaymentEvent;
import microservice.paymentservice.event.OrderCreatedEvent;
import microservice.paymentservice.event.PaymentSuccessEvent;
import microservice.paymentservice.event.RefundSuccessEvent;
import microservice.paymentservice.model.Wallet;
import microservice.paymentservice.publisher.PaymentEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    private final Map<String, Wallet> walletRepository = new ConcurrentHashMap<>();

    @Autowired
    private PaymentEventPublisher eventPublisher;

    public PaymentService() {
        walletRepository.put("CUST-001", Wallet.builder().customerId("CUST-001").balance(new BigDecimal("1000000")).lastUpdated(System.currentTimeMillis()).build());
        walletRepository.put("CUST-002", Wallet.builder().customerId("CUST-002").balance(new BigDecimal("50000")).lastUpdated(System.currentTimeMillis()).build());
    }

    public void setBalance(String customerId, BigDecimal balance) {
        walletRepository.put(customerId, Wallet.builder().customerId(customerId).balance(balance).lastUpdated(System.currentTimeMillis()).build());
    }

    public BigDecimal getBalance(String customerId) {
        Wallet wallet = walletRepository.get(customerId);
        return wallet != null ? wallet.getBalance() : BigDecimal.ZERO;
    }

    public void processOrderCreated(OrderCreatedEvent event) {
        System.out.println("[PAYMENT-SERVICE] Nhận OrderCreatedEvent cho đơn hàng: " + event.getOrderId());
        BigDecimal currentBalance = getBalance(event.getCustomerId());

        if (currentBalance.compareTo(event.getTotalAmount()) >= 0) {
            BigDecimal newBalance = currentBalance.subtract(event.getTotalAmount());
            walletRepository.put(event.getCustomerId(), Wallet.builder().customerId(event.getCustomerId()).balance(newBalance).lastUpdated(System.currentTimeMillis()).build());

            String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("[PAYMENT-SERVICE] Trừ tiền THÀNH CÔNG khách " + event.getCustomerId()
                    + " | Số tiền: " + event.getTotalAmount() + " | Số dư mới: " + newBalance);

            PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .paymentId(paymentId)
                    .amountDeducted(event.getTotalAmount())
                    .shippingAddress(event.getShippingAddress())
                    .timestamp(System.currentTimeMillis())
                    .build();

            eventPublisher.publishPaymentSuccess(successEvent);
        } else {
            System.out.println("[PAYMENT-SERVICE] Khách " + event.getCustomerId() + " KHÔNG ĐỦ TIỀN thanh toán đơn " + event.getOrderId());
        }
    }

    public void processCompensatePayment(CompensatePaymentEvent event) {
        System.out.println("[PAYMENT-SERVICE] [COMPENSATION] Nhận CompensatePaymentEvent cho đơn: " + event.getOrderId() + " | Lý do: " + event.getReason());

        BigDecimal currentBalance = getBalance(event.getCustomerId());
        BigDecimal newBalance = currentBalance.add(event.getRefundAmount());
        walletRepository.put(event.getCustomerId(), Wallet.builder().customerId(event.getCustomerId()).balance(newBalance).lastUpdated(System.currentTimeMillis()).build());

        String refundId = "REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[PAYMENT-SERVICE] HOÀN TIỀN THÀNH CÔNG khách " + event.getCustomerId()
                + " | Số tiền hoàn: " + event.getRefundAmount() + " | Số dư mới: " + newBalance);

        RefundSuccessEvent refundEvent = RefundSuccessEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .refundId(refundId)
                .refundedAmount(event.getRefundAmount())
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.publishRefundSuccess(refundEvent);
    }
}
