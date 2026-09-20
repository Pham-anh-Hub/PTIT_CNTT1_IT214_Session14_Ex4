package microservice.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensatePaymentEvent {
    private String orderId;
    private String customerId;
    private BigDecimal refundAmount;
    private String reason;
    private long timestamp;
}
