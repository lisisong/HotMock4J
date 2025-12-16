package com.hotmock4j.http;

import com.hotmock4j.http.handlers.StaticResourceHandler;
import com.hotmock4j.http.handlers.MockPlanHandler;
import com.hotmock4j.http.handlers.ClassSearchHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;


public class AgentHttpServer {


    public static void startHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            
            configureHandlers(server);
            
            server.start();
            System.out.println("Agent server started on http://localhost:8080");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void configureHandlers(HttpServer server) {
        server.createContext("/", new StaticResourceHandler());
        
        server.createContext("/api/mockplans", new MockPlanHandler());
        
        server.createContext("/api/classes", new ClassSearchHandler());
    }
}
