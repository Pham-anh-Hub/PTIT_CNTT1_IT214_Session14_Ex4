package microservice.shippingservice.publisher;

import microservice.orders_service.saga.broker.EventBroker;
import microservice.shippingservice.event.ShippingFailedEvent;
import microservice.shippingservice.event.ShippingSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class ShippingEventPublisher {

    public void publishShippingSuccess(ShippingSuccessEvent event) {
        System.out.println("[SHIPPING-SERVICE] 🟢 PUBLISH -> Topic: 'shipping-success-topic' | Waybill: " + event.getWaybillCode());
        EventBroker.publish("shipping-success-topic", event);
    }

    public void publishShippingFailed(ShippingFailedEvent event) {
        System.out.println("[SHIPPING-SERVICE] 🔴 PUBLISH -> Topic: 'shipping-failed-topic' | Reason: " + event.getReason());
        EventBroker.publish("shipping-failed-topic", event);
    }
}
