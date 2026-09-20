package microservice.orders_service.controller;

import microservice.orders_service.model.OrderModel;
import microservice.orders_service.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    public record CreateOrderRequest(String customerId, BigDecimal totalAmount, String shippingAddress) {}

    @PostMapping
    public ResponseEntity<OrderModel> createOrder(@RequestBody CreateOrderRequest request) {
        OrderModel order = orderService.createOrder(request.customerId(), request.totalAmount(), request.shippingAddress());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderModel> getOrder(@PathVariable String orderId) {
        OrderModel order = orderService.getOrder(orderId);
        if (order != null) {
            return ResponseEntity.ok(order);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{orderId}/trigger-timeout")
    public ResponseEntity<String> triggerTimeout(@PathVariable String orderId) {
        orderService.triggerShippingTimeout(orderId);
        return ResponseEntity.ok("Timeout triggered for order: " + orderId);
    }
}
