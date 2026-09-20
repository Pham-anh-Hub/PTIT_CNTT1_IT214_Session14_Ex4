package microservice.shippingservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingSuccessEvent {
    private String orderId;
    private String waybillCode;
    private String carrierName;
    private long timestamp;
}
