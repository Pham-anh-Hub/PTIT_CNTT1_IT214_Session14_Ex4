package microservice.orders_service.saga.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFailedEvent {
    private String orderId;
    private String customerId;
    private String reason;
    private long timestamp;
}
