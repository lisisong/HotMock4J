package com.hotmock4j.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MockPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private MockProject project;
    private String planName;

    private String jsonFileName;
    private Date creationDate;

    private List<MockClass> mockClassList;
    private boolean isActive;

    public MockPlan(MockProject project, String planName) {
        this.project = project;
        this.planName = planName;
    }

    public MockPlan(String planName) {
        this.planName = planName;
    }

    public MockPlan() {
    }

    @JsonCreator
    public MockPlan(@JsonProperty("project") MockProject project,
                   @JsonProperty("planName") String planName,
                   @JsonProperty("jsonFileName") String jsonFileName,
                   @JsonProperty("creationDate") Date creationDate,
                   @JsonProperty("mockClassList") List<MockClass> mockClassList,
                   @JsonProperty("active") boolean isActive) {
        this.project = project;
        this.planName = planName;
        this.jsonFileName = jsonFileName;
        this.creationDate = creationDate;
        this.mockClassList = mockClassList;
        this.isActive = isActive;
    }

    public MockProject getProject() {
        return project;
    }

    public void setProject(MockProject project) {
        this.project = project;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public List<MockClass> getMockClassList() {
        if (mockClassList == null) {
            mockClassList = new ArrayList<>();
        }
        return mockClassList;
    }

    public void setMockClassList(List<MockClass> mockClassList) {
        this.mockClassList = mockClassList;
    }


    public String getJsonFileName() {
        return jsonFileName;
    }

    public void setJsonFileName(String jsonFileName) {
        this.jsonFileName = jsonFileName;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void putMockClass(MockClass mockClass) {
        if (mockClassList == null) {
            mockClassList = new ArrayList<>();
        }
        mockClassList.add(mockClass);
    }
}
