package microservice.orders_service;

import microservice.orders_service.model.OrderModel;
import microservice.orders_service.publisher.OrderEventPublisher;
import microservice.orders_service.saga.broker.EventBroker;
import microservice.orders_service.saga.events.OrderStatus;
import microservice.orders_service.saga.events.ShippingSuccessEvent;
import microservice.orders_service.service.OrderService;
import microservice.paymentservice.publisher.PaymentEventPublisher;
import microservice.paymentservice.service.PaymentService;
import microservice.shippingservice.publisher.ShippingEventPublisher;
import microservice.shippingservice.service.ShippingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class MicroservicesSagaIntegrationTest {

    private OrderService orderService;
    private PaymentService paymentService;
    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        EventBroker.clearAll();

        // 1. Khởi tạo Orders Service
        orderService = new OrderService();
        OrderEventPublisher orderPublisher = new OrderEventPublisher();
        ReflectionTestUtils.setField(orderService, "eventPublisher", orderPublisher);

        microservice.orders_service.listener.OrderEventListener orderListener = new microservice.orders_service.listener.OrderEventListener();
        ReflectionTestUtils.setField(orderListener, "orderService", orderService);
        orderListener.initListeners();

        // 2. Khởi tạo Payment Service
        paymentService = new PaymentService();
        PaymentEventPublisher paymentPublisher = new PaymentEventPublisher();
        ReflectionTestUtils.setField(paymentService, "eventPublisher", paymentPublisher);

        microservice.paymentservice.listener.PaymentEventListener paymentListener = new microservice.paymentservice.listener.PaymentEventListener();
        ReflectionTestUtils.setField(paymentListener, "paymentService", paymentService);
        paymentListener.initListeners();

        // 3. Khởi tạo Shipping Service
        shippingService = new ShippingService();
        ShippingEventPublisher shippingPublisher = new ShippingEventPublisher();
        ReflectionTestUtils.setField(shippingService, "eventPublisher", shippingPublisher);

        microservice.shippingservice.listener.ShippingEventListener shippingListener = new microservice.shippingservice.listener.ShippingEventListener();
        ReflectionTestUtils.setField(shippingListener, "shippingService", shippingService);
        shippingListener.initListeners();

        // Nạp số dư cho ví khách hàng
        paymentService.setBalance("CUST-100", new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("Kịch bản 1: Giao tiếp 3 Microservices độc lập - Thành công (Happy Path)")
    void testMicroservicesHappyPath() {
        System.out.println("\n==========================================================================");
        System.out.println("TEST 1: 3 MICROSERVICES ĐỘC LẬP (ORDERS, PAYMENT, SHIPPING) - HAPPY PATH");
        System.out.println("==========================================================================");

        OrderModel order = orderService.createOrder("CUST-100", new BigDecimal("250000"), "123 Le Loi, District 1, HCMC");

        // Kiểm tra kết quả độc lập từ từng microservice
        OrderModel finalOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.COMPLETED, finalOrder.getStatus());
        assertNotNull(finalOrder.getPaymentId());
        assertNotNull(finalOrder.getWaybillCode());
        assertEquals(new BigDecimal("750000"), paymentService.getBalance("CUST-100"));
        assertNotNull(shippingService.getWaybillByOrderId(order.getOrderId()));

        System.out.println(">>> PASSED: Orders Service, Payment Service và Shipping Service giao tiếp thành công!");
    }

    @Test
    @DisplayName("Kịch bản 2: Giao tiếp 3 Microservices độc lập - Shipping Thất bại & Hoàn tiền Bù trừ")
    void testMicroservicesShippingFailedCompensation() {
        System.out.println("\n==========================================================================");
        System.out.println("TEST 2: 3 MICROSERVICES ĐỘC LẬP - SHIPPING FAILED & REFUND COMPENSATION");
        System.out.println("==========================================================================");

        OrderModel order = orderService.createOrder("CUST-100", new BigDecimal("400000"), "UNSUPPORTED - Đảo Xa");

        OrderModel finalOrder = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.CANCELLED, finalOrder.getStatus());
        assertEquals(new BigDecimal("1000000"), paymentService.getBalance("CUST-100"));

        System.out.println(">>> PASSED: Shipping Service báo lỗi -> Orders Service bù trừ -> Payment Service hoàn tiền 100%!");
    }

    @Test
    @DisplayName("Kịch bản 3: Giao tiếp 3 Microservices độc lập - Timeout 30s & Idempotent Event Trễ")
    void testMicroservicesShippingTimeoutCompensation() {
        System.out.println("\n==========================================================================");
        System.out.println("TEST 3: 3 MICROSERVICES ĐỘC LẬP - SHIPPING TIMEOUT 30S & IDEMPOTENT CHECK");
        System.out.println("==========================================================================");

        shippingService.setSimulateTimeoutMode(true);

        OrderModel order = orderService.createOrder("CUST-100", new BigDecimal("500000"), "456 Nguyen Hue, HCMC");

        assertEquals(OrderStatus.WAITING_FOR_SHIPPING, orderService.getOrder(order.getOrderId()).getStatus());
        assertEquals(new BigDecimal("500000"), paymentService.getBalance("CUST-100"));

        // Kích hoạt Timeout 30s
        orderService.triggerShippingTimeout(order.getOrderId());

        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(order.getOrderId()).getStatus());
        assertEquals(new BigDecimal("1000000"), paymentService.getBalance("CUST-100"));

        // Event trễ tới từ Shipping Service
        EventBroker.publish("shipping-success-topic", ShippingSuccessEvent.builder().orderId(order.getOrderId()).waybillCode("WB-LATE").timestamp(System.currentTimeMillis()).build());
        assertEquals(OrderStatus.CANCELLED, orderService.getOrder(order.getOrderId()).getStatus());

        System.out.println(">>> PASSED: Timeout 30s kích hoạt bù trừ hoàn tiền & Đảm bảo tính Idempotent chuẩn!");
    }
}
