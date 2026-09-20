package microservice.orders_service.saga.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundSuccessEvent {
    private String orderId;
    private String customerId;
    private String refundId;
    private BigDecimal refundedAmount;
    private long timestamp;
}
