package microservice.orders_service.publisher;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.CompensatePaymentEvent;
import microservice.orders_service.saga.events.OrderCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    public void publishOrderCreated(OrderCreatedEvent event) {
        System.out.println("[ORDERS-SERVICE] 🔵 PUBLISH -> Topic: 'order-created-topic' | OrderId: " + event.getOrderId());
        EventBroker.publish("order-created-topic", event);
    }

    public void publishCompensatePayment(CompensatePaymentEvent event) {
        System.out.println("[ORDERS-SERVICE] 🔄 PUBLISH -> Topic: 'compensate-payment-topic' | Reason: " + event.getReason());
        EventBroker.publish("compensate-payment-topic", event);
    }
}
