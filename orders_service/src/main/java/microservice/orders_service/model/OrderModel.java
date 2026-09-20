package microservice.orders_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import microservice.orders_service.saga.events.OrderStatus;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModel {
    private String orderId;
    private String customerId;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private OrderStatus status;
    private String paymentId;
    private String waybillCode;
    private long createdAt;
    private long updatedAt;
}
