package com.hotmock4j.http.handlers;

import com.hotmock4j.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ClassSearchHandler implements HttpHandler {
    
    private final ClassSearchService classSearchService;
    private final MockPlanManager mockPlanManager;
    private final ObjectMapper objectMapper;
    private final ResponseHelper responseHelper;
    
    public ClassSearchHandler() {
        this.classSearchService = ClassSearchService.getInstance();
        this.mockPlanManager = MockPlanManager.getInstance();
        this.objectMapper = new ObjectMapper();
        this.responseHelper = new ResponseHelper();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        try {
            if ("GET".equals(method)) {
                handleGetRequest(exchange, path);
            } else {
                responseHelper.sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            responseHelper.sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    

    private void handleGetRequest(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/classes")) {
            handleClassSearch(exchange);
        } else if (path.startsWith("/api/classes/statistics")) {
            handleClassStatistics(exchange);
        } else if (path.startsWith("/api/classes/")) {
            handleClassInfo(exchange, path);
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Not found");
        }
    }
    

    private void handleClassSearch(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String keyword = extractKeywordFromQuery(query);
        
        List<String> classNames = classSearchService.searchClasses(keyword);
        
        Map<String, Object> response = new HashMap<>();
        response.put("classes", classNames);
        response.put("total", classNames.size());
        response.put("keyword", keyword);
        
        String jsonResponse = objectMapper.writeValueAsString(response);
        responseHelper.sendResponse(exchange, 200, jsonResponse, "application/json");
    }
    

    private void handleClassStatistics(HttpExchange exchange) throws IOException {
        ClassSearchService.ClassStatistics statistics = classSearchService.getClassStatistics();
        String jsonResponse = objectMapper.writeValueAsString(statistics);
        responseHelper.sendResponse(exchange, 200, jsonResponse, "application/json");
    }
    

    private void handleClassInfo(HttpExchange exchange, String path) throws IOException {
        String originalClassName = path.substring("/api/classes/".length());
        originalClassName = URLDecoder.decode(originalClassName, StandardCharsets.UTF_8.name());
        
        if (path.endsWith("/with-mock")) {
            handleClassInfoWithMock(exchange, originalClassName);
        } else {
            handleClassInfoBasic(exchange, originalClassName);
        }
    }
    

    private void handleClassInfoBasic(HttpExchange exchange, String className) throws IOException {
        MockClass classInfo = classSearchService.getClassInfo(className);
        if (classInfo != null) {
            String jsonResponse = objectMapper.writeValueAsString(classInfo);
            responseHelper.sendResponse(exchange, 200, jsonResponse, "application/json");
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Class not found: " + className);
        }
    }
    

    private void handleClassInfoWithMock(HttpExchange exchange, String originalClassName) throws IOException {
        String className = originalClassName.substring(0, originalClassName.length() - "/with-mock".length());
        String query = exchange.getRequestURI().getQuery();
        
        Map<String, String> queryParams = extractQueryParams(query);
        String projectName = queryParams.get("projectName");
        String planName = queryParams.get("planName");
        
        MockClass existingMockClass = getExistingMockClass(projectName, planName, className);
        
        MockClass classInfo = classSearchService.getClassInfoWithMockComparison(className, existingMockClass);
        if (classInfo != null) {
            String jsonResponse = objectMapper.writeValueAsString(classInfo);
            responseHelper.sendResponse(exchange, 200, jsonResponse, "application/json");
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Class not found: " + className);
        }
    }
    

    private String extractKeywordFromQuery(String query) throws IOException {
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "keyword".equals(keyValue[0])) {
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                }
            }
        }
        return null;
    }
    

    private Map<String, String> extractQueryParams(String query) throws IOException {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            String[] paramPairs = query.split("&");
            for (String param : paramPairs) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                }
            }
        }
        return params;
    }
    

    private MockClass getExistingMockClass(String projectName, String planName, String className) {
        if (projectName != null && planName != null) {
            MockPlan mockPlan = mockPlanManager.getMockPlan(projectName, planName);
            if (mockPlan != null && mockPlan.getMockClassList() != null) {
                return mockPlan.getMockClassList().stream()
                        .filter(mc -> mc.getClassName().equals(className))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }
}
