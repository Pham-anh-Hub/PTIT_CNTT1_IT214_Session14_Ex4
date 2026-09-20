package microservice.shippingservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent {
    private String orderId;
    private String customerId;
    private String paymentId;
    private BigDecimal amountDeducted;
    private String shippingAddress;
    private long timestamp;
}
