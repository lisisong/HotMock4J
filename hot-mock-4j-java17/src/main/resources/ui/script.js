// Main Application Entry Point
import APIManager from './modules/api-manager.js';
import UIManager from './modules/ui-manager.js';
import ClassEditor from './modules/class-editor.js';

// MockPlan API Manager Class - main controller
class MockPlanAPIManager {
    constructor() {
        this.apiManager = new APIManager('http://localhost:8080/api');
        this.uiManager = new UIManager();
        this.classEditor = new ClassEditor(this.apiManager, this.uiManager);
        
        this.currentMockPlan = null;
        this.currentSelectedClass = null;
        
        this.init();
    }

    init() {
        this.loadMockPlans();
        this.bindEvents();
    }

    // Load MockPlans
    async loadMockPlans() {
        try {
            const mockPlans = await this.apiManager.getMockPlans();
            this.uiManager.renderMockPlans(
                mockPlans,
                this.currentMockPlan,
                (mockplan) => this.selectMockPlan(mockplan),
                (projectName, planName) => this.activateMockPlan(projectName, planName),
                (projectName, planName) => this.deleteMockPlan(projectName, planName)
            );
            return mockPlans;
        } catch (error) {
            console.error('Failed to load mock plans:', error);
            this.uiManager.showMessage('Failed to load MockPlans: ' + error.message, 'error');
            return [];
        }
    }

    // Select a MockPlan
    selectMockPlan(mockplan) {
        this.currentMockPlan = mockplan;
        this.showMockPlanDetails();
    }

    // Display MockPlan details
    showMockPlanDetails() {
        this.uiManager.showMockPlanDetails(
            this.currentMockPlan,
            () => this.loadMockPlans(),
            () => this.showClassSearchModal(),
            (className) => this.showClassEditModal(className),
            (className) => this.deleteMockClass(className)
        );
    }

    // Show class search modal
    showClassSearchModal() {
        this.uiManager.showClassSearchModal();
    }

    // Search classes
    async searchClasses(keyword) {
        try {
            const classes = await this.apiManager.searchClasses(keyword);
            this.uiManager.renderSearchResults(classes, (className) => this.showClassDetails(className));
            return classes;
        } catch (error) {
            console.error('Failed to search classes:', error);
            this.uiManager.showMessage('Failed to search classes: ' + error.message, 'error');
            return [];
        }
    }

    // Show class details
    async showClassDetails(className) {
        try {
            const classInfo = await this.apiManager.getClassInfo(className);
            if (classInfo) {
                this.uiManager.showClassDetails(classInfo);
            }
        } catch (error) {
            console.error('Failed to get class info:', error);
            this.uiManager.showMessage('Failed to get class information: ' + error.message, 'error');
        }
    }

    // Add class to MockPlan
    async addClassToMockPlan() {
        if (!this.uiManager.currentSelectedClass || !this.currentMockPlan) {
            this.uiManager.showMessage('Please select a class first', 'error');
            return;
        }

        try {
            const templateNameInput = document.getElementById('classTemplateName');
            const templateName = templateNameInput ? templateNameInput.value.trim() : '';
            if (!templateName) {
                this.uiManager.showMessage('Template name is required', 'error');
                if (templateNameInput) templateNameInput.focus();
                return;
            }

            const mockClass = {
                className: this.uiManager.currentSelectedClass.className,
                classPackage: this.uiManager.currentSelectedClass.classPackage,
                templateName: templateName,
                fields: [],
                methods: []
            };

            const result = await this.apiManager.updateMockClass(
                this.currentMockPlan.project.projectName,
                this.currentMockPlan.planName,
                mockClass
            );

            if (result.success) {
                this.uiManager.showMessage(`Class ${this.uiManager.currentSelectedClass.className} has been added to MockPlan with template '${templateName}'`, 'success');
                this.uiManager.hideClassSearchModal();
                
                // Refresh MockPlan details to ensure we have the latest data
                const allMockPlans = await this.loadMockPlans();
                const refreshedMockPlan = allMockPlans.find(p => 
                    p.planName === this.currentMockPlan.planName && 
                    p.project.projectName === this.currentMockPlan.project.projectName
                );
                if (refreshedMockPlan) {
                    this.currentMockPlan = refreshedMockPlan;
                }
                this.showMockPlanDetails();
            } else {
                this.uiManager.showMessage('Failed to add class: ' + result.message, 'error');
            }
        } catch (error) {
            console.error('Failed to add class to mock plan:', error);
            this.uiManager.showMessage('Failed to add class: ' + error.message, 'error');
        }
    }

    // Create MockPlan
    async createMockPlan(projectName, planName) {
        try {
            const newMockPlan = await this.apiManager.createMockPlan(projectName, planName);
            this.uiManager.showMessage('MockPlan created successfully', 'success');
            await this.loadMockPlans();
            this.selectMockPlan(newMockPlan);
            return newMockPlan;
        } catch (error) {
            console.error('Failed to create mock plan:', error);
            this.uiManager.showMessage('Failed to create Mock Plan: ' + error.message, 'error');
        }
    }

    // Activate MockPlan
    async activateMockPlan(projectName, planName) {
        try {
            const result = await this.apiManager.activateMockPlan(projectName, planName);
            if (result.success) {
                this.uiManager.showMessage('MockPlan activated successfully', 'success');
                await this.loadMockPlans();
            } else {
                this.uiManager.showMessage('Failed to activate MockPlan: ' + result.message, 'error');
            }
        } catch (error) {
            console.error('Failed to activate mock plan:', error);
            this.uiManager.showMessage('Failed to activate MockPlan: ' + error.message, 'error');
        }
    }

    // Delete MockPlan
    async deleteMockPlan(projectName, planName) {
        if (!confirm(`Are you sure you want to delete MockPlan "${planName}"?`)) {
            return;
        }

        try {
            const result = await this.apiManager.deleteMockPlan(projectName, planName);
            if (result.success) {
                this.uiManager.showMessage('MockPlan deleted successfully', 'success');
                if (this.currentMockPlan && this.currentMockPlan.planName === planName) {
                    this.currentMockPlan = null;
                    this.showMockPlanDetails();
                }
                await this.loadMockPlans();
            } else {
                this.uiManager.showMessage('Failed to delete MockPlan: ' + result.message, 'error');
            }
        } catch (error) {
            console.error('Failed to delete mock plan:', error);
            this.uiManager.showMessage('Failed to delete MockPlan: ' + error.message, 'error');
        }
    }

    // Delete Mock Class
    async deleteMockClass(className) {
        if (!this.currentMockPlan || !className) return;
        if (!confirm(`Are you sure you want to delete Mock Class "${className}"?`)) {
            return;
        }

        try {
            const result = await this.apiManager.deleteMockClass(
                this.currentMockPlan.project.projectName,
                this.currentMockPlan.planName,
                className
            );
            if (result && result.success) {
                this.uiManager.showMessage('Mock Class deleted successfully', 'success');
                // Refresh MockPlan list and details
                const all = await this.loadMockPlans();
                const refreshed = all.find(p => p.planName === this.currentMockPlan.planName && p.project.projectName === this.currentMockPlan.project.projectName);
                if (refreshed) {
                    this.currentMockPlan = refreshed;
                }
                this.showMockPlanDetails();
            } else {
                this.uiManager.showMessage('Deletion failed' + (result && result.message ? ': ' + result.message : ''), 'error');
            }
        } catch (error) {
            console.error('Failed to delete mock class:', error);
            this.uiManager.showMessage('Deletion failed: ' + error.message, 'error');
        }
    }

    // Show class edit modal
    async showClassEditModal(className) {
        await this.classEditor.showClassEditModal(className, this.currentMockPlan);
    }

    // Bind events
    bindEvents() {
        // New button event
        document.getElementById('newMockPlan').addEventListener('click', () => {
            this.uiManager.showModal();
        });

        // Modal close events
        document.querySelectorAll('.close').forEach(closeBtn => {
            closeBtn.addEventListener('click', () => {
                this.uiManager.hideModal();
                this.uiManager.hideClassSearchModal();
            });
        });

        document.querySelectorAll('.cancel-btn').forEach(cancelBtn => {
            cancelBtn.addEventListener('click', () => {
                this.uiManager.hideModal();
                this.uiManager.hideClassSearchModal();
            });
        });

        // Click outside modal to close
        window.addEventListener('click', (e) => {
            const modal = document.getElementById('newMockPlanModal');
            const classModal = document.getElementById('classSearchModal');
            if (e.target === modal) {
                this.uiManager.hideModal();
            }
            if (e.target === classModal) {
                this.uiManager.hideClassSearchModal();
            }
        });

        // Form submit event
        document.getElementById('newMockPlanForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleFormSubmit();
        });

        // Class search input event
        document.getElementById('classSearchInput').addEventListener('input', async (e) => {
            const keyword = e.target.value.trim();
            if (keyword.length >= 2) {
                await this.searchClasses(keyword);
            } else if (keyword.length === 0) {
                document.getElementById('classSearchResults').innerHTML = '<div class="empty-state">Enter a keyword to search for classes</div>';
                document.getElementById('classDetails').style.display = 'none';
                document.getElementById('confirmAddClass').disabled = true;
            }
        });

        // Confirm add class button event
        document.getElementById('confirmAddClass').addEventListener('click', () => {
            this.addClassToMockPlan();
        });

        // Listen for Mock Class updates and automatically refresh the UI
        document.addEventListener('mockClassUpdated', async () => {
            await this.refreshCurrentView();
        });
    }

    /**
     * Refresh current view
     */
    async refreshCurrentView() {
        if (this.currentMockPlan) {
            // Refresh MockPlan list and details
            await this.loadMockPlans();
            this.showMockPlanDetails();
        }
    }

    // Handle form submission
    async handleFormSubmit() {
        const projectName = document.getElementById('projectName').value.trim();
        const planName = document.getElementById('mockplanName').value.trim();

        if (!projectName || !planName) {
            this.uiManager.showMessage('Please enter the project name and MockPlan name', 'error');
            return;
        }

        await this.createMockPlan(projectName, planName);
        this.uiManager.hideModal();
    }
}

// App startup
document.addEventListener('DOMContentLoaded', () => {
    new MockPlanAPIManager();
});
