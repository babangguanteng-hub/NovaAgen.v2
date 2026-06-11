package com.novaagent.app.core.bus;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom EventBus untuk Android Go.
 * Menggantikan callback hell tanpa menggunakan library eksternal berat (seperti GreenRobot/RxJava).
 */
public class EventBus {
    private static volatile EventBus instance;
    private final ConcurrentHashMap<AgentEvent.EventType, List<EventListener>> listeners = new ConcurrentHashMap<>();
    
    // Executor tunggal untuk memproses event di background agar UI/Accessibility tidak freeze
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface EventListener {
        void onEvent(AgentEvent event);
    }

    private EventBus() {}

    public static EventBus getInstance() {
        if (instance == null) {
            synchronized (EventBus.class) {
                if (instance == null) {
                    instance = new EventBus();
                }
            }
        }
        return instance;
    }

    public void subscribe(AgentEvent.EventType type, EventListener listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(AgentEvent.EventType type, EventListener listener) {
        List<EventListener> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void publish(AgentEvent event) {
        List<EventListener> list = listeners.get(event.type);
        if (list != null && !list.isEmpty()) {
            executor.execute(() -> {
                for (EventListener listener : list) {
                    listener.onEvent(event);
                }
            });
        }
    }
}
