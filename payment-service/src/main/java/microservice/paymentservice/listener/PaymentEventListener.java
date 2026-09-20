package microservice.paymentservice.listener;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.paymentservice.event.CompensatePaymentEvent;
import microservice.paymentservice.event.OrderCreatedEvent;
import microservice.paymentservice.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class PaymentEventListener {

    @Autowired
    private PaymentService paymentService;

    @PostConstruct
    public void initListeners() {
        EventBroker.subscribe("order-created-topic", event -> {
            if (event instanceof OrderCreatedEvent) {
                paymentService.processOrderCreated((OrderCreatedEvent) event);
            }
        });

        EventBroker.subscribe("compensate-payment-topic", event -> {
            if (event instanceof CompensatePaymentEvent) {
                paymentService.processCompensatePayment((CompensatePaymentEvent) event);
            }
        });
    }
}
