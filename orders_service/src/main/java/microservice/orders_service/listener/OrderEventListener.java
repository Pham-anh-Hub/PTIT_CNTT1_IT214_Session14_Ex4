package microservice.orders_service.listener;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.PaymentSuccessEvent;
import microservice.orders_service.saga.events.RefundSuccessEvent;
import microservice.orders_service.saga.events.ShippingFailedEvent;
import microservice.orders_service.saga.events.ShippingSuccessEvent;
import microservice.orders_service.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class OrderEventListener {

    @Autowired
    private OrderService orderService;

    @PostConstruct
    public void initListeners() {
        EventBroker.subscribe("payment-success-topic", event -> {
            if (event instanceof PaymentSuccessEvent) {
                orderService.processPaymentSuccess((PaymentSuccessEvent) event);
            }
        });

        EventBroker.subscribe("shipping-success-topic", event -> {
            if (event instanceof ShippingSuccessEvent) {
                orderService.processShippingSuccess((ShippingSuccessEvent) event);
            }
        });

        EventBroker.subscribe("shipping-failed-topic", event -> {
            if (event instanceof ShippingFailedEvent) {
                orderService.processShippingFailed((ShippingFailedEvent) event);
            }
        });

        EventBroker.subscribe("refund-success-topic", event -> {
            if (event instanceof RefundSuccessEvent) {
                orderService.processRefundSuccess((RefundSuccessEvent) event);
            }
        });
    }
}
