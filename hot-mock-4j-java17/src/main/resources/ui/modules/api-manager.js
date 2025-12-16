class APIManager {
    constructor(baseUrl = 'http://localhost:8080/api') {
        this.baseUrl = baseUrl;
    }

    async request(url, options = {}) {
        try {
            const response = await fetch(url, {
                headers: {
                    'Content-Type': 'application/json',
                    ...options.headers
                },
                ...options
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error(`API request failed: ${url}`, error);
            throw error;
        }
    }

    async getMockPlans() {
        return this.request(`${this.baseUrl}/mockplans`);
    }

    async createMockPlan(projectName, planName) {
        return this.request(`${this.baseUrl}/mockplans`, {
            method: 'POST',
            body: JSON.stringify({ projectName, planName })
        });
    }

    async activateMockPlan(projectName, planName) {
        return this.request(`${this.baseUrl}/mockplans/activate`, {
            method: 'PUT',
            body: JSON.stringify({ projectName, planName })
        });
    }

    async deleteMockPlan(projectName, planName) {
        return this.request(`${this.baseUrl}/mockplans`, {
            method: 'DELETE',
            body: JSON.stringify({ projectName, planName })
        });
    }

    async updateMockClass(projectName, planName, mockClass) {
        return this.request(`${this.baseUrl}/mockplans/class`, {
            method: 'PUT',
            body: JSON.stringify({ projectName, planName, mockClass })
        });
    }

    async deleteMockClass(projectName, planName, className) {
        const url = `${this.baseUrl}/mockplans/class?projectName=${encodeURIComponent(projectName)}&planName=${encodeURIComponent(planName)}&className=${encodeURIComponent(className)}`;
        return this.request(url, { method: 'DELETE' });
    }

    async searchClasses(keyword = '') {
        const url = keyword 
            ? `${this.baseUrl}/classes?keyword=${encodeURIComponent(keyword)}`
            : `${this.baseUrl}/classes`;
        const data = await this.request(url);
        return data.classes || [];
    }

    async getClassInfo(className) {
        return this.request(`${this.baseUrl}/classes/${encodeURIComponent(className)}`);
    }

    async getClassInfoWithMock(className, projectName, planName) {
        const url = `${this.baseUrl}/classes/${encodeURIComponent(className)}/with-mock?projectName=${encodeURIComponent(projectName)}&planName=${encodeURIComponent(planName)}`;
        return this.request(url);
    }
}

export default APIManager;
