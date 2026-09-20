package microservice.shippingservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Waybill {
    private String waybillCode;
    private String orderId;
    private String customerId;
    private String shippingAddress;
    private String carrierName;
    private String status; // "CREATED", "DELIVERED", "FAILED"
    private long createdAt;
}
