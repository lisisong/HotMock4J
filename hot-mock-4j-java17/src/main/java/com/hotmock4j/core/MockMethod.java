package com.hotmock4j.core;

import java.io.Serializable;
import java.util.List;

public class MockMethod implements Serializable {

    private static final long serialVersionUID = 1L;

    private Object returnObject;
    private String methodName;
    private String returnType;
    private List<Object> parameters;
    private List<String> parameterTypes;
    private MethodSpy methodSpy;
    private MethodStub methodStub;
    private boolean isActive;
    private String returnClassName;
    private String activeReturnTemplateName;

    public Object getReturnObject() {
        return returnObject;
    }

    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public MethodSpy getMethodSpy() {
        return methodSpy;
    }

    public void setMethodSpy(MethodSpy methodSpy) {
        this.methodSpy = methodSpy;
    }

    public MethodStub getMethodStub() {
        return methodStub;
    }

    public void setMethodStub(MethodStub methodStub) {
        this.methodStub = methodStub;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getReturnClassName() {
        return returnClassName;
    }

    public void setReturnClassName(String returnClassName) {
        this.returnClassName = returnClassName;
    }

    public String getActiveReturnTemplateName() {
        return activeReturnTemplateName;
    }

    public void setActiveReturnTemplateName(String activeReturnTemplateName) {
        this.activeReturnTemplateName = activeReturnTemplateName;
    }
}
