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
public class CompensatePaymentEvent {
    private String orderId;
    private String customerId;
    private BigDecimal refundAmount;
    private String reason; // "SHIPPING_FAILED" hoặc "SHIPPING_TIMEOUT"
    private long timestamp;
}
