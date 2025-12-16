package com.hotmock4j.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MockPlan Manager, responsible for CRUD operations and persistence of MockPlans
 */
public class MockPlanManager {
    
    private static final MockPlanManager INSTANCE = new MockPlanManager();
    private final Map<String, MockPlan> mockPlans = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String STORAGE_DIR = "mock-plans";
    

    private MockPlanManager() {
        // Create storage directory
        try {
            Files.createDirectories(Paths.get(STORAGE_DIR));
            loadAllFromFiles();
        } catch (IOException e) {
            System.err.println("Failed to create storage directory: " + e.getMessage());
        }
    }
    
    public static MockPlanManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Create a new MockPlan
     */
    public MockPlan createMockPlan(String planName, MockProject project) {
        String key = generateKey(project, planName);
        if (mockPlans.containsKey(key)) {
            throw new IllegalArgumentException("MockPlan already exists: " + planName);
        }
        
        MockPlan mockPlan = new MockPlan(project, planName);
        mockPlan.setCreationDate(new Date());
        mockPlan.setJsonFileName(generateJsonFileName(planName));
        mockPlan.setActive(false);
        
        mockPlans.put(key, mockPlan);
        saveToFile(mockPlan);
        
        return mockPlan;
    }
    
    /**
     * Get all MockPlans
     */
    public List<MockPlan> getAllMockPlans() {
        return new ArrayList<>(mockPlans.values());
    }
    
    /**
     * Get MockPlan by project and name
     */
    public MockPlan getMockPlan(MockProject project, String planName) {
        String key = generateKey(project, planName);
        return mockPlans.get(key);
    }
    
    /**
     * Delete MockPlan
     */
    public boolean deleteMockPlan(MockProject project, String planName) {
        String key = generateKey(project, planName);
        MockPlan mockPlan = mockPlans.remove(key);
        if (mockPlan != null) {
            deleteFile(mockPlan);
            return true;
        }
        return false;
    }

    /**
     * Delete all MockClass entries that match className in the specified MockPlan
     */
    public boolean deleteMockClass(String projectName, String planName, String className) {
        MockPlan mockPlan = getMockPlan(projectName, planName);
        if (mockPlan == null || className == null || className.trim().isEmpty()) {
            return false;
        }
        List<MockClass> list = mockPlan.getMockClassList();
        if (list == null || list.isEmpty()) {
            return false;
        }
        int before = list.size();
        list.removeIf(mc -> className.equals(mc.getClassName()));
        boolean removed = list.size() < before;
        if (removed) {
            saveToFile(mockPlan);
        }
        return removed;
    }
    
    /**
     * Activate MockPlan
     */
    public boolean activateMockPlan(MockProject project, String planName) {
        // First deactivate all other MockPlans
        mockPlans.values().forEach(plan -> plan.setActive(false));
        
        // Activate the specified MockPlan
        String key = generateKey(project, planName);
        MockPlan mockPlan = mockPlans.get(key);
        if (mockPlan != null) {
            mockPlan.setActive(true);
            saveToFile(mockPlan);
            return true;
        }
        return false;
    }
    
    /**
     * Get currently active MockPlan
     */
    public MockPlan getActiveMockPlan() {
        return mockPlans.values().stream()
                .filter(MockPlan::isActive)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Add class to MockPlan
     */
    public boolean addClassToMockPlan(String projectName, String planName, String className) {
        MockPlan mockPlan = getMockPlan(projectName, planName);
        if (mockPlan == null) {
            return false;
        }
        // Allow adding the same class multiple times; uniqueness is enforced by templateName
        // Create new MockClass
        MockClass mockClass = new MockClass("", className);
        mockPlan.getMockClassList().add(mockClass);
        
        saveMockPlans();
        return true;
    }
    
    /**
     * Update field mock values for MockClass
     */
    public boolean updateMockClass(String projectName, String planName, MockClass updatedClass) {
        MockPlan mockPlan = getMockPlan(projectName, planName);
        boolean isUpdate = false;
        if (mockPlan == null) {
            return isUpdate;
        }
        
        // Validate templateName uniqueness within the same mock plan
        if (updatedClass.getTemplateName() != null && !updatedClass.getTemplateName().trim().isEmpty()) {
            if (!isTemplateNameUnique(mockPlan, updatedClass.getTemplateName(), updatedClass.getClassName())) {
                throw new IllegalArgumentException("Template name '" + updatedClass.getTemplateName() + "' already exists in this mock plan");
            }
        }
        
        // Find and update the corresponding MockClass
        // Prefer exact match by className + templateName
        for (MockClass mockClass : mockPlan.getMockClassList()) {
            if (mockClass.getClassName().equals(updatedClass.getClassName())
                    && safeEquals(mockClass.getTemplateName(), updatedClass.getTemplateName())) {
                mockClass.setFields(updatedClass.getFields());
                mockClass.setMethods(updatedClass.getMethods());
                mockClass.setTemplateName(updatedClass.getTemplateName());
                saveMockPlans();
                isUpdate = true;
                break;
            }
        }

        if (!isUpdate) {
            mockPlan.putMockClass(updatedClass);
            isUpdate = true;
        }

        // If MockPlan is active, need to update mock field values for loaded classes
        if (mockPlan.isActive()) {
            updateAttachedMockFields(updatedClass);
        }
        
        return isUpdate;
    }
    
    /**
     * Get MockClass from MockPlan
     */
    public MockClass getMockClass(String projectName, String planName, String className) {
        MockPlan mockPlan = getMockPlan(projectName, planName);
        if (mockPlan == null) {
            return null;
        }
        
        for (MockClass mockClass : mockPlan.getMockClassList()) {
            if (mockClass.getClassName().equals(className)) {
                return mockClass;
            }
        }
        
        return null;
    }
    
    /**
     * Get MockPlan by project name and plan name
     */
    public MockPlan getMockPlan(String projectName, String planName) {
        MockProject project = new MockProject(projectName);
        return getMockPlan(project, planName);
    }
    
    /**
     * Generate storage key
     */
    private String generateKey(MockProject project, String planName) {
        return project.getProjectName() + "::" + planName;
    }
    
    /**
     * Generate JSON file name
     */
    private String generateJsonFileName(String planName) {
        return planName + ".json";
    }
    
    /**
     * Save all MockPlans to files
     */
    private void saveMockPlans() {
        for (MockPlan mockPlan : mockPlans.values()) {
            saveToFile(mockPlan);
        }
    }
    
    private void saveToFile(MockPlan mockPlan) {
        try {
            String fileName = mockPlan.getJsonFileName();
            File file = new File(STORAGE_DIR, fileName);
            objectMapper.writeValue(file, mockPlan);
        } catch (IOException e) {
            System.err.println("Failed to save MockPlan to file: " + e.getMessage());
        }
    }
    
    private void deleteFile(MockPlan mockPlan) {
        try {
            String fileName = mockPlan.getJsonFileName();
            File file = new File(STORAGE_DIR, fileName);
            if (file.exists()) {
                Files.delete(file.toPath());
            }
        } catch (IOException e) {
            System.err.println("Failed to delete MockPlan file: " + e.getMessage());
        }
    }
    
    /**
     * Update mock field values for loaded classes
     * When MockPlan is activated, need to apply mock configuration to loaded classes
     */
    private void updateAttachedMockFields(MockClass mockClass) {
        System.out.println("Updating mock fields for class: " + mockClass.getClassName());
        
        // If there are field mock configurations, need to apply to loaded classes
        if (mockClass.getFields() != null) {
            for (MockField mockField : mockClass.getFields()) {
                if (mockField.getMockFieldValue() != null || mockField.getActiveTemplate() != null) {
                    System.out.println("Mock field: " + mockField.getFieldName() + " = " + mockField.getMockFieldValue());
                    // Use reflection to modify field values of existing instances
                    updateFieldValues(mockClass.getClassName(), mockField);
                }
            }
        }
        
        // If there are method mock configurations, need to apply to loaded classes
        if (mockClass.getMethods() != null) {
            for (MockMethod mockMethod : mockClass.getMethods()) {
                if (mockMethod.getReturnObject() != null) {
                    System.out.println("Mock method: " + mockMethod.getMethodName() + " returns " + mockMethod.getReturnObject());
                }
            }
        }
        
        // Retransform class through bytecode enhancement (affects newly created instances)
        retransformClass(mockClass.getClassName());
    }
    
    /**
     * Use reflection to modify field values of existing instances
     */
    private void updateFieldValues(String className, MockField mockField) {
        try {
            // Get target class
            Class<?> targetClass = Class.forName(className);
            
            // Update static fields
            updateStaticField(targetClass, mockField);
            
            // Update instance fields (requires instance tracking mechanism)
            updateInstanceFields(targetClass, mockField);
            
        } catch (Exception e) {
            System.err.println("Failed to update field values for " + className + "." + mockField.getFieldName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Update instance field values
     * Use reflection to modify field values of existing instances
     */
    private void updateInstanceFields(Class<?> targetClass, MockField mockField) {
        try {
            Field field = targetClass.getDeclaredField(mockField.getFieldName());
            field.setAccessible(true);
            
            // Check if it's an instance field
            if (!Modifier.isStatic(field.getModifiers())) {
                System.out.println("Updating instance field: " + targetClass.getName() + "." + mockField.getFieldName());
                
                // Get all loaded instances and modify field values
                updateAllInstances(targetClass, field, mockField);
            }
        } catch (Exception e) {
            System.err.println("Failed to update instance field " + targetClass.getName() + "." + mockField.getFieldName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Update field values for all existing instances
     */
    private void updateAllInstances(Class<?> targetClass, Field field, MockField mockField) {
        try {
            // Use InstanceTracker to update field values for all registered instances
            InstanceTracker instanceTracker = InstanceTracker.getInstance();

            Object valueToSet = null;

            // Prefer template mounting for complex types
            if (mockField.getActiveTemplate() != null && mockField.getFieldClassName() != null
                    && !mockField.getActiveTemplate().trim().isEmpty()) {
                try {
                    MockPlan activePlan = getActiveMockPlan();
                    if (activePlan != null && activePlan.getMockClassList() != null) {
                        MockClass templateClass = activePlan.getMockClassList().stream()
                                .filter(mc -> mockField.getFieldClassName().equals(mc.getClassName()))
                                .filter(mc -> mockField.getActiveTemplate().equals(mc.getTemplateName()))
                                .findFirst()
                                .orElse(null);
                        if (templateClass != null) {
                            valueToSet = buildInstanceFromTemplate(mockField.getFieldClassName(), templateClass);
                        } else {
                            System.err.println("Template not found for field '" + mockField.getFieldName() + "': "
                                    + mockField.getFieldClassName() + "#" + mockField.getActiveTemplate());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to build instance from template for field '" + mockField.getFieldName() + "': " + e.getMessage());
                }
            }

            // Fallback to simple mock value
            if (valueToSet == null && mockField.getMockFieldValue() != null) {
                valueToSet = convertToReflectionType(mockField.getMockFieldValue().toString(), field.getType());
            }

            if (valueToSet != null) {
                instanceTracker.updateFieldValues(targetClass.getName(), mockField.getFieldName(), valueToSet);
                System.out.println("Updated instance field " + targetClass.getName() + "." + mockField.getFieldName() + " = " + valueToSet);
            }
        } catch (Exception e) {
            System.err.println("Failed to update all instances: " + e.getMessage());
        }
    }

    /**
     * Static helper for ASM: build an instance for the given class using the specified template
     * from the currently active MockPlan. Returns null if not found or on failure.
     */
    public static Object buildInstanceFromActiveTemplate(String className, String templateName) {
        try {
            MockPlanManager mgr = getInstance();
            MockPlan activePlan = mgr.getActiveMockPlan();
            if (activePlan == null || activePlan.getMockClassList() == null) {
                return null;
            }
            MockClass templateClass = activePlan.getMockClassList().stream()
                    .filter(mc -> className.equals(mc.getClassName()))
                    .filter(mc -> templateName != null && templateName.equals(mc.getTemplateName()))
                    .findFirst()
                    .orElse(null);
            if (templateClass == null) {
                return null;
            }
            return mgr.buildInstanceFromTemplate(className, templateClass);
        } catch (Exception e) {
            System.err.println("buildInstanceFromActiveTemplate failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Build a new instance for the given class name and populate its fields from a template definition.
     * Supports recursive population when field templates reference other class templates.
     */
    private Object buildInstanceFromTemplate(String className, MockClass templateClass) {
        return buildInstanceFromTemplate(className, templateClass, new HashSet<>());
    }

    /**
     * Internal recursive helper with cycle detection via visited set (class#template).
     */
    private Object buildInstanceFromTemplate(String className, MockClass templateClass, Set<String> visited) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (templateClass.getFields() != null) {
                for (MockField tf : templateClass.getFields()) {
                    try {
                        java.lang.reflect.Field f = clazz.getDeclaredField(tf.getFieldName());
                        f.setAccessible(true);

                        // 1) Simple mock value (primitives / String)
                        if (tf.getMockFieldValue() != null) {
                            Object v = convertToReflectionType(tf.getMockFieldValue().toString(), f.getType());
                            if (v != null) {
                                f.set(instance, v);
                                continue;
                            }
                        }

                        // 2) Recursive template mounting for reference types
                        if (tf.getActiveTemplate() != null && tf.getFieldClassName() != null
                                && !tf.getActiveTemplate().trim().isEmpty()) {
                            try {
                                MockPlan activePlan = getActiveMockPlan();
                                if (activePlan != null && activePlan.getMockClassList() != null) {
                                    String nestedKey = tf.getFieldClassName() + "#" + tf.getActiveTemplate();
                                    if (visited.contains(nestedKey)) {
                                        // Prevent infinite loops on cyclic references
                                        continue;
                                    }
                                    MockClass nestedTemplate = activePlan.getMockClassList().stream()
                                            .filter(mc -> tf.getFieldClassName().equals(mc.getClassName()))
                                            .filter(mc -> tf.getActiveTemplate().equals(mc.getTemplateName()))
                                            .findFirst()
                                            .orElse(null);
                                    if (nestedTemplate != null) {
                                        visited.add(nestedKey);
                                        Object nestedInstance = buildInstanceFromTemplate(tf.getFieldClassName(), nestedTemplate, visited);
                                        if (nestedInstance != null) {
                                            f.set(instance, nestedInstance);
                                        }
                                    }
                                }
                            } catch (Exception nestedEx) {
                                System.err.println("Failed to build nested template for field '" + tf.getFieldName() + "' of class '" + className + "': " + nestedEx.getMessage());
                            }
                        }
                    } catch (NoSuchFieldException nsf) {
                        // ignore missing fields
                    }
                }
            }

            return instance;
        } catch (Exception e) {
            System.err.println("Failed to build instance from template for class '" + className + "': " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Add InstanceRegistrationTransformer to Instrumentation
     */
    private void addInstanceRegistrationTransformer() {
        try {
            if (com.hotmock4j.agent.AgentBootstrap.ins != null) {
                com.hotmock4j.agent.transformer.InstanceRegistrationTransformer transformer =
                    new com.hotmock4j.agent.transformer.InstanceRegistrationTransformer();
                com.hotmock4j.agent.AgentBootstrap.ins.addTransformer(transformer, true);
                System.out.println("InstanceRegistrationTransformer added to instrumentation");
            }
        } catch (Exception e) {
            System.err.println("Failed to add InstanceRegistrationTransformer: " + e.getMessage());
        }
    }
    
    /**
     * Update static field values
     */
    private void updateStaticField(Class<?> targetClass, MockField mockField) {
        try {
            java.lang.reflect.Field field = targetClass.getDeclaredField(mockField.getFieldName());
            field.setAccessible(true);
            
            // Check if it's a static field
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                Object mockValue = convertToReflectionType(mockField.getMockFieldValue().toString(), field.getType());
                if (mockValue != null) {
                    field.set(null, mockValue);
                    System.out.println("Updated static field: " + targetClass.getName() + "." + mockField.getFieldName() + " = " + mockValue);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update static field " + targetClass.getName() + "." + mockField.getFieldName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Convert string value to type used by reflection
     */
    private Object convertToReflectionType(String value, Class<?> type) {
        try {
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (type == byte.class || type == Byte.class) {
                return Byte.parseByte(value);
            } else if (type == char.class || type == Character.class) {
                return value.charAt(0);
            } else if (type == short.class || type == Short.class) {
                return Short.parseShort(value);
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(value);
            } else if (type == float.class || type == Float.class) {
                return Float.parseFloat(value);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(value);
            } else if (type == String.class) {
                return value;
            } else {
                // For other reference types, temporarily return null
                return null;
            }
        } catch (Exception e) {
            System.err.println("Failed to convert mock value '" + value + "' to type " + type + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retransform specified class
     */
    private void retransformClass(String className) {
        try {
            // Get Instrumentation instance
            if (com.hotmock4j.agent.AgentBootstrap.ins != null) {
                // Find loaded classes
                Class<?> targetClass = null;
                for (Class<?> clazz : com.hotmock4j.agent.AgentBootstrap.ins.getAllLoadedClasses()) {
                    if (clazz.getName().equals(className)) {
                        targetClass = clazz;
                        break;
                    }
                }
                
                if (targetClass != null) {
                    System.out.println("Retransforming class: " + className);
                    com.hotmock4j.agent.AgentBootstrap.ins.retransformClasses(targetClass);
                } else {
                    System.out.println("Class not loaded yet: " + className);
                }
            } else {
                System.out.println("Instrumentation not available, adding MockFieldTransformer");
                // If Instrumentation is not available, add MockFieldTransformer
                addMockFieldTransformer();
            }
        } catch (Exception e) {
            System.err.println("Failed to retransform class " + className + ": " + e.getMessage());
        }
    }
    
    /**
     * Add MockFieldTransformer to Instrumentation
     */
    private void addMockFieldTransformer() {
        try {
            if (com.hotmock4j.agent.AgentBootstrap.ins != null) {
                com.hotmock4j.agent.transformer.MockFieldTransformer transformer =
                    new com.hotmock4j.agent.transformer.MockFieldTransformer();
                com.hotmock4j.agent.AgentBootstrap.ins.addTransformer(transformer, true);
                System.out.println("MockFieldTransformer added to instrumentation");
            }
        } catch (Exception e) {
            System.err.println("Failed to add MockFieldTransformer: " + e.getMessage());
        }
    }
    
    /**
     * Check if template name is unique within the mock plan
     * @param mockPlan the mock plan to check
     * @param templateName the template name to validate
     * @param currentClassName the current class name (to exclude from check when updating)
     * @return true if template name is unique, false otherwise
     */
    private boolean isTemplateNameUnique(MockPlan mockPlan, String templateName, String currentClassName) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return true; // Empty template names are always considered unique
        }
        
        boolean skippedCurrent = false;
        for (MockClass mockClass : mockPlan.getMockClassList()) {
            if (templateName.equals(mockClass.getTemplateName())) {
                // allow exactly one occurrence for the current class when updating
                if (!skippedCurrent && currentClassName != null && currentClassName.equals(mockClass.getClassName())) {
                    skippedCurrent = true;
                    continue;
                }
                return false; // Template name already exists
            }
        }
        
        return true; // Template name is unique
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Load all MockPlans from file system
     */
    public void loadAllFromFiles() {
        try {
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                return;
            }
            
            File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try {
                        MockPlan mockPlan = objectMapper.readValue(file, MockPlan.class);
                        String key = generateKey(mockPlan.getProject(), mockPlan.getPlanName());
                        mockPlans.put(key, mockPlan);
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                        System.err.println("Failed to load MockPlan from file: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load MockPlans from files: " + e.getMessage());
        }
    }
}
