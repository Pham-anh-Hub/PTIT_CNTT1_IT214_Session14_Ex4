package microservice.shippingservice.controller;

import microservice.shippingservice.model.Waybill;
import microservice.shippingservice.service.ShippingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    @Autowired
    private ShippingService shippingService;

    @GetMapping("/waybill/{orderId}")
    public ResponseEntity<Waybill> getWaybillByOrderId(@PathVariable String orderId) {
        Waybill waybill = shippingService.getWaybillByOrderId(orderId);
        if (waybill != null) {
            return ResponseEntity.ok(waybill);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/simulate-timeout")
    public ResponseEntity<String> setSimulateTimeout(@RequestParam boolean enable) {
        shippingService.setSimulateTimeoutMode(enable);
        return ResponseEntity.ok("Simulate timeout mode set to: " + enable);
    }
}
