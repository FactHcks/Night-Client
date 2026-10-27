package me.night.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    private static class MethodData {
        final Object source;
        final Method method;
        final byte priority;

        MethodData(Object source, Method method, byte priority) {
            this.source = source;
            this.method = method;
            this.priority = priority;
            this.method.setAccessible(true);
        }
    }

    private final Map<Class<? extends Event>, List<MethodData>> registry = new HashMap<>();

    public synchronized void register(Object listener) {
        if (listener == null) return;
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventTarget.class) && method.getParameterCount() == 1) {
                Class<?> param = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(param)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventClass = (Class<? extends Event>) param;
                    EventTarget target = method.getAnnotation(EventTarget.class);
                    MethodData data = new MethodData(listener, method, target.priority());

                    List<MethodData> list = registry.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
                    list.add(data);
                    list.sort((a, b) -> Byte.compare(b.priority, a.priority));
                }
            }
        }
    }

    public synchronized void unregister(Object listener) {
        if (listener == null) return;
        for (List<MethodData> list : registry.values()) {
            list.removeIf(data -> data.source == listener);
        }
    }

    public <T extends Event> T post(T event) {
        if (event == null) return null;
        List<MethodData> list = registry.get(event.getClass());
        if (list != null && !list.isEmpty()) {
            for (MethodData data : list) {
                try {
                    data.method.invoke(data.source, event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return event;
    }
}
