package com.hotmock4j.core;

import java.io.Serializable;

public class MockField implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fieldName;
    private String fieldType;
    private Object orgFieldValue;
    private Object mockFieldValue;
    private boolean isActive;
    private String activeTemplate;
    private String fieldClassName;
    private boolean isPrimitive;

    public MockField() {
    }

    public MockField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public Object getOrgFieldValue() {
        return orgFieldValue;
    }

    public void setOrgFieldValue(Object orgFieldValue) {
        this.orgFieldValue = orgFieldValue;
    }

    public Object getMockFieldValue() {
        return mockFieldValue;
    }

    public void setMockFieldValue(Object mockFieldValue) {
        this.mockFieldValue = mockFieldValue;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getActiveTemplate() {
        return activeTemplate;
    }

    public void setActiveTemplate(String activeTemplate) {
        this.activeTemplate = activeTemplate;
    }

    public String getFieldClassName() {
        return fieldClassName;
    }

    public void setFieldClassName(String fieldClassName) {
        this.fieldClassName = fieldClassName;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public void setPrimitive(boolean primitive) {
        isPrimitive = primitive;
    }
}
