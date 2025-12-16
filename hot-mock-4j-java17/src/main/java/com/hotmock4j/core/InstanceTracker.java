package com.hotmock4j.core;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Instance tracker for tracking and managing created instances
 */
public class InstanceTracker {
    
    private static final InstanceTracker INSTANCE = new InstanceTracker();
    private final Map<String, Set<WeakReference<Object>>> instancesByClass = new ConcurrentHashMap<>();
    
    private InstanceTracker() {}
    
    public static InstanceTracker getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register instance
     */
    public static void registerInstance(String className, Object instance) {
        getInstance().instancesByClass.computeIfAbsent(className, k -> Collections.synchronizedSet(new HashSet<>()))
                       .add(new WeakReference<>(instance));
        System.out.println("Registered instance of " + className + ": " + instance);
    }
    
    /**
     * Get all instances of specified class
     */
    public List<Object> getInstances(String className) {
        Set<WeakReference<Object>> instanceRefs = instancesByClass.get(className);
        if (instanceRefs == null) {
            return Collections.emptyList();
        }
        
        List<Object> instances = new ArrayList<>();
        Iterator<WeakReference<Object>> iterator = instanceRefs.iterator();
        while (iterator.hasNext()) {
            WeakReference<Object> ref = iterator.next();
            Object instance = ref.get();
            if (instance != null) {
                instances.add(instance);
            } else {
                // Clean up references that have been garbage collected
                iterator.remove();
            }
        }
        
        return instances;
    }
    
    /**
     * Update field values for all instances of specified class
     */
    public void updateFieldValues(String className, String fieldName, Object fieldValue) {
        List<Object> instances = getInstances(className);
        System.out.println("Updating field " + fieldName + " for " + instances.size() + " instances of " + className);
        
        for (Object instance : instances) {
            try {
                java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(instance, fieldValue);
                System.out.println("Updated field " + fieldName + " = " + fieldValue + " for instance: " + instance);
            } catch (Exception e) {
                System.err.println("Failed to update field " + fieldName + " for instance " + instance + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Clear all instance references
     */
    public void clear() {
        instancesByClass.clear();
    }
}
