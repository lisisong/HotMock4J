package com.hotmock4j.agent.transformer;

import com.hotmock4j.core.InstanceTracker;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Instance registration transformer for registering instances in constructors
 */
public class InstanceRegistrationTransformer implements ClassFileTransformer {
    
    private final InstanceTracker instanceTracker;
    
    public InstanceRegistrationTransformer() {
        this.instanceTracker = InstanceTracker.getInstance();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, 
                           ProtectionDomain protectionDomain, byte[] classFileBuffer) 
                           throws IllegalClassFormatException {
        
        // Convert internal class name to standard class name
        String standardClassName = className.replace('/', '.');
        
        // Check if there are mock configurations for this class
        com.hotmock4j.core.MockPlanManager mockPlanManager = com.hotmock4j.core.MockPlanManager.getInstance();
        com.hotmock4j.core.MockPlan activePlan = mockPlanManager.getActiveMockPlan();
        if (activePlan == null) {
            return null;
        }
        
        // Check if there are mock configurations for this class
        boolean hasMockConfig = activePlan.getMockClassList().stream()
                .anyMatch(mc -> mc.getClassName().equals(standardClassName));
        
        if (!hasMockConfig) {
            return null;
        }
        
        // If there are mock configurations, perform bytecode enhancement
        ClassReader classReader = new ClassReader(classFileBuffer);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                           String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                
                // Register instance in constructor
                if ("<init>".equals(name)) {
                    return new InstanceRegistrationVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor, standardClassName);
                }
                
                return methodVisitor;
            }
        };
        
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }
    
    /**
     * Instance registration visitor for registering instances in constructors
     */
    private class InstanceRegistrationVisitor extends MethodVisitor {
        private final String className;
        
        public InstanceRegistrationVisitor(int api, MethodVisitor methodVisitor, int access, 
                                         String name, String descriptor, String className) {
            super(api, methodVisitor);
            this.className = className;
        }
        
        @Override
        public void visitInsn(int opcode) {
            // Register instance before constructor returns
            if (opcode == Opcodes.RETURN) {
                // Register instance before constructor returns
                // Push this reference to stack top (this is fully initialized at this point)
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                
                // Call InstanceTracker.registerInstance method
                mv.visitLdcInsn(className); // Class name
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this reference
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                                 Type.getInternalName(InstanceTracker.class),
                                 "registerInstance",
                                 "(Ljava/lang/String;Ljava/lang/Object;)V",
                                 false);
                
                System.out.println("Added instance registration for class: " + className);
            }
            super.visitInsn(opcode);
        }
    }
}
