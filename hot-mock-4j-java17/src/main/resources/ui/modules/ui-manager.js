class UIManager {
    constructor() {
        this.currentMockPlan = null;
        this.currentSelectedClass = null;
    }

    renderMockPlans(mockPlans, currentMockPlan, onSelect, onActivate, onDelete) {
        const mockplanList = document.getElementById('mockplanList');
        mockplanList.innerHTML = '';

        if (!mockPlans || mockPlans.length === 0) {
            mockplanList.innerHTML = '<div class="empty-state">No MockPlans available, click "New" to create</div>';
            return;
        }

        mockPlans.forEach((mockplan) => {
            const mockplanItem = document.createElement('div');
            mockplanItem.className = 'mockplan-item';
            if (currentMockPlan && currentMockPlan.planName === mockplan.planName) {
                mockplanItem.classList.add('active');
            }

            mockplanItem.innerHTML = `
                <div class="mockplan-item-header">
                    <div class="mockplan-name">${mockplan.planName}</div>
                    <div class="mockplan-project">Project: ${mockplan.project.projectName}</div>
                    <div class="mockplan-actions">
                        <button class="action-btn activate-btn" data-project="${mockplan.project.projectName}" data-plan="${mockplan.planName}">
                            ${mockplan.active ? 'Activated' : 'Activate'}
                        </button>
                        <button class="action-btn delete-btn" data-project="${mockplan.project.projectName}" data-plan="${mockplan.planName}">Delete</button>
                    </div>
                </div>
                <div class="mockplan-meta">
                    <span class="creation-date">Created: ${new Date(mockplan.creationDate).toLocaleString()}</span>
                    <span class="status ${mockplan.active ? 'active' : 'inactive'}">
                        ${mockplan.active ? 'Activated' : 'Inactive'}
                    </span>
                </div>
            `;

            mockplanItem.addEventListener('click', (e) => {
                if (!e.target.classList.contains('action-btn')) {
                    onSelect(mockplan);
                }
            });

            mockplanList.appendChild(mockplanItem);
        });

        this.bindActionButtons(onActivate, onDelete);
    }

    bindActionButtons(onActivate, onDelete) {
        document.querySelectorAll('.activate-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectName = btn.getAttribute('data-project');
                const planName = btn.getAttribute('data-plan');
                onActivate(projectName, planName);
            });
        });

        document.querySelectorAll('.delete-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const projectName = btn.getAttribute('data-project');
                const planName = btn.getAttribute('data-plan');
                onDelete(projectName, planName);
            });
        });
    }

    showMockPlanDetails(mockPlan, onRefresh, onAddClass, onEditClass, onDeleteClass) {
        const welcomeMessage = document.getElementById('welcomeMessage');
        const mockplanDetails = document.getElementById('mockplanDetails');

        if (mockPlan) {
            welcomeMessage.style.display = 'none';
            mockplanDetails.style.display = 'block';
            mockplanDetails.innerHTML = `
                <div class="detail-header">
                    <h2>${mockPlan.planName}</h2>
                    <div class="detail-actions">
                        <button class="new-btn" id="refreshMockPlan">Refresh</button>
                    </div>
                </div>
                <div class="detail-content">
                    <div class="detail-section">
                        <h3>Basic Information</h3>
                        <p><strong>Project Name:</strong> ${mockPlan.project.projectName}</p>
                        <p><strong>Status:</strong> <span class="status ${mockPlan.active ? 'active' : 'inactive'}">
                            ${mockPlan.active ? 'Activated' : 'Inactive'}
                        </span></p>
                        <p><strong>Created:</strong> ${new Date(mockPlan.creationDate).toLocaleString()}</p>
                        <p><strong>JSON File:</strong> ${mockPlan.jsonFileName || 'Not set'}</p>
                    </div>
                    <div class="detail-section">
                        <div class="section-header">
                            <h3>Mock Classes</h3>
                            <button class="new-btn" id="addMockClass">Add Class</button>
                        </div>
                        ${this.renderMockClassList(mockPlan.mockClassList)}
                    </div>
                </div>
            `;

            document.getElementById('refreshMockPlan').addEventListener('click', onRefresh);

            document.getElementById('addMockClass').addEventListener('click', onAddClass);

            this.bindEditButtons(onEditClass, onDeleteClass);
        } else {
            welcomeMessage.style.display = 'block';
            mockplanDetails.style.display = 'none';
        }
    }

    renderMockClassList(mockClassList) {
        if (!mockClassList || mockClassList.length === 0) {
            return '<div class="empty-state">No mock classes</div>';
        }

        return `
            <div class="mock-class-list">
                ${mockClassList.map(mockClass => `
                    <div class="mock-class-item" data-class="${mockClass.className}">
                        <div class="class-header">
                            <div class="class-name">${mockClass.className || 'Unnamed class'}</div>
                            <div class="class-actions">
                                <button class="action-btn edit-btn" data-class="${mockClass.className}">Edit</button>
                                <button class="action-btn delete-class-btn" data-class="${mockClass.className}">Delete</button>
                            </div>
                        </div>
                        <div class="class-details">
                            <div class="class-meta">
                                <span class="template-badge ${mockClass.templateName ? 'has-template' : 'no-template'}" title="Template Name">
                                    <span class="template-badge-label">Template Name</span>
                                    <span class="template-badge-name">${mockClass.templateName ? mockClass.templateName : 'NONE'}</span>
                                </span>
                                <span class="field-count">${mockClass.fields ? mockClass.fields.length : 0} fields</span>
                                <span class="method-count">${mockClass.methods ? mockClass.methods.length : 0} methods</span>
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    bindEditButtons(onEditClass, onDeleteClass) {
        document.querySelectorAll('.edit-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const className = btn.getAttribute('data-class');
                onEditClass(className);
            });
        });

        if (onDeleteClass) {
            document.querySelectorAll('.delete-class-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const className = btn.getAttribute('data-class');
                    onDeleteClass(className);
                });
            });
        }
    }

    showClassSearchModal() {
        const modal = document.getElementById('classSearchModal');
        modal.style.display = 'block';
        document.getElementById('classSearchInput').focus();
        this.currentSelectedClass = null;
        document.getElementById('confirmAddClass').disabled = true;
        document.getElementById('classDetails').style.display = 'none';
        document.getElementById('classSearchResults').innerHTML = '<div class="empty-state">Enter keywords to search for classes</div>';
        const tplInput = document.getElementById('classTemplateName');
        if (tplInput) {
            tplInput.value = '';
            tplInput.addEventListener('input', () => this.updateConfirmAddState());
        }
    }

    hideClassSearchModal() {
        const modal = document.getElementById('classSearchModal');
        modal.style.display = 'none';
        document.getElementById('classSearchInput').value = '';
    }

    renderSearchResults(classes, onViewDetails) {
        const resultsContainer = document.getElementById('classSearchResults');
        
        if (classes.length === 0) {
            resultsContainer.innerHTML = '<div class="empty-state">No matching classes found</div>';
            return;
        }

        resultsContainer.innerHTML = classes.map(className => `
            <div class="search-result-item" data-class="${className}">
                <div class="class-name">${className}</div>
                <button class="action-btn view-details-btn">View Details</button>
            </div>
        `).join('');

        document.querySelectorAll('.view-details-btn').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const className = e.target.closest('.search-result-item').getAttribute('data-class');
                onViewDetails(className);
            });
        });

        document.querySelectorAll('.search-result-item').forEach(item => {
            item.addEventListener('click', async (e) => {
                if (!e.target.classList.contains('view-details-btn')) {
                    const className = item.getAttribute('data-class');
                    onViewDetails(className);
                }
            });
        });
    }

    showClassDetails(classInfo) {
        this.currentSelectedClass = classInfo;
        this.updateConfirmAddState();
        document.getElementById('classDetails').style.display = 'block';

        const classInfoContent = document.getElementById('classInfoContent');
        classInfoContent.innerHTML = `
            <div class="class-basic-info">
                <p><strong>Class Name:</strong> ${classInfo.simpleName}</p>
                <p><strong>Package:</strong> ${classInfo.classPackage}</p>
                <p><strong>Type:</strong> 
                    ${classInfo.isInterface ? 'Interface' : ''}
                    ${classInfo.isEnum ? 'Enum' : ''}
                    ${classInfo.isAnnotation ? 'Annotation' : ''}
                    ${classInfo.isArray ? 'Array' : ''}
                    ${classInfo.isPrimitive ? 'Primitive' : ''}
                    ${!classInfo.isInterface && !classInfo.isEnum && !classInfo.isAnnotation && !classInfo.isArray && !classInfo.isPrimitive ? 'Class' : ''}
                </p>
                <p><strong>Modifiers:</strong> ${this.getModifierString(classInfo.modifiers)}</p>
            </div>
            ${this.renderFieldsInfo(classInfo.fields)}
            ${this.renderMethodsInfo(classInfo.methods)}
        `;

        document.querySelectorAll('.search-result-item').forEach(item => {
            item.classList.remove('selected');
        });
        document.querySelector(`.search-result-item[data-class="${classInfo.className}"]`).classList.add('selected');
    }

    // Enable confirm only when a class is selected and template name is provided
    updateConfirmAddState() {
        const confirmBtn = document.getElementById('confirmAddClass');
        const tplInput = document.getElementById('classTemplateName');
        const hasClass = !!this.currentSelectedClass;
        const hasTpl = !!(tplInput && tplInput.value && tplInput.value.trim().length > 0);
        if (confirmBtn) {
            confirmBtn.disabled = !(hasClass && hasTpl);
        }
    }

    renderFieldsInfo(fields) {
        if (!fields || fields.length === 0) {
            return '<div class="info-section"><h5>Fields</h5><div class="empty-state">No fields</div></div>';
        }

        return `
            <div class="info-section">
                <h5>Fields (${fields.length})</h5>
                <div class="fields-list">
                    ${fields.map(field => `
                        <div class="field-item">
                            <div class="field-header">
                                <span class="field-name">${field.fieldName}</span>
                                <span class="field-type">${field.fieldType}</span>
                            </div>
                            <div class="field-meta">
                                <span class="modifiers">${this.getModifierString(field.modifiers)}</span>
                                <span class="accessible">${field.accessible ? 'Accessible' : 'Not accessible'}</span>
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    renderMethodsInfo(methods) {
        if (!methods || methods.length === 0) {
            return '<div class="info-section"><h5>Methods</h5><div class="empty-state">No methods</div></div>';
        }

        return `
            <div class="info-section">
                <h5>Methods (${methods.length})</h5>
                <div class="methods-list">
                    ${methods.map(method => `
                        <div class="method-item">
                            <div class="method-header">
                                <span class="method-name">${method.methodName}</span>
                                <span class="return-type">${method.returnType}</span>
                            </div>
                            <div class="method-meta">
                                <span class="modifiers">${this.getModifierString(method.modifiers)}</span>
                                ${method.parameters && method.parameters.length > 0 ? 
                                    `<span class="parameters">Parameters: ${method.parameters.map(p => p.type).join(', ')}</span>` : 
                                    '<span class="parameters">No parameters</span>'
                                }
                                ${method.exceptions && method.exceptions.length > 0 ? 
                                    `<span class="exceptions">Exceptions: ${method.exceptions.join(', ')}</span>` : 
                                    ''
                                }
                            </div>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    getModifierString(modifiers) {
        const modifierNames = [];
        if (modifiers & 0x0001) modifierNames.push('public');
        if (modifiers & 0x0002) modifierNames.push('private');
        if (modifiers & 0x0004) modifierNames.push('protected');
        if (modifiers & 0x0008) modifierNames.push('static');
        if (modifiers & 0x0010) modifierNames.push('final');
        if (modifiers & 0x0200) modifierNames.push('interface');
        if (modifiers & 0x0400) modifierNames.push('abstract');
        
        return modifierNames.length > 0 ? modifierNames.join(', ') : 'default';
    }

    showModal() {
        const modal = document.getElementById('newMockPlanModal');
        modal.style.display = 'block';
        document.getElementById('projectName').focus();
    }

    hideModal() {
        const modal = document.getElementById('newMockPlanModal');
        modal.style.display = 'none';
        this.resetForm();
    }

    resetForm() {
        document.getElementById('newMockPlanForm').reset();
    }

    showMessage(message, type) {
        const messageEl = document.createElement('div');
        messageEl.className = `message ${type}`;
        messageEl.textContent = message;
        
        document.body.appendChild(messageEl);
        
        setTimeout(() => {
            if (messageEl.parentNode) {
                messageEl.parentNode.removeChild(messageEl);
            }
        }, 3000);
    }
}

export default UIManager;
