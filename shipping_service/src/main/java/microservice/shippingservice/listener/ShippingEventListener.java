package microservice.shippingservice.listener;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.shippingservice.event.PaymentSuccessEvent;
import microservice.shippingservice.service.ShippingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ShippingEventListener {

    @Autowired
    private ShippingService shippingService;

    @PostConstruct
    public void initListeners() {
        EventBroker.subscribe("payment-success-topic", event -> {
            if (event instanceof PaymentSuccessEvent) {
                shippingService.processPaymentSuccess((PaymentSuccessEvent) event);
            }
        });
    }
}
