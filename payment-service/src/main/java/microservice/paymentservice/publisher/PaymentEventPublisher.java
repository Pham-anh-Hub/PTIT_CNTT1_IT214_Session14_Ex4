package microservice.paymentservice.publisher;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.paymentservice.event.PaymentSuccessEvent;
import microservice.paymentservice.event.RefundSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        System.out.println("[PAYMENT-SERVICE] PUBLISH -> Topic: 'payment-success-topic' | PaymentId: " + event.getPaymentId());
        EventBroker.publish("payment-success-topic", event);
    }

    public void publishRefundSuccess(RefundSuccessEvent event) {
        System.out.println("[PAYMENT-SERVICE] PUBLISH -> Topic: 'refund-success-topic' | RefundId: " + event.getRefundId());
        EventBroker.publish("refund-success-topic", event);
    }
}
