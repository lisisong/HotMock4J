package com.hotmock4j.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


public class ResponseHelper {
    
    private final ObjectMapper objectMapper;
    
    public ResponseHelper() {
        this.objectMapper = new ObjectMapper();
    }
    

    public void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        setCommonHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    

    public void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", message);
        
        String response = objectMapper.writeValueAsString(errorResponse);
        sendResponse(exchange, statusCode, response, "application/json");
    }
    

    private void setCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
