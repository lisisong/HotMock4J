package com.hotmock4j.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class MockProject implements Serializable {

    public MockProject(String name) {
        this.projectName = name;
    }

    public MockProject() {
    }

    @JsonCreator
    public MockProject(@JsonProperty("projectName") String projectName,
                      @JsonProperty("mockPlan") List<MockPlan> mockPlan) {
        this.projectName = projectName;
        this.mockPlan = mockPlan;
    }

    private String projectName;
    List<MockPlan> mockPlan;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<MockPlan> getMockPlan() {
        return mockPlan;
    }

    public void setMockPlan(List<MockPlan> mockPlan) {
        this.mockPlan = mockPlan;
    }

    @Override
    public String toString() {
        return projectName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MockProject that = (MockProject) o;
        return Objects.equals(projectName, that.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectName);
    }
}
