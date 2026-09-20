package microservice.paymentservice.controller;

import microservice.paymentservice.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/wallet/{customerId}")
    public ResponseEntity<Map<String, Object>> getBalance(@PathVariable String customerId) {
        BigDecimal balance = paymentService.getBalance(customerId);
        return ResponseEntity.ok(Map.of("customerId", customerId, "balance", balance));
    }

    @PostMapping("/topup")
    public ResponseEntity<String> topUpBalance(@RequestParam String customerId, @RequestParam BigDecimal amount) {
        BigDecimal current = paymentService.getBalance(customerId);
        paymentService.setBalance(customerId, current.add(amount));
        return ResponseEntity.ok("Successfully topped up " + amount + " for customer " + customerId);
    }
}
