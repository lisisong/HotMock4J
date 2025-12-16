package com.hotmock4j.core;

import com.hotmock4j.agent.AgentBootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ClassSearchService {
    
    private static final ClassSearchService INSTANCE = new ClassSearchService();
    
    private ClassSearchService() {
    }
    
    public static ClassSearchService getInstance() {
        return INSTANCE;
    }
    

    public List<String> getAllClassNames() {
        Map<String, Class> classMap = AgentBootstrap.classMap;
        if (classMap == null || classMap.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(classMap.keySet());
    }

    public List<String> searchClasses(String keyword) {
        for (Class<?> clazz : AgentBootstrap.ins.getAllLoadedClasses()) {
            AgentBootstrap.classMap.put(clazz.getName(), clazz);
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllClassNames();
        }
        String searchTerm = keyword.toLowerCase().trim();
        Map<String, Class> classMap = AgentBootstrap.classMap;
        
        if (classMap == null || classMap.isEmpty()) {
            return new ArrayList<>();
        }
        
        return classMap.keySet().stream()
                .filter(className -> className.toLowerCase().contains(searchTerm))
                .sorted()
                .collect(Collectors.toList());
    }
    

    public MockClass getClassInfo(String className) {
        for (Class<?> clazz : AgentBootstrap.ins.getAllLoadedClasses()) {
            AgentBootstrap.classMap.put(clazz.getName(), clazz);
        }
        Map<String, Class> classMap = AgentBootstrap.classMap;
        if (classMap == null || !classMap.containsKey(className)) {
            return null;
        }
        
        Class clazz = classMap.get(className);
        MockClass mockClass = new MockClass(
            clazz.getPackage() != null ? clazz.getPackage().getName() : "", clazz.getName()
        );
        mockClass.setSimpleName(clazz.getSimpleName());
        mockClass.setInterface(clazz.isInterface());
        mockClass.setEnum(clazz.isEnum());
        mockClass.setAnnotation(clazz.isAnnotation());
        mockClass.setArray(clazz.isArray());
        mockClass.setPrimitive(clazz.isPrimitive());
        mockClass.setModifiers(clazz.getModifiers());
        
        mockClass.setFields(getFieldInfo(clazz));
        
        mockClass.setMethods(getMethodInfo(clazz));
        
        return mockClass;
    }


    public MockClass getClassInfoWithMockComparison(String className, MockClass existingMockClass) {
        MockClass latestClassInfo = getClassInfo(className);
        if (latestClassInfo == null) {
            return existingMockClass;
        }

        if (existingMockClass == null) {
            return latestClassInfo;
        }
        
        if (latestClassInfo.getFields() != null && existingMockClass.getFields() != null) {
            Map<String, MockField> existingFieldMap = existingMockClass.getFields().stream()
                    .collect(Collectors.toMap(MockField::getFieldName, field -> field));
            
            for (MockField latestField : latestClassInfo.getFields()) {
                MockField existingField = existingFieldMap.get(latestField.getFieldName());
                if (existingField != null) {
                    latestField.setMockFieldValue(existingField.getMockFieldValue());
                    latestField.setOrgFieldValue(existingField.getOrgFieldValue());
                    latestField.setActive(existingField.isActive());
                    latestField.setActiveTemplate(existingField.getActiveTemplate());
                    latestField.setFieldClassName(existingField.getFieldClassName());
                    latestField.setPrimitive(existingField.isPrimitive());
                }
            }
        }
        
        if (latestClassInfo.getMethods() != null && existingMockClass.getMethods() != null) {
            Map<String, MockMethod> existingMethodMap = existingMockClass.getMethods().stream()
                    .collect(Collectors.toMap(MockMethod::getMethodName, method -> method));
            
            for (MockMethod latestMethod : latestClassInfo.getMethods()) {
                MockMethod existingMethod = existingMethodMap.get(latestMethod.getMethodName());
                if (existingMethod != null) {
                    latestMethod.setReturnObject(existingMethod.getReturnObject());
                    latestMethod.setActive(existingMethod.isActive());
                    latestMethod.setReturnClassName(existingMethod.getReturnClassName());
                    latestMethod.setActiveReturnTemplateName(existingMethod.getActiveReturnTemplateName());
                }
            }
        }
        
        latestClassInfo.setTemplateName(existingMockClass.getTemplateName());

        return latestClassInfo;
    }
    

    private List<MockField> getFieldInfo(Class clazz) {
        List<MockField> fields = new ArrayList<>();
        try {
            java.lang.reflect.Field[] declaredFields = clazz.getDeclaredFields();
            for (java.lang.reflect.Field field : declaredFields) {
                MockField mockField = new MockField();
                mockField.setFieldName(field.getName());
                mockField.setFieldType(field.getType().getSimpleName());
                mockField.setOrgFieldValue(null);
                mockField.setMockFieldValue(null);
                mockField.setActive(false);
                
                mockField.setFieldClassName(field.getType().getName());
                mockField.setPrimitive(!field.getType().isPrimitive() && !field.getType().getName().startsWith("java.lang."));
                mockField.setActiveTemplate(null); // 默认模板为空
                
                fields.add(mockField);
            }
        } catch (Exception e) {
            System.err.println("Failed to get field info for class: " + clazz.getName());
        }
        return fields;
    }
    

    private List<MockMethod> getMethodInfo(Class clazz) {
        List<MockMethod> methods = new ArrayList<>();
        try {
            java.lang.reflect.Method[] declaredMethods = clazz.getDeclaredMethods();
            for (java.lang.reflect.Method method : declaredMethods) {
                MockMethod mockMethod = new MockMethod();
                mockMethod.setMethodName(method.getName());
                mockMethod.setReturnType(method.getReturnType().getName());
                mockMethod.setReturnObject(null);
                mockMethod.setActive(false);
                
                mockMethod.setReturnClassName(method.getReturnType().getName());
                mockMethod.setActiveReturnTemplateName(null); // 默认模板名为空
                
                List<String> parameterTypes = new ArrayList<>();
                Class<?>[] paramTypes = method.getParameterTypes();
                for (Class<?> paramType : paramTypes) {
                    parameterTypes.add(paramType.getSimpleName());
                }
                mockMethod.setParameterTypes(parameterTypes);
                
                methods.add(mockMethod);
            }
        } catch (Exception e) {
            System.err.println("Failed to get method info for class: " + clazz.getName());
        }
        return methods;
    }
    

    public ClassStatistics getClassStatistics() {
        Map<String, Class> classMap = AgentBootstrap.classMap;
        if (classMap == null || classMap.isEmpty()) {
            return new ClassStatistics(0, 0, 0, 0, 0, 0);
        }
        
        int totalClasses = classMap.size();
        int interfaces = 0;
        int enums = 0;
        int annotations = 0;
        int arrays = 0;
        int primitives = 0;
        
        for (Class clazz : classMap.values()) {
            if (clazz.isInterface()) interfaces++;
            if (clazz.isEnum()) enums++;
            if (clazz.isAnnotation()) annotations++;
            if (clazz.isArray()) arrays++;
            if (clazz.isPrimitive()) primitives++;
        }
        
        return new ClassStatistics(totalClasses, interfaces, enums, annotations, arrays, primitives);
    }
    

    public static class ClassStatistics {
        private int totalClasses;
        private int interfaces;
        private int enums;
        private int annotations;
        private int arrays;
        private int primitives;
        
        public ClassStatistics(int totalClasses, int interfaces, int enums, int annotations, int arrays, int primitives) {
            this.totalClasses = totalClasses;
            this.interfaces = interfaces;
            this.enums = enums;
            this.annotations = annotations;
            this.arrays = arrays;
            this.primitives = primitives;
        }
        
        public int getTotalClasses() { return totalClasses; }
        public int getInterfaces() { return interfaces; }
        public int getEnums() { return enums; }
        public int getAnnotations() { return annotations; }
        public int getArrays() { return arrays; }
        public int getPrimitives() { return primitives; }
    }
}
