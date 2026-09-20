package microservice.orders_service.saga.events;

public enum OrderStatus {
    PENDING,                  // Khởi tạo đơn
    WAITING_FOR_SHIPPING,     // Đã thanh toán thành công, chờ giao hàng
    COMPLETED,                // Giao hàng thành công -> Đơn hoàn tất
    SHIPPING_FAILED,          // Giao hàng thất bại
    COMPENSATING_PAYMENT,     // Đang yêu cầu Payment Service hoàn tiền
    CANCELLED,                // Đã hoàn tiền & Hủy đơn thành công
    SHIPPING_TIMED_OUT        // Quá thời gian 30s không phản hồi từ Shipping Service
}
