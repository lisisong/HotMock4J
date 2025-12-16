package com.hotmock4j.core;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MockClass implements Serializable {

    private static final long serialVersionUID = 1L;


    private String classPackage;
    private String className;
    private String simpleName;
    private boolean isInterface;
    private boolean isEnum;
    private boolean isAnnotation;
    private boolean isArray;
    private boolean isPrimitive;
    private int modifiers;
    private List<MockField> fields;
    private List<MockMethod> methods;
    private boolean isActive;
    private String templateName;



    public MockClass() {
    }

    public MockClass(String classPackage, String className) {
        this.className = className;
        this.classPackage = classPackage;
    }

    public String getClassPackage() {
        return classPackage;
    }

    public void setClassPackage(String classPackage) {
        this.classPackage = classPackage;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean anInterface) {
        isInterface = anInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public void setEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    public boolean isAnnotation() {
        return isAnnotation;
    }

    public void setAnnotation(boolean annotation) {
        isAnnotation = annotation;
    }

    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean array) {
        isArray = array;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    public void setPrimitive(boolean primitive) {
        isPrimitive = primitive;
    }

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int modifiers) {
        this.modifiers = modifiers;
    }

    public List<MockField> getFields() {
        if (fields == null) {
            fields = new ArrayList<MockField>();
        }
        return fields;
    }

    public void setFields(List<MockField> fields) {
        this.fields = fields;
    }

    public List<MockMethod> getMethods() {
        if (methods == null) {
            methods = new ArrayList<>();
        }
        return methods;
    }

    public void setMethods(List<MockMethod> methods) {
        this.methods = methods;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }
}
