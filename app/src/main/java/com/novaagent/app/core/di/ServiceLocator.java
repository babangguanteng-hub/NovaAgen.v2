package com.novaagent.app.core.di;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manual Dependency Injection berbasis Service Locator.
 * Menghindari overhead reflection/code-gen Dagger/Hilt demi performa Cold Start Android Go.
 */
public class ServiceLocator {
    private static volatile ServiceLocator instance;
    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    private ServiceLocator() {}

    public static ServiceLocator getInstance() {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) {
                    instance = new ServiceLocator();
                }
            }
        }
        return instance;
    }

    public <T> void register(Class<T> serviceClass, T serviceInstance) {
        services.put(serviceClass, serviceInstance);
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> serviceClass) {
        Object service = services.get(serviceClass);
        if (service == null) {
            throw new IllegalStateException("Service belum diregistrasi: " + serviceClass.getSimpleName());
        }
        return (T) service;
    }
}
