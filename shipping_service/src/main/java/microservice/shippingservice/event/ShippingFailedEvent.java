package microservice.shippingservice.event;

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
