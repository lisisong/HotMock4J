package com.hotmock4j.agent.transformer;

import com.hotmock4j.core.MockPlanManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Mock field and method transformer
 * Used for dynamically modifying field values and method return values
 */
public class MockFieldTransformer implements ClassFileTransformer {
    
    private final MockPlanManager mockPlanManager;
    
    public MockFieldTransformer() {
        this.mockPlanManager = MockPlanManager.getInstance();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
                           ProtectionDomain protectionDomain, byte[] classFileBuffer) 
                           throws IllegalClassFormatException {
        
        // Convert internal class name to standard class name
        String standardClassName = className.replace('/', '.');
        
        // Check if there are mock configurations for this class
        com.hotmock4j.core.MockPlan activePlan = mockPlanManager.getActiveMockPlan();
        if (activePlan == null) {
            return null;
        }
        
        com.hotmock4j.core.MockClass mockClass = activePlan.getMockClassList().stream()
                .filter(mc -> mc.getClassName().equals(standardClassName))
                .findFirst()
                .orElse(null);
        
        if (mockClass == null) {
            return null;
        }
        
        // If there are mock configurations, perform bytecode enhancement
        ClassReader classReader = new ClassReader(classFileBuffer);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, 
                                         String signature, Object value) {
                FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);
                
                // Check if there are mock configurations for this field
                if (mockClass.getFields() != null) {
                    for (com.hotmock4j.core.MockField mockField : mockClass.getFields()) {
                        if (mockField.getFieldName().equals(name) && mockField.getMockFieldValue() != null) {
                            // Set mock value based on field type
                            Object mockValue = convertToType(mockField.getMockFieldValue().toString(), descriptor);
                            if (mockValue != null) {
                                System.out.println("Mocking field: " + standardClassName + "." + name + " = " + mockValue);
                                // Create field visitor to modify field value
                                return new MockFieldVisitor(Opcodes.ASM9, fieldVisitor, access, name, descriptor, mockValue);
                            }
                        }
                    }
                }
                return fieldVisitor;
            }
            
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                           String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                
                // Check if there are mock configurations for this method
                if (mockClass.getMethods() != null) {
                    for (com.hotmock4j.core.MockMethod mockMethod : mockClass.getMethods()) {
                        if (!mockMethod.getMethodName().equals(name)) continue;
                        // Case 1: explicit returnObject mock
                        if (mockMethod.getReturnObject() != null) {
                            return new MockMethodVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor, mockMethod);
                        }
                        // Case 2: mount template for object return types
                        if (mockMethod.getActiveReturnTemplateName() != null &&
                            !mockMethod.getActiveReturnTemplateName().trim().isEmpty()) {
                            // Only for reference return types
                            String retDesc = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                            if (retDesc.startsWith("L") || retDesc.startsWith("[")) {
                                return new MockMethodVisitor.TemplateReturnMethodVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor, mockMethod);
                            }
                        }
                    }
                }
                
                // Add field access interception for instance field mocking
                // Only add field access interception for getter methods to avoid affecting other methods
                if (mockClass.getFields() != null && !mockClass.getFields().isEmpty() && 
                    (name.startsWith("get") || name.startsWith("is"))) {
                    return new InstanceFieldMockVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor, mockClass);
                }
                
                return methodVisitor;
            }
        };
        
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
    
    /**
     * Convert string value to specified type
     */
    private Object convertToType(String value, String descriptor) {
        try {
            switch (descriptor) {
                case "Z": // boolean
                    return Boolean.parseBoolean(value);
                case "B": // byte
                    return Byte.parseByte(value);
                case "C": // char
                    return value.charAt(0);
                case "S": // short
                    return Short.parseShort(value);
                case "I": // int
                    return Integer.parseInt(value);
                case "J": // long
                    return Long.parseLong(value);
                case "F": // float
                    return Float.parseFloat(value);
                case "D": // double
                    return Double.parseDouble(value);
                case "Ljava/lang/String;": // String
                    return value;
                default:
                    // For other reference types, temporarily return null
                    return null;
            }
        } catch (Exception e) {
            System.err.println("Failed to convert mock value '" + value + "' to type " + descriptor + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Mock field visitor for modifying field values
     */
    private static class MockFieldVisitor extends FieldVisitor {
        private final Object mockValue;
        
        public MockFieldVisitor(int api, FieldVisitor fieldVisitor, int access, 
                               String name, String descriptor, Object mockValue) {
            super(api, fieldVisitor);
            this.mockValue = mockValue;
        }
        
        @Override
        public void visitEnd() {
            // Set mock value when field definition ends
            if (mockValue != null) {
                // Here we cannot directly modify existing instance field values
                // Bytecode enhancement can only modify initial field values, not runtime instance values
                // For runtime instance field value modification, other methods are needed
                super.visitEnd();
            } else {
                super.visitEnd();
            }
        }
    }
    
    /**
     * Instance field mock visitor for intercepting field access
     */
    private static class InstanceFieldMockVisitor extends MethodVisitor {
        private final com.hotmock4j.core.MockClass mockClass;
        
        public InstanceFieldMockVisitor(int api, MethodVisitor methodVisitor, int access, 
                                      String name, String descriptor, com.hotmock4j.core.MockClass mockClass) {
            super(api, methodVisitor);
            this.mockClass = mockClass;
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Check field access instruction
            if (opcode == Opcodes.GETFIELD) {
                // Check if there are mock configurations for this field
                if (mockClass.getFields() != null) {
                    for (com.hotmock4j.core.MockField mockField : mockClass.getFields()) {
                        if (mockField.getFieldName().equals(name) && mockField.getMockFieldValue() != null) {
                            // If GETFIELD, intercept and return mock value
                            System.out.println("Intercepting field get: " + owner + "." + name);
                            
                            // Remove original GETFIELD instruction
                            // GETFIELD instruction pops object reference from stack top, then pushes field value
                            // We need to pop object reference, then push mock value
                            mv.visitInsn(Opcodes.POP); // Pop object reference
                            
                            // Insert code to return mock value
                            Object mockValue = convertToType(mockField.getMockFieldValue().toString(), descriptor);
                            if (mockValue != null) {
                                // Generate corresponding load instruction based on field type
                                generateLoadMockValue(mockValue, descriptor);
                                return; // Skip original instruction
                            }
                        }
                    }
                }
            }
            // Execute original instruction
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
        
        /**
         * Generate bytecode to load mock value
         */
        private void generateLoadMockValue(Object mockValue, String descriptor) {
            switch (descriptor) {
                case "Z": // boolean
                    mv.visitLdcInsn((Boolean) mockValue);
                    break;
                case "B": // byte
                    mv.visitLdcInsn((Byte) mockValue);
                    break;
                case "C": // char
                    mv.visitLdcInsn((Character) mockValue);
                    break;
                case "S": // short
                    mv.visitLdcInsn((Short) mockValue);
                    break;
                case "I": // int
                    mv.visitLdcInsn((Integer) mockValue);
                    break;
                case "J": // long
                    mv.visitLdcInsn((Long) mockValue);
                    break;
                case "F": // float
                    mv.visitLdcInsn((Float) mockValue);
                    break;
                case "D": // double
                    mv.visitLdcInsn((Double) mockValue);
                    break;
                case "Ljava/lang/String;": // String
                    mv.visitLdcInsn((String) mockValue);
                    break;
                default:
                    // For other reference types, return null
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    break;
            }
        }
        
        /**
         * Convert string value to specified type (instance method version)
         */
        private Object convertToType(String value, String descriptor) {
            try {
                switch (descriptor) {
                    case "Z": // boolean
                        return Boolean.parseBoolean(value);
                    case "B": // byte
                        return Byte.parseByte(value);
                    case "C": // char
                        return value.charAt(0);
                    case "S": // short
                        return Short.parseShort(value);
                    case "I": // int
                        return Integer.parseInt(value);
                    case "J": // long
                        return Long.parseLong(value);
                    case "F": // float
                        return Float.parseFloat(value);
                    case "D": // double
                        return Double.parseDouble(value);
                    case "Ljava/lang/String;": // String
                        return value;
                    default:
                        // For other reference types, temporarily return null
                        return null;
                }
            } catch (Exception e) {
                System.err.println("Failed to convert mock value '" + value + "' to type " + descriptor + ": " + e.getMessage());
                return null;
            }
        }
        }

        /**
         * Mock method visitor for modifying method return values
         */
        private static class MockMethodVisitor extends MethodVisitor {
        private final String methodName;
        private final String descriptor;
        private final com.hotmock4j.core.MockMethod mockMethod;
        
        public MockMethodVisitor(int api, MethodVisitor methodVisitor, int access, 
                               String methodName, String descriptor, com.hotmock4j.core.MockMethod mockMethod) {
            super(api, methodVisitor);
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.mockMethod = mockMethod;
        }

        /**
         * Method visitor to return an instance built from mounted template for object return types.
         */
        private static class TemplateReturnMethodVisitor extends MethodVisitor {
            private final String methodName;
            private final String descriptor;
            private final com.hotmock4j.core.MockMethod mockMethod;

            public TemplateReturnMethodVisitor(int api, MethodVisitor mv, int access,
                                               String methodName, String descriptor, com.hotmock4j.core.MockMethod mockMethod) {
                super(api, mv);
                this.methodName = methodName;
                this.descriptor = descriptor;
                this.mockMethod = mockMethod;
            }

            @Override
            public void visitCode() {
                super.visitCode();
                // Build instance via MockPlanManager.buildInstanceFromActiveTemplate(className, templateName)
                String returnClassName = mockMethod.getReturnClassName();
                String tplName = mockMethod.getActiveReturnTemplateName();
                if (returnClassName == null || tplName == null || tplName.trim().isEmpty()) {
                    return; // fall back to original
                }
                System.out.println("Mocking method (template): " + methodName + " -> " + returnClassName + "#" + tplName);

                // Push arguments for static call
                mv.visitLdcInsn(returnClassName);
                mv.visitLdcInsn(tplName);
                // Call static helper: com.maple.core.MockPlanManager.buildInstanceFromActiveTemplate(String,String)Object
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/hotmock4j/core/MockPlanManager",
                        "buildInstanceFromActiveTemplate",
                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                        false);

                // Cast to expected return type and return
                org.objectweb.asm.Type retType = org.objectweb.asm.Type.getReturnType(descriptor);
                String internalName = retType.getInternalName();
                if (internalName != null) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, internalName);
                }
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // Insert code to return mock value at method start
            String returnType = descriptor.substring(descriptor.lastIndexOf(')') + 1);
            Object mockValue = mockMethod.getReturnObject();
            
            if (mockValue != null) {
                System.out.println("Mocking method: " + methodName + " returns " + mockValue);
                
                // Generate corresponding return instruction based on return type
                switch (returnType) {
                    case "Z": // boolean
                        mv.visitLdcInsn(Boolean.parseBoolean(mockValue.toString()));
                        mv.visitInsn(Opcodes.IRETURN);
                        break;
                    case "B": // byte
                        mv.visitLdcInsn(Byte.parseByte(mockValue.toString()));
                        mv.visitInsn(Opcodes.IRETURN);
                        break;
                    case "C": // char
                        mv.visitLdcInsn(mockValue.toString().charAt(0));
                        mv.visitInsn(Opcodes.IRETURN);
                        break;
                    case "S": // short
                        mv.visitLdcInsn(Short.parseShort(mockValue.toString()));
                        mv.visitInsn(Opcodes.IRETURN);
                        break;
                    case "I": // int
                        mv.visitLdcInsn(Integer.parseInt(mockValue.toString()));
                        mv.visitInsn(Opcodes.IRETURN);
                        break;
                    case "J": // long
                        mv.visitLdcInsn(Long.parseLong(mockValue.toString()));
                        mv.visitInsn(Opcodes.LRETURN);
                        break;
                    case "F": // float
                        mv.visitLdcInsn(Float.parseFloat(mockValue.toString()));
                        mv.visitInsn(Opcodes.FRETURN);
                        break;
                    case "D": // double
                        mv.visitLdcInsn(Double.parseDouble(mockValue.toString()));
                        mv.visitInsn(Opcodes.DRETURN);
                        break;
                    case "Ljava/lang/String;": // String
                        mv.visitLdcInsn(mockValue.toString());
                        mv.visitInsn(Opcodes.ARETURN);
                        break;
                    case "V": // void
                        // void method directly returns
                        mv.visitInsn(Opcodes.RETURN);
                        break;
                    default:
                        // For other reference types, return null
                        mv.visitInsn(Opcodes.ACONST_NULL);
                        mv.visitInsn(Opcodes.ARETURN);
                        break;
                }
            }
        }
    }
}
