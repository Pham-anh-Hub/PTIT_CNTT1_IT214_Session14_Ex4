package microservice.orders_service.saga.broker;

import java.util.*;

/**
 * Mô phỏng Message Broker (như Confluent Kafka / RabbitMQ)
 * Cho phép đăng ký listener theo Topic và publish Event tới các Consumer một cách bất đồng bộ.
 */
public class EventBroker {
    private static final Map<String, List<EventListener>> listeners = new HashMap<>();

    @FunctionalInterface
    public interface EventListener {
        void onEvent(Object event);
    }

    public static synchronized void subscribe(String topic, EventListener listener) {
        listeners.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
    }

    public static void publish(String topic, Object event) {
        System.out.println("  --> [BROKER PUBLISH] Topic: '" + topic + "' | Event Payload: " + event.getClass().getSimpleName() + " " + event);
        List<EventListener> topicListeners;
        synchronized (EventBroker.class) {
            topicListeners = new ArrayList<>(listeners.getOrDefault(topic, Collections.emptyList()));
        }
        for (EventListener listener : topicListeners) {
            // Chạy async hoặc sync cho mô phỏng
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("  [BROKER ERROR] Lỗi khi xử lý event trên topic " + topic + ": " + e.getMessage());
            }
        }
    }

    public static synchronized void clearAll() {
        listeners.clear();
    }
}
