package com.hotmock4j.http.handlers;

import com.hotmock4j.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MockPlanHandler implements HttpHandler {
    
    private final MockPlanManager mockPlanManager;
    private final ObjectMapper objectMapper;
    private final ResponseHelper responseHelper;
    
    public MockPlanHandler() {
        this.mockPlanManager = MockPlanManager.getInstance();
        this.objectMapper = new ObjectMapper();
        this.responseHelper = new ResponseHelper();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        try {
            switch (method) {
                case "GET":
                    handleGetRequest(exchange, path);
                    break;
                case "POST":
                    handlePostRequest(exchange, path);
                    break;
                case "PUT":
                    handlePutRequest(exchange, path);
                    break;
                case "DELETE":
                    handleDeleteRequest(exchange, path);
                    break;
                default:
                    responseHelper.sendErrorResponse(exchange, 405, "Method not allowed");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            responseHelper.sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
    

    private void handleGetRequest(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/mockplans")) {
            String response = objectMapper.writeValueAsString(mockPlanManager.getAllMockPlans());
            responseHelper.sendResponse(exchange, 200, response, "application/json");
        } else if (path.startsWith("/api/mockplans/active")) {
            MockPlan activePlan = mockPlanManager.getActiveMockPlan();
            String response = objectMapper.writeValueAsString(activePlan);
            responseHelper.sendResponse(exchange, 200, response, "application/json");
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Not found");
        }
    }
    

    private void handlePostRequest(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/mockplans")) {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> requestData = objectMapper.readValue(requestBody, Map.class);
            
            String projectName = requestData.get("projectName");
            String planName = requestData.get("planName");
            
            if (projectName == null || planName == null) {
                responseHelper.sendErrorResponse(exchange, 400, "Missing required parameters: projectName and planName");
                return;
            }
            
            MockProject project = new MockProject(projectName);
            MockPlan mockPlan = mockPlanManager.createMockPlan(planName, project);
            String response = objectMapper.writeValueAsString(mockPlan);
            responseHelper.sendResponse(exchange, 201, response, "application/json");
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Not found");
        }
    }
    

    private void handlePutRequest(HttpExchange exchange, String path) throws IOException {
        if (path.startsWith("/api/mockplans/activate")) {
            handleActivateMockPlan(exchange);
        } else if (path.startsWith("/api/mockplans/class")) {
            handleUpdateMockClass(exchange);
        } else {
            responseHelper.sendErrorResponse(exchange, 404, "Not found");
        }
    }
    

    private void handleActivateMockPlan(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> requestData = objectMapper.readValue(requestBody, Map.class);
        
        String projectName = requestData.get("projectName");
        String planName = requestData.get("planName");
        
        if (projectName == null || planName == null) {
            responseHelper.sendErrorResponse(exchange, 400, "Missing required parameters: projectName and planName");
            return;
        }
        
        MockProject project = new MockProject(projectName);
        boolean activated = mockPlanManager.activateMockPlan(project, planName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", activated);
        response.put("message", activated ? "MockPlan activated successfully" : "MockPlan not found");
        
        responseHelper.sendResponse(exchange, activated ? 200 : 404, 
            objectMapper.writeValueAsString(response), "application/json");
    }
    

    private void handleUpdateMockClass(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> requestData = objectMapper.readValue(requestBody, Map.class);
        
        String projectName = (String) requestData.get("projectName");
        String planName = (String) requestData.get("planName");
        Map<String, Object> mockClassData = (Map<String, Object>) requestData.get("mockClass");
        
        if (projectName == null || planName == null || mockClassData == null) {
            responseHelper.sendErrorResponse(exchange, 400, "Missing required parameters: projectName, planName, and mockClass");
            return;
        }
        
        try {
            MockClass mockClass = convertAndFilterMockClass(mockClassData);
            
            boolean updated = mockPlanManager.updateMockClass(projectName, planName, mockClass);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", updated);
            response.put("message", updated ? "MockClass updated successfully" : "MockClass not found");
            
            responseHelper.sendResponse(exchange, updated ? 200 : 404, 
                objectMapper.writeValueAsString(response), "application/json");
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            responseHelper.sendResponse(exchange, 400, 
                objectMapper.writeValueAsString(response), "application/json");
        }
    }
    

    private MockClass convertAndFilterMockClass(Map<String, Object> mockClassData) {
        MockClass mockClass = new MockClass();
        mockClass.setClassName((String) mockClassData.get("className"));
        mockClass.setClassPackage((String) mockClassData.get("classPackage"));
        
        mockClass.setTemplateName((String) mockClassData.get("templateName"));
        
        Object activeValue = mockClassData.get("isActive");
        if (activeValue == null) {
            activeValue = mockClassData.get("active");
        }
        if (activeValue != null) {
            mockClass.setActive(Boolean.TRUE.equals(activeValue));
        }
        
        if (mockClassData.get("fields") instanceof List) {
            List<Map<String, Object>> fieldsData = (List<Map<String, Object>>) mockClassData.get("fields");
            for (Map<String, Object> fieldData : fieldsData) {
                MockField field = new MockField();
                field.setFieldName((String) fieldData.get("fieldName"));
                field.setFieldType((String) fieldData.get("fieldType"));
                field.setMockFieldValue(fieldData.get("mockFieldValue"));
                if (fieldData.containsKey("activeTemplate")) {
                    field.setActiveTemplate((String) fieldData.get("activeTemplate"));
                }
                if (fieldData.containsKey("fieldClassName")) {
                    field.setFieldClassName((String) fieldData.get("fieldClassName"));
                }
                if (fieldData.containsKey("primitive")) {
                    Object pv = fieldData.get("primitive");
                    if (pv instanceof Boolean) {
                        field.setPrimitive((Boolean) pv);
                    }
                }
                
                Object fieldActiveValue = fieldData.get("isActive");
                if (fieldActiveValue == null) {
                    fieldActiveValue = fieldData.get("active");
                }
                if (fieldActiveValue != null) {
                    field.setActive(Boolean.TRUE.equals(fieldActiveValue));
                }
                mockClass.getFields().add(field);
            }
        }
        
        if (mockClassData.get("methods") instanceof List) {
            List<Map<String, Object>> methodsData = (List<Map<String, Object>>) mockClassData.get("methods");
            for (Map<String, Object> methodData : methodsData) {
                MockMethod method = new MockMethod();
                method.setMethodName((String) methodData.get("methodName"));
                method.setReturnObject(methodData.get("returnObject"));
                if (methodData.containsKey("activeReturnTemplateName")) {
                    method.setActiveReturnTemplateName((String) methodData.get("activeReturnTemplateName"));
                }
                if (methodData.containsKey("returnClassName")) {
                    method.setReturnClassName((String) methodData.get("returnClassName"));
                }
                
                Object methodActiveValue = methodData.get("isActive");
                if (methodActiveValue == null) {
                    methodActiveValue = methodData.get("active");
                }
                if (methodActiveValue != null) {
                    method.setActive(Boolean.TRUE.equals(methodActiveValue));
                }
                
                boolean hasReturnMock = method.getReturnObject() != null &&
                    !method.getReturnObject().toString().trim().isEmpty();
                boolean hasReturnTemplate = method.getActiveReturnTemplateName() != null && 
                    !method.getActiveReturnTemplateName().trim().isEmpty();
                if ((hasReturnMock || hasReturnTemplate) && method.isActive()) {
                    mockClass.getMethods().add(method);
                }
            }
        }
        
        return mockClass;
    }
    

    private void handleDeleteRequest(HttpExchange exchange, String path) throws IOException {
        if (!path.startsWith("/api/mockplans")) {
            responseHelper.sendErrorResponse(exchange, 404, "Not found");
            return;
        }

        // Read body (may be empty for some clients). We'll also fallback to query params.
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> requestData = new HashMap<>();
        if (requestBody != null && !requestBody.trim().isEmpty()) {
            try {
                requestData = objectMapper.readValue(requestBody, Map.class);
            } catch (Exception ignore) {
                // ignore malformed body and fallback to query parsing
            }
        }

        // Fallback to query string params when body is empty
        if (requestData.isEmpty() && exchange.getRequestURI().getQuery() != null) {
            String[] pairs = exchange.getRequestURI().getQuery().split("&");
            for (String p : pairs) {
                int idx = p.indexOf('=');
                if (idx > 0) {
                    String k = java.net.URLDecoder.decode(p.substring(0, idx), StandardCharsets.UTF_8);
                    String v = java.net.URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
                    requestData.put(k, v);
                }
            }
        }

        if (path.startsWith("/api/mockplans/class")) {
            String projectName = requestData.get("projectName");
            String planName = requestData.get("planName");
            String className = requestData.get("className");

            if (projectName == null || planName == null || className == null) {
                responseHelper.sendErrorResponse(exchange, 400, "Missing required parameters: projectName, planName and className");
                return;
            }

            boolean deleted = mockPlanManager.deleteMockClass(projectName, planName, className);
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "MockClass deleted successfully" : "MockClass not found");
            responseHelper.sendResponse(exchange, deleted ? 200 : 404,
                    objectMapper.writeValueAsString(response), "application/json");
            return;
        }

        // Default: delete plan
        String projectName = requestData.get("projectName");
        String planName = requestData.get("planName");

        if (projectName == null || planName == null) {
            responseHelper.sendErrorResponse(exchange, 400, "Missing required parameters: projectName and planName");
            return;
        }

        MockProject project = new MockProject(projectName);
        boolean deleted = mockPlanManager.deleteMockPlan(project, planName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", deleted);
        response.put("message", deleted ? "MockPlan deleted successfully" : "MockPlan not found");

        responseHelper.sendResponse(exchange, deleted ? 200 : 404,
                objectMapper.writeValueAsString(response), "application/json");
    }
}
