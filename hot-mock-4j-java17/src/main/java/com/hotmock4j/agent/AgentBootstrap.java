package com.hotmock4j.agent;

import com.hotmock4j.http.AgentHttpServer;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;


public class AgentBootstrap {

    public static Map<String, Class> classMap = new HashMap<String, Class>();
    public static Instrumentation ins;


    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        ins = instrumentation;
        System.out.println("Agent started before main application...");
        System.out.println("--------start agent");
        
        // Add MockFieldTransformer
        com.hotmock4j.agent.transformer.MockFieldTransformer mockTransformer =
            new com.hotmock4j.agent.transformer.MockFieldTransformer();
        instrumentation.addTransformer(mockTransformer, true);
        System.out.println("MockFieldTransformer registered");
        
        // Add InstanceRegistrationTransformer
        com.hotmock4j.agent.transformer.InstanceRegistrationTransformer instanceTransformer =
            new com.hotmock4j.agent.transformer.InstanceRegistrationTransformer();
        instrumentation.addTransformer(instanceTransformer, true);
        System.out.println("InstanceRegistrationTransformer registered");
        
        AgentHttpServer.startHttpServer();
    }


    public static void agentmain(String args, Instrumentation instrumentation) {
        System.out.println("--------start agent (attach)");
        ins = instrumentation;

        // Register transformers for attach mode as well
        try {
            com.hotmock4j.agent.transformer.MockFieldTransformer mockTransformer =
                new com.hotmock4j.agent.transformer.MockFieldTransformer();
            instrumentation.addTransformer(mockTransformer, true);
            System.out.println("MockFieldTransformer registered (attach)");

            com.hotmock4j.agent.transformer.InstanceRegistrationTransformer instanceTransformer =
                new com.hotmock4j.agent.transformer.InstanceRegistrationTransformer();
            instrumentation.addTransformer(instanceTransformer, true);
            System.out.println("InstanceRegistrationTransformer registered (attach)");
        } catch (Throwable t) {
            System.err.println("Failed to register transformers on attach: " + t.getMessage());
        }

        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            classMap.put(clazz.getName(), clazz);
        }

        // Start HTTP server if not already started in target JVM
        try {
            AgentHttpServer.startHttpServer();
        } catch (Throwable t) {
            System.err.println("Failed to start AgentHttpServer on attach: " + t.getMessage());
        }

        System.out.println("--------end agent (attach)");
    }




}
