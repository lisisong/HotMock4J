class ClassEditor {
    constructor(apiManager, uiManager) {
        this.apiManager = apiManager;
        this.uiManager = uiManager;
    }

    getRawTypeName(typeStr = '') {
        try {
            let t = String(typeStr || '').trim();
            if (!t) return '';
            const lt = t.indexOf('<');
            if (lt > -1) t = t.substring(0, lt);
            while (t.endsWith("[]")) t = t.substring(0, t.length - 2);
            return t.trim();
        } catch (e) { return ''; }
    }

    deriveReturnClassName(method = {}) {
        return method.returnClassName || this.getRawTypeName(method.returnType) || '';
    }

    async showClassEditModal(className, currentMockPlan) {
        const mockClass = currentMockPlan.mockClassList.find(c => c.className === className);
        if (!mockClass) {
            this.uiManager.showMessage('Class information not found', 'error');
            return;
        }

        try {
            const latestClassInfo = await this.apiManager.getClassInfoWithMock(
                className, 
                currentMockPlan.project.projectName, 
                currentMockPlan.planName
            );
            
            const modal = document.createElement('div');
            modal.className = 'modal';
            modal.id = 'classEditModal';
            modal.innerHTML = `
                <div class="modal-content">
                    <div class="modal-header">
                        <h3>Edit Class: ${className}</h3>
                        <span class="close">&times;</span>
                    </div>
                    <div class="modal-body">
                        <div class="form-group">
                            <label>ClassName</label>
                            <input type="text" value="${className}" disabled>
                        </div>
                        <div class="form-group">
                            <label>Template Name <span class="required">*</span></label>
                            <input type="text" class="template-name-input" value="${mockClass.templateName || ''}" placeholder="Enter a unique template name" required>
                            <div class="field-hint">Template name must be unique within the same Mock Plan</div>
                        </div>
                        <div class="info-message">
                            <p>Loaded the latest class information and compared it against the existing mock configuration</p>
                        </div>
                        ${this.renderFieldsEdit(latestClassInfo.fields, currentMockPlan)}
                        ${this.renderMethodsEdit(latestClassInfo.methods, currentMockPlan)}
                        <div class="form-actions">
                            <div style="flex:1;display:flex;gap:8px;align-items:center;">
                                <button type="button" class="delete-btn" id="deleteMockClass">Delete Class</button>
                            </div>
                            <div style="display:flex;gap:8px;align-items:center;">
                                <button type="button" class="cancel-btn">Cancel</button>
                                <button type="button" id="saveClassEdit">Save</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.body.appendChild(modal);

            this.bindClassEditEvents(modal, latestClassInfo, currentMockPlan);

            modal.style.display = 'block';

        } catch (error) {
            console.error('Failed to load latest class info:', error);
            this.uiManager.showMessage('Failed to load latest class information: ' + error.message, 'error');
        }
    }

    bindClassEditEvents(modal, mockClass, currentMockPlan) {
        modal.querySelector('.close').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('.cancel-btn').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('#saveClassEdit').addEventListener('click', async () => {
            await this.saveClassEdit(mockClass, modal, currentMockPlan);
        });

        const deleteBtn = modal.querySelector('#deleteMockClass');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', async () => {
                await this.handleDeleteMockClass(mockClass, modal, currentMockPlan);
            });
        }

        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.remove();
            }
        });

        this.bindToggleButtons(modal);
        
        this.bindEditMockButtons(modal, mockClass);
        this.injectManageTemplateButtons(modal, mockClass);
        this.bindManageTemplateButtons(modal, currentMockPlan);

        // If the field type is an interface, enhance its template dropdown to allow attaching implementing class templates
        this.enhanceInterfaceTemplateOptions(modal, mockClass, currentMockPlan);

        // If the method return type is an interface, enhance its template dropdown to allow attaching implementing class templates
        this.enhanceMethodInterfaceTemplateOptions(modal, mockClass, currentMockPlan);

        // Bind "Select implementation class" buttons (fields and methods)
        this.bindSelectImplButtons(modal, currentMockPlan);
    }

    async handleDeleteMockClass(mockClass, modal, currentMockPlan) {
        try {
            const className = mockClass.className || '';
            if (!className) {
                this.uiManager.showMessage('Unable to determine which class to delete', 'error');
                return;
            }

            const ok = confirm(`Confirm deletion of Mock Class: ${className}?\nThis will remove the class templates and configuration from the current plan.`);
            if (!ok) return;

            const result = await this.apiManager.deleteMockClass(
                currentMockPlan.project.projectName,
                currentMockPlan.planName,
                className
            );

            if (result && result.success) {
                try {
                    if (currentMockPlan && Array.isArray(currentMockPlan.mockClassList)) {
                        currentMockPlan.mockClassList = currentMockPlan.mockClassList.filter(c => c.className !== className);
                    }
                } catch (e) { /* ignore sync error */ }
                this.uiManager.showMessage('Deleted successfully', 'success');
                modal.remove();
            } else {
                this.uiManager.showMessage('Deletion failed' + (result && result.message ? ': ' + result.message : ''), 'error');
            }
        } catch (error) {
            console.error('Failed to delete mock class:', error);
            this.uiManager.showMessage('Deletion failed: ' + error.message, 'error');
        }
    }

    bindToggleButtons(modal) {
        const toggleFieldsBtn = modal.querySelector('#toggleAllFields');
        if (toggleFieldsBtn) {
            toggleFieldsBtn.addEventListener('click', () => {
                const checkboxes = modal.querySelectorAll('.mock-checkbox[data-type="field"]:not(:disabled)');
                const allChecked = Array.from(checkboxes).every(cb => cb.checked);
                checkboxes.forEach(cb => cb.checked = !allChecked);
            });
        }

        const toggleMethodsBtn = modal.querySelector('#toggleAllMethods');
        if (toggleMethodsBtn) {
            toggleMethodsBtn.addEventListener('click', () => {
                const checkboxes = modal.querySelectorAll('.mock-checkbox[data-type="method"]:not(:disabled)');
                const allChecked = Array.from(checkboxes).every(cb => cb.checked);
                checkboxes.forEach(cb => cb.checked = !allChecked);
            });
        }
    }

    bindEditMockButtons(modal, mockClass) {
        modal.querySelectorAll('.edit-mock-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const type = btn.getAttribute('data-type');
                const name = btn.getAttribute('data-name');
                this.showMockEditModal(type, name, mockClass);
            });
        });
    }

    showMockEditModal(type, name, mockClass) {
        let currentValue = '';
        let title = '';
        let returnType = '';
        let fieldClassName = '';
        let isPrimitive = true;
        
        if (type === 'field') {
            const field = mockClass.fields.find(f => f.fieldName === name);
            currentValue = field ? field.mockFieldValue || '' : '';
            title = `Edit Mock value for field "${name}"`;
            fieldClassName = field ? field.fieldClassName || '' : '';
            isPrimitive = field ? field.isPrimitive : true;
        } else if (type === 'method') {
            const method = mockClass.methods.find(m => m.methodName === name);
            currentValue = method ? method.returnObject || '' : '';
            returnType = method ? method.returnType || '' : '';
            title = `Edit Mock return value for method "${name}"`;
        }

        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'mockEditModal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>${title}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body">
                    ${returnType ? `<div class="form-group">
                        <label>Return Type</label>
                        <input type="text" value="${returnType}" disabled class="return-type-display">
                        <button type="button" class="new-btn load-fields-btn" style="margin-top: 8px;" data-return-type="${returnType}">Load object fields</button>
                    </div>` : ''}
                    ${type === 'field' && fieldClassName ? `<div class="form-group">
                        <label>Field Type</label>
                        <input type="text" value="${fieldClassName}" disabled class="field-class-display">
                        ${!isPrimitive ? `<button type="button" class="new-btn load-field-class-btn" style="margin-top: 8px;" data-field-class="${fieldClassName}">Load object fields</button>` : ''}
                    </div>` : ''}
                    <div class="form-group">
                        <label>Mock Value</label>
                        <div class="mock-value-editor">
                            <div class="editor-tabs">
                                <button type="button" class="tab-btn active" data-tab="text">Text</button>
                                <button type="button" class="tab-btn" data-tab="json">JSON Object</button>
                                <button type="button" class="tab-btn" data-tab="class">Class Instance</button>
                            </div>
                            <div class="tab-content">
                                <div class="tab-pane active" id="text-tab">
                                    <textarea class="mock-value-textarea" placeholder="Enter mock value (supports primitives, strings, JSON)" rows="6">${currentValue}</textarea>
                                </div>
                                <div class="tab-pane" id="json-tab">
                                    <textarea class="mock-value-json" placeholder='Enter a JSON object, e.g. {"name": "test", "value": 123}' rows="6"></textarea>
                                    <div class="json-preview" style="display: none;">
                                        <h4>JSON Preview</h4>
                                        <pre class="json-preview-content"></pre>
                                    </div>
                                </div>
                                <div class="tab-pane" id="class-tab">
                                    <div class="class-instance-editor">
                                        <div class="form-group">
                                            <label>Class Name</label>
                                            <input type="text" class="class-name-input" placeholder="Enter the full class name">
                                        </div>
                                        <div class="form-group">
                                            <label>Field Configuration</label>
                                            <div class="class-fields-list">
                                                <!-- Dynamically generated field configuration -->
                                            </div>
                                            <button type="button" class="new-btn add-field-btn">Add Field</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="cancel-btn">Cancel</button>
                        <button type="button" id="saveMockValue" data-type="${type}" data-name="${name}">Save</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        this.initMockValueEditor(modal, currentValue);

        if (returnType && this.isObjectType(returnType)) {
            this.autoLoadClassFields(modal, returnType);
        }

        if (type === 'field' && fieldClassName && !isPrimitive) {
            this.autoLoadClassFields(modal, fieldClassName);
        }

        if (returnType) {
            this.bindLoadFieldsButton(modal, returnType);
        }

        if (type === 'field' && fieldClassName && !isPrimitive) {
            this.bindFieldClassLoadButton(modal, fieldClassName);
        }

        modal.querySelector('.close').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('.cancel-btn').addEventListener('click', () => {
            modal.remove();
        });

        modal.querySelector('#saveMockValue').addEventListener('click', () => {
            this.saveMockValue(type, name, mockClass, modal);
        });

        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.remove();
            }
        });

        modal.style.display = 'block';
        modal.querySelector('.mock-value-textarea').focus();
    }

    isObjectType(typeName) {
        const basicTypes = ['void', 'int', 'long', 'double', 'float', 'boolean', 'char', 'byte', 'short'];
        const typeLower = typeName.toLowerCase();
        
        if (basicTypes.includes(typeLower)) {
            return false;
        }
        
        if (typeName.endsWith('[]')) {
            const elementType = typeName.substring(0, typeName.length - 2);
            return !basicTypes.includes(elementType.toLowerCase());
        }
        
        return true;
    }

    /**
     * Trigger a refresh on the main interface
     */
    triggerMainViewRefresh() {
        // Fire a custom event to notify the main interface to refresh
        const refreshEvent = new CustomEvent('mockClassUpdated', {
            detail: { action: 'saved' }
        });
        document.dispatchEvent(refreshEvent);
    }

    async autoLoadClassFields(modal, className) {
        try {
            const classInfo = await this.apiManager.getClassInfo(className);
            
            if (classInfo && classInfo.fields && classInfo.fields.length > 0) {
                const classNameInput = modal.querySelector('.class-name-input');
                classNameInput.value = className;
                
                const fieldsList = modal.querySelector('.class-fields-list');
                fieldsList.innerHTML = '';
                
                classInfo.fields.forEach(field => {
                    const fieldHtml = `
                        <div class="class-field-item">
                            <div class="field-row">
                                <input type="text" class="field-name" value="${field.fieldName}" readonly>
                                <input type="text" class="field-type" value="${field.fieldType}" readonly>
                                <input type="text" class="field-value" placeholder="Enter field value">
                                <button type="button" class="remove-field-btn">Delete</button>
                            </div>
                        </div>
                    `;
                    fieldsList.insertAdjacentHTML('beforeend', fieldHtml);
                    
                    const removeBtn = fieldsList.lastElementChild.querySelector('.remove-field-btn');
                    removeBtn.addEventListener('click', (e) => {
                        e.target.closest('.class-field-item').remove();
                    });
                });
                
                console.log(`Automatically loaded ${classInfo.fields.length} fields`);
            }
        } catch (error) {
            console.error('Failed to auto load fields:', error);
        }
    }

    bindLoadFieldsButton(modal, returnType) {
        const loadFieldsBtn = modal.querySelector('.load-fields-btn');
        loadFieldsBtn.addEventListener('click', async () => {
            await this.loadClassFields(modal, returnType);
        });
    }

    bindFieldClassLoadButton(modal, fieldClassName) {
        const loadFieldClassBtn = modal.querySelector('.load-field-class-btn');
        loadFieldClassBtn.addEventListener('click', async () => {
            await this.loadClassFields(modal, fieldClassName);
        });
    }

    async loadClassFields(modal, className) {
        try {
            const loadFieldsBtn = modal.querySelector('.load-fields-btn');
            loadFieldsBtn.disabled = true;
            loadFieldsBtn.textContent = 'Loading...';
            
            const classInfo = await this.apiManager.getClassInfo(className);
            
            if (classInfo && classInfo.fields && classInfo.fields.length > 0) {
                const classTabBtn = modal.querySelector('.tab-btn[data-tab="class"]');
                classTabBtn.click();
                
                const classNameInput = modal.querySelector('.class-name-input');
                classNameInput.value = className;
                
                const fieldsList = modal.querySelector('.class-fields-list');
                fieldsList.innerHTML = '';
                
                classInfo.fields.forEach(field => {
                    const fieldHtml = `
                        <div class="class-field-item">
                            <div class="field-row">
                                <input type="text" class="field-name" value="${field.fieldName}" readonly>
                                <input type="text" class="field-type" value="${field.fieldType}" readonly>
                                <input type="text" class="field-value" placeholder="Enter field value">
                                <button type="button" class="remove-field-btn">Delete</button>
                            </div>
                        </div>
                    `;
                    fieldsList.insertAdjacentHTML('beforeend', fieldHtml);
                    
                    const removeBtn = fieldsList.lastElementChild.querySelector('.remove-field-btn');
                    removeBtn.addEventListener('click', (e) => {
                        e.target.closest('.class-field-item').remove();
                    });
                });
                
                this.uiManager.showMessage(`Loaded ${classInfo.fields.length} fields`, 'success');
            } else {
                this.uiManager.showMessage('No field information found or this class has no fields', 'warning');
            }
        } catch (error) {
            console.error('Failed to load fields:', error);
            this.uiManager.showMessage('Failed to load fields: ' + error.message, 'error');
        } finally {
            const loadFieldsBtn = modal.querySelector('.load-fields-btn');
            loadFieldsBtn.disabled = false;
            loadFieldsBtn.textContent = 'Load object fields';
        }
    }

    initMockValueEditor(modal, currentValue) {
        const tabBtns = modal.querySelectorAll('.tab-btn');
        const tabPanes = modal.querySelectorAll('.tab-pane');
        const jsonTextarea = modal.querySelector('.mock-value-json');
        const jsonPreview = modal.querySelector('.json-preview');
        const jsonPreviewContent = modal.querySelector('.json-preview-content');

        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const tabName = btn.getAttribute('data-tab');
                
                tabBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                
                tabPanes.forEach(pane => pane.classList.remove('active'));
                modal.querySelector(`#${tabName}-tab`).classList.add('active');

                if (tabName === 'json' && currentValue) {
                    try {
                        const jsonObj = JSON.parse(currentValue);
                        jsonTextarea.value = JSON.stringify(jsonObj, null, 2);
                        jsonPreviewContent.textContent = JSON.stringify(jsonObj, null, 2);
                        jsonPreview.style.display = 'block';
                    } catch (e) {
                        jsonTextarea.value = currentValue;
                        jsonPreview.style.display = 'none';
                    }
                }
            });
        });

        jsonTextarea.addEventListener('input', () => {
            try {
                const jsonObj = JSON.parse(jsonTextarea.value);
                jsonPreviewContent.textContent = JSON.stringify(jsonObj, null, 2);
                jsonPreview.style.display = 'block';
            } catch (e) {
                jsonPreview.style.display = 'none';
            }
        });

        this.initClassInstanceEditor(modal);
    }

    initClassInstanceEditor(modal) {
        const classNameInput = modal.querySelector('.class-name-input');
        const fieldsList = modal.querySelector('.class-fields-list');
        const addFieldBtn = modal.querySelector('.add-field-btn');

        addFieldBtn.addEventListener('click', () => {
            const fieldHtml = `
                <div class="class-field-item">
                    <div class="field-row">
                        <input type="text" class="field-name" placeholder="Field name">
                        <input type="text" class="field-type" placeholder="Field type">
                        <input type="text" class="field-value" placeholder="Field value">
                        <button type="button" class="remove-field-btn">Delete</button>
                    </div>
                </div>
            `;
            fieldsList.insertAdjacentHTML('beforeend', fieldHtml);

            const removeBtn = fieldsList.lastElementChild.querySelector('.remove-field-btn');
            removeBtn.addEventListener('click', (e) => {
                e.target.closest('.class-field-item').remove();
            });
        });

        fieldsList.querySelectorAll('.remove-field-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.target.closest('.class-field-item').remove();
            });
        });
    }

    saveMockValue(type, name, mockClass, modal) {
        let value = '';
        
        const activeTab = modal.querySelector('.tab-btn.active').getAttribute('data-tab');
        
        switch (activeTab) {
            case 'text':
                value = modal.querySelector('.mock-value-textarea').value.trim();
                break;
            case 'json':
                value = modal.querySelector('.mock-value-json').value.trim();
                if (value) {
                    try {
                        JSON.parse(value);
                    } catch (e) {
                        this.uiManager.showMessage('Invalid JSON format: ' + e.message, 'error');
                        return;
                    }
                }
                break;
            case 'class':
                value = this.buildClassInstanceValue(modal);
                break;
            default:
                value = modal.querySelector('.mock-value-textarea').value.trim();
        }
        
        if (type === 'field') {
            const field = mockClass.fields.find(f => f.fieldName === name);
            if (field) {
                field.mockFieldValue = value || null;
                field.isActive = !!value; // Auto-activate when a mock value is present
                const fieldItem = document.querySelector(`.field-edit-item .field-header .field-info input[data-name="${name}"]`);
                if (fieldItem) {
                    fieldItem.checked = !!value;
                    const display = fieldItem.closest('.field-edit-item').querySelector('.mock-value-display');
                    const valueSpan = display.querySelector('.mock-value');
                    display.style.display = value ? 'block' : 'none';
                    valueSpan.textContent = this.formatDisplayValue(value);
                }
            }
        } else if (type === 'method') {
            const method = mockClass.methods.find(m => m.methodName === name);
            if (method) {
                method.returnObject = value || null;
                method.isActive = !!value; // Auto-activate when a mock value is present
                const methodItem = document.querySelector(`.method-edit-item .method-header .method-info input[data-name="${name}"]`);
                if (methodItem) {
                    methodItem.checked = !!value;
                    const display = methodItem.closest('.method-edit-item').querySelector('.mock-value-display');
                    const valueSpan = display.querySelector('.mock-value');
                    display.style.display = value ? 'block' : 'none';
                    valueSpan.textContent = this.formatDisplayValue(value);
                }
            }
        }

        modal.remove();
    }

    buildClassInstanceValue(modal) {
        const className = modal.querySelector('.class-name-input').value.trim();
        const fieldItems = modal.querySelectorAll('.class-field-item');
        
        if (!className) {
            this.uiManager.showMessage('Please enter the class name', 'error');
            return '';
        }
        
        const classConfig = {
            className: className,
            fields: []
        };
        
        fieldItems.forEach(item => {
            const fieldName = item.querySelector('.field-name').value.trim();
            const fieldType = item.querySelector('.field-type').value.trim();
            const fieldValue = item.querySelector('.field-value').value.trim();
            
            if (fieldName && fieldType) {
                classConfig.fields.push({
                    name: fieldName,
                    type: fieldType,
                    value: fieldValue
                });
            }
        });
        
        return JSON.stringify(classConfig);
    }

    formatDisplayValue(value) {
        if (!value) return '';
        
        try {
            const obj = JSON.parse(value);
            if (obj.className) {
                return `Class instance: ${obj.className} (${obj.fields?.length || 0} fields)`;
            } else {
                return JSON.stringify(obj);
            }
        } catch (e) {
            return value.length > 50 ? value.substring(0, 50) + '...' : value;
        }
    }

    renderFieldsEdit(fields, currentMockPlan) {
        if (!fields || fields.length === 0) {
            return '<div class="form-group"><label>Fields</label><div class="empty-state">No fields</div></div>';
        }

        return `
            <div class="form-group">
                <div class="section-header">
                    <label>Field Configuration</label>
                    <button type="button" class="new-btn" id="toggleAllFields">Select All/Cancel</button>
                </div>
                <div class="fields-edit-list">
                    <table class="fields-edit-table">
                        <thead>
                            <tr>
                                <th style="width: 80px;">Select</th>
                                <th>Field Name</th>
                                <th>Type</th>
                                <th style="min-width: 220px;">Attached Template</th>
                                <th>Mock Value</th>
                                <th style="width: 120px;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${fields.map(field => {
                                let templateOptions = '<option value="">Do not attach</option>';
                                try {
                                    const sameClassTemplates = (currentMockPlan?.mockClassList || [])
                                        .filter(mc => mc.className === field.fieldClassName && mc.templateName)
                                        .map(mc => mc.templateName);
                                    const unique = Array.from(new Set(sameClassTemplates));
                                    templateOptions = ['<option value="">Do not attach</option>']
                                        .concat(unique.map(t => `<option value="${t}" ${field.activeTemplate === t ? 'selected' : ''}>${t}</option>`))
                                        .join('');
                                } catch (e) {}
                                const showTemplate = !!field.fieldClassName && field.primitive !== true;
                                return `
                                <tr class="field-edit-item">
                                    <td>
                                        <div class="field-header">
                                            <div class="field-info">
                                                <input type="checkbox" class="mock-checkbox" data-type="field" data-name="${field.fieldName}" ${field.active ? 'checked' : ''}>
                                            </div>
                                        </div>
                                    </td>
                                    <td><span class="field-name">${field.fieldName}</span></td>
                                    <td><span class="field-type">${field.fieldType || 'Unknown type'}</span></td>
                                    <td>
                                        ${showTemplate ? `
                                        <div class="field-edit-controls">
                                            <select class="template-select" data-name="${field.fieldName}">${templateOptions}</select>
                                            <span class="template-hint"></span>
                                        </div>
                                        ` : `<div class="field-edit-controls"></div>`}
                                    </td>
                                    <td>
                                        <div class="mock-value-display" style="display: ${field.mockFieldValue ? 'block' : 'none'};">
                                            <span class="mock-value">${field.mockFieldValue || ''}</span>
                                        </div>
                                    </td>
                                    <td>
                                        <button type="button" class="action-btn edit-mock-btn" data-type="field" data-name="${field.fieldName}">Edit Mock</button>
                                    </td>
                                </tr>`;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    renderMethodsEdit(methods, currentMockPlan) {
        if (!methods || methods.length === 0) {
            return '<div class="form-group"><label>Methods</label><div class="empty-state">No methods</div></div>';
        }

        return `
            <div class="form-group">
                <div class="section-header">
                    <label>Method Configuration</label>
                    <button type="button" class="new-btn" id="toggleAllMethods">Select All/Cancel</button>
                </div>
                <div class="methods-edit-list">
                    <table class="methods-edit-table">
                        <thead>
                            <tr>
                                <th style="width: 80px;">Select</th>
                                <th>Method Name</th>
                                <th>Return Type</th>
                                <th style="min-width: 320px;">Return Configuration</th>
                                <th style="width: 120px;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${methods.map(method => {
                                let tplOptions = '';
                                let showTpl = true;
                                try {
                                    const names = Array.from(new Set(
                                        (currentMockPlan?.mockClassList || [])
                                            .filter(mc => mc.className === method.returnClassName && mc.templateName)
                                            .map(mc => mc.templateName)
                                    ));
                                    tplOptions = ['<option value="">Do not attach</option>']
                                        .concat(names.map(t => `<option value="${t}" ${method.activeReturnTemplateName === t ? 'selected' : ''}>${t}</option>`))
                                        .join('');
                                } catch (e) {}
                                return `
                                <tr class="method-edit-item">
                                    <td>
                                        <div class="method-header">
                                            <div class="method-info">
                                                <input type="checkbox" class="mock-checkbox" data-type="method" data-name="${method.methodName}" ${((!method.returnType || String(method.returnType).trim().toLowerCase() === 'void')) ? 'disabled' : (method.active ? 'checked' : '')}>
                                            </div>
                                        </div>
                                    </td>
                                    <td><span class="method-name">${method.methodName}</span></td>
                                    <td><span class="return-type">${method.returnType || 'void'}</span></td>
                                    <td>
                                        ${(showTpl && !(String(method.returnType || '').trim().toLowerCase() === 'void')) ? `
                                        <div class="method-edit-controls">
                                            <select class="return-template-select" data-name="${method.methodName}" data-return-class="${method.returnClassName || this.getRawTypeName(method.returnType) || ''}">${tplOptions}</select>
                                            <span class="template-hint"></span>
                                        </div>
                                        ` : ''}
                                        <div class="mock-value-display" style="display: ${method.returnObject ? 'block' : 'none'};">
                                            <span class="mock-value">${method.returnObject || ''}</span>
                                        </div>
                                    </td>
                                    <td>
                                        ${!(String(method.returnType || '').trim().toLowerCase() === 'void') ? `<button type="button" class="action-btn edit-mock-btn" data-type="method" data-name="${method.methodName}">Edit Mock</button>` : ''}
                                    </td>
                                </tr>`;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    async saveClassEdit(mockClass, modal, currentMockPlan) {
        try {
            const templateNameInput = modal.querySelector('.template-name-input');
            const templateName = templateNameInput.value.trim();
            
            if (!templateName) {
                this.uiManager.showMessage('Template name is required', 'error');
                templateNameInput.focus();
                return false;
            }

            const fieldCheckboxes = modal.querySelectorAll('.mock-checkbox[data-type="field"]');
            const methodCheckboxes = modal.querySelectorAll('.mock-checkbox[data-type="method"]');
            
            fieldCheckboxes.forEach(checkbox => {
                const fieldName = checkbox.getAttribute('data-name');
                const field = mockClass.fields.find(f => f.fieldName === fieldName);
                if (field) {
                    field.isActive = checkbox.checked;
                    const sel = modal.querySelector(`.template-select[data-name="${fieldName}"]`);
                    if (sel) {
                        field.activeTemplate = sel.value || null;
                    }
                }
            });
            
            methodCheckboxes.forEach(checkbox => {
                const methodName = checkbox.getAttribute('data-name');
                const method = mockClass.methods.find(m => m.methodName === methodName);
                if (method) {
                    method.isActive = checkbox.checked;
                    const sel = modal.querySelector(`.return-template-select[data-name="${methodName}"]`);
                    if (sel) {
                        method.activeReturnTemplateName = sel.value || null;
                    }
                }
            });

            const mockContent = {
                className: mockClass.className,
                classPackage: mockClass.classPackage,
                templateName: templateName,
                active: mockClass.isActive,
                fields: [],
                methods: []
            };

            if (mockClass.fields) {
                mockClass.fields.forEach(field => {
                    if (field.isActive || field.mockFieldValue || field.activeTemplate) {
                        mockContent.fields.push({
                            fieldName: field.fieldName,
                            fieldType: field.fieldType,
                            mockFieldValue: field.mockFieldValue,
                            active: field.isActive,
                            activeTemplate: field.activeTemplate || null,
                            fieldClassName: field.fieldClassName || null,
                            primitive: field.primitive === true
                        });
                    }
                });
            }

            if (mockClass.methods) {
                mockClass.methods.forEach(method => {
                    if (method.isActive || method.returnObject || method.activeReturnTemplateName) {
                        mockContent.methods.push({
                            methodName: method.methodName,
                            returnObject: method.returnObject,
                            active: method.isActive,
                            activeReturnTemplateName: method.activeReturnTemplateName || null,
                            returnClassName: method.returnClassName || null
                        });
                    }
                });
            }

            const result = await this.apiManager.updateMockClass(
                currentMockPlan.project.projectName,
                currentMockPlan.planName,
                mockContent
            );

            if (result.success) {
                try {
                    const target = (currentMockPlan && currentMockPlan.mockClassList)
                        ? currentMockPlan.mockClassList.find(c => c.className === mockClass.className)
                        : null;
                    if (target) {
                        target.templateName = templateName;
                        target.fields = mockContent.fields;
                        target.methods = mockContent.methods;
                    }
                } catch (e) {
                    console.warn('Failed to sync local currentMockPlan after save:', e);
                }
                this.uiManager.showMessage('Saved successfully!', 'success');
                modal.remove();
                
                // Trigger a refresh on the main interface
                this.triggerMainViewRefresh();
                
                return true;
            } else {
                this.uiManager.showMessage('Save failed: ' + result.message, 'error');
            }
        } catch (error) {
            console.error('Failed to save class edit:', error);
            this.uiManager.showMessage('Save failed: ' + error.message, 'error');
        }
        return false;
    }

    injectManageTemplateButtons(modal, mockClass) {
        try {
            modal.querySelectorAll('.field-edit-controls').forEach(ctrl => {
                const selectEl = ctrl.querySelector('.template-select');
                if (selectEl && !ctrl.querySelector('.manage-template-btn')) {
                    const fieldName = selectEl.getAttribute('data-name');
                    const field = (mockClass.fields || []).find(f => f.fieldName === fieldName);
                    if (field && field.fieldClassName) {
                        const btn = document.createElement('button');
                        btn.type = 'button';
                        btn.className = 'new-btn manage-template-btn';
                        btn.textContent = 'Manage Templates';
                        btn.setAttribute('data-field-name', field.fieldName);
                        btn.setAttribute('data-field-class', field.fieldClassName);
                        const hint = ctrl.querySelector('.template-hint');
                        if (hint) {
                            ctrl.insertBefore(btn, hint);
                        } else {
                            ctrl.appendChild(btn);
                        }
                    }
                }
            });

            modal.querySelectorAll('.method-edit-controls').forEach(ctrl => {
                const selectEl = ctrl.querySelector('.return-template-select');
                if (selectEl && !ctrl.querySelector('.manage-template-btn')) {
                    const methodName = selectEl.getAttribute('data-name');
                    const method = (mockClass.methods || []).find(m => m.methodName === methodName);
                    if (method && method.returnClassName) {
                        const btn = document.createElement('button');
                        btn.type = 'button';
                        btn.className = 'new-btn manage-template-btn';
                        btn.textContent = 'Manage Templates';
                        btn.setAttribute('data-return-class', method.returnClassName);
                        ctrl.appendChild(btn);
                    }
                }
            });
        } catch (e) {
            console.warn('injectManageTemplateButtons failed:', e);
        }
    }

    bindManageTemplateButtons(modal, currentMockPlan) {
        modal.querySelectorAll('.manage-template-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const fieldClass = btn.getAttribute('data-field-class');
                const fieldName = btn.getAttribute('data-field-name');
                const returnClass = btn.getAttribute('data-return-class');
                const targetClassName = fieldClass || returnClass;
                if (!targetClassName) return;

                try {
                    await this.ensureClassInMockPlan(currentMockPlan, targetClassName);
                    await this.showClassEditModal(targetClassName, currentMockPlan);
                    // After editing, refresh the parent dropdown template options
                    if (fieldClass && fieldName) {
                        this.refreshFieldTemplateOptions(modal, fieldName, fieldClass, currentMockPlan);
                    }
                    if (returnClass) {
                        this.refreshAllMethodTemplateOptionsByClass(modal, returnClass, currentMockPlan);
                    }
                } catch (e) {
                    console.error('Failed to manage template:', e);
                    this.uiManager.showMessage('Failed to open/create template: ' + e.message, 'error');
                }
            });
        });
    }

    async ensureClassInMockPlan(currentMockPlan, className) {
        if (!currentMockPlan || !className) return;
        const exists = (currentMockPlan.mockClassList || []).some(c => c.className === className);
        if (exists) return;
        const payload = {
            className: className,
            classPackage: '',
            fields: [],
            methods: []
        };
        const result = await this.apiManager.updateMockClass(
            currentMockPlan.project.projectName,
            currentMockPlan.planName,
            payload
        );
        if (!(result && result.success)) {
            throw new Error(result && result.message ? result.message : 'Failed to add class to MockPlan');
        }
        currentMockPlan.mockClassList = currentMockPlan.mockClassList || [];
        currentMockPlan.mockClassList.push({
            className,
            classPackage: '',
            fields: [],
            methods: []
        });
    }

    refreshFieldTemplateOptions(modal, fieldName, fieldClassName, currentMockPlan) {
        const sel = modal.querySelector(`.template-select[data-name="${fieldName}"]`);
        if (!sel) return;
        const names = Array.from(new Set((currentMockPlan?.mockClassList || [])
            .filter(mc => mc.className === fieldClassName && mc.templateName)
            .map(mc => mc.templateName)));
        const html = ['<option value="">Do not attach</option>']
            .concat(names.map(t => `<option value="${t}">${t}</option>`))
            .join('');
        sel.innerHTML = html;

        // Update/create the "Manage Templates" button so it points to the currently selected implementation class
        try {
            const ctrl = sel.closest('.field-edit-controls') || sel.parentElement;
            if (ctrl) {
                let manageBtn = ctrl.querySelector('.manage-template-btn');
                const hint = ctrl.querySelector('.template-hint');
                if (!manageBtn) {
                    manageBtn = document.createElement('button');
                    manageBtn.type = 'button';
                    manageBtn.className = 'new-btn manage-template-btn';
                    manageBtn.textContent = 'Manage Templates';
                    if (hint) {
                        ctrl.insertBefore(manageBtn, hint);
                    } else {
                        ctrl.appendChild(manageBtn);
                    }
                }
                manageBtn.setAttribute('data-field-class', fieldClassName);
                manageBtn.setAttribute('data-field-name', fieldName);
            }
        } catch (e) { /* ignore */ }
    }

    refreshMethodTemplateOptions(modal, methodName, returnClassName, currentMockPlan) {
        const sel = modal.querySelector(`.return-template-select[data-name="${methodName}"]`);
        if (!sel) return;
        const names = Array.from(new Set((currentMockPlan?.mockClassList || [])
            .filter(mc => mc.className === returnClassName && mc.templateName)
            .map(mc => mc.templateName)));
        const html = ['<option value="">Do not attach</option>']
            .concat(names.map(t => `<option value="${t}">${t}</option>`))
            .join('');
        sel.innerHTML = html;
    }

    refreshAllMethodTemplateOptionsByClass(modal, returnClassName, currentMockPlan) {
        modal.querySelectorAll('.return-template-select').forEach(sel => {
            if (sel.getAttribute('data-return-class') !== returnClassName) return;
            const names = Array.from(new Set((currentMockPlan?.mockClassList || [])
                .filter(mc => mc.className === returnClassName && mc.templateName)
                .map(mc => mc.templateName)));
            const html = ['<option value="">Do not attach</option>']
                .concat(names.map(t => `<option value="${t}">${t}</option>`))
                .join('');
            sel.innerHTML = html;
        });
    }

    async refreshSingleMethodTemplateOptions(modal, methodName, interfaceName, currentMockPlan) {
        const sel = modal.querySelector(`.return-template-select[data-name="${methodName}"]`);
        if (!sel) return;
        const candidates = (currentMockPlan?.mockClassList || []).filter(mc => mc && mc.templateName);
        const allowed = [];
        for (const mc of candidates) {
            try {
                if (await this.doesClassImplementInterface(mc.className, interfaceName)) {
                    allowed.push(mc.templateName);
                }
            } catch (e) { /* ignore */ }
        }
        const html = ['<option value="">Do not attach</option>']
            .concat(allowed.map(t => `<option value="${t}">${t}</option>`))
            .join('');
        sel.innerHTML = html;
        const ctrl = sel.closest('.method-edit-controls') || sel.parentElement;
        const hint = ctrl?.querySelector('.template-hint');
        if (hint) {
            hint.textContent = 'Only show templates of classes implementing this interface (unique template name within the same plan)';
        }
    }

    async enhanceInterfaceTemplateOptions(modal, mockClass, currentMockPlan) {
        try {
            const fields = mockClass.fields || [];
            for (const field of fields) {
                const className = field.fieldClassName;
                if (!className) continue;
                let classInfo;
                try {
                    classInfo = await this.apiManager.getClassInfo(className);
                } catch (e) { continue; }
                if (!classInfo || !classInfo.isInterface) continue;

                const sel = modal.querySelector(`.template-select[data-name="${field.fieldName}"]`);
                if (!sel) continue;

                // Interface fields: only display templates for classes that implement the interface
                const ifaceName = className;
                const candidates = (currentMockPlan?.mockClassList || []).filter(mc => mc && mc.templateName);
                const allowed = [];
                for (const mc of candidates) {
                    try {
                        const ok = await this.doesClassImplementInterface(mc.className, ifaceName);
                        if (ok) allowed.push(mc.templateName);
                    } catch (e) { /* ignore */ }
                }

                const current = field.activeTemplate || '';
                const html = ['<option value="">Do not attach</option>']
                    .concat(allowed.map(t => `<option value="${t}" ${current === t ? 'selected' : ''}>${t}</option>`))
                    .join('');
                sel.innerHTML = html;
                // Show hint
                const hint = sel.parentElement?.querySelector('.template-hint');
                if (hint) {
                    hint.textContent = 'Only show templates of classes implementing this interface (unique template name within the same plan)';
                }

                // Insert a "Choose Implementation" button for interface fields if missing
                if (!sel.parentElement.querySelector('.select-impl-btn')) {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'new-btn select-impl-btn';
                    btn.textContent = 'Choose Implementation';
                    btn.setAttribute('data-field-name', field.fieldName);
                    btn.setAttribute('data-interface-class', className);
                    if (hint) {
                        sel.parentElement.insertBefore(btn, hint);
                    } else {
                        sel.parentElement.appendChild(btn);
                    }
                }
            }
        } catch (e) {
            console.warn('enhanceInterfaceTemplateOptions failed:', e);
        }
    }

    async enhanceMethodInterfaceTemplateOptions(modal, mockClass, currentMockPlan) {
        try {
            const methods = mockClass.methods || [];
            for (const method of methods) {
                const returnClass = this.deriveReturnClassName(method);
                if (!returnClass) continue;
                let classInfo;
                try {
                    classInfo = await this.apiManager.getClassInfo(returnClass);
                } catch (e) { continue; }
                if (!classInfo || !classInfo.isInterface) continue;

                const sel = modal.querySelector(`.return-template-select[data-name="${method.methodName}"]`);
                if (!sel) continue;

                // Interface returns: only display templates for classes that implement the interface
                const ifaceName = returnClass;
                const candidates = (currentMockPlan?.mockClassList || []).filter(mc => mc && mc.templateName);
                const allowed = [];
                for (const mc of candidates) {
                    try {
                        const ok = await this.doesClassImplementInterface(mc.className, ifaceName);
                        if (ok) allowed.push(mc.templateName);
                    } catch (e) { /* ignore */ }
                }

                const current = method.activeReturnTemplateName || '';
                const html = ['<option value="">Do not attach</option>']
                    .concat(allowed.map(t => `<option value="${t}" ${current === t ? 'selected' : ''}>${t}</option>`))
                    .join('');
                sel.innerHTML = html;

                // Show hint
                const ctrl = sel.closest('.method-edit-controls') || sel.parentElement;
                const hint = ctrl?.querySelector('.template-hint');
                if (hint) {
                    hint.textContent = 'Only show templates of classes implementing this interface (unique template name within the same plan)';
                }

                // Insert a "Choose Implementation" button for interface returns if missing
                if (ctrl && !ctrl.querySelector('.select-impl-btn')) {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'new-btn select-impl-btn';
                    btn.textContent = 'Choose Implementation';
                    btn.setAttribute('data-method-name', method.methodName);
                    btn.setAttribute('data-interface-class', ifaceName);
                    if (hint) {
                        ctrl.insertBefore(btn, hint);
                    } else {
                        ctrl.appendChild(btn);
                    }
                }
            }
        } catch (e) {
            console.warn('enhanceMethodInterfaceTemplateOptions failed:', e);
        }
    }

    bindSelectImplButtons(modal, currentMockPlan) {
        modal.querySelectorAll('.select-impl-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                const fieldName = btn.getAttribute('data-field-name');
                const methodName = btn.getAttribute('data-method-name');
                const iface = btn.getAttribute('data-interface-class');
                await this.openImplementationSelectModal(modal, { fieldName, methodName, interfaceName: iface }, currentMockPlan);
            });
        });
    }

    async openImplementationSelectModal(parentModal, target, currentMockPlan) {
        const { fieldName, methodName, interfaceName } = target || {};
        const modal = document.createElement('div');
        modal.className = 'modal';
        modal.id = 'implSelectModal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h3>Select Implementation Class: ${interfaceName}</h3>
                    <span class="close">&times;</span>
                </div>
                <div class="modal-body">
                    <div class="form-group">
                        <label>Search Class</label>
                        <input type="text" class="impl-search-input" placeholder="Enter keywords to search for classes (at least 2 characters)">
                    </div>
                    <div class="search-results" style="max-height:240px;overflow:auto;border:1px solid #3e3e42;border-radius:4px;background:#1e1e1e;padding:8px;">
                        <div class="empty-state">Enter keywords to start searching</div>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="cancel-btn">Cancel</button>
                        <button type="button" class="new-btn confirm-select" disabled>Select</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        modal.style.display = 'block';

        let selectedClass = '';
        const input = modal.querySelector('.impl-search-input');
        const results = modal.querySelector('.search-results');
        const confirmBtn = modal.querySelector('.confirm-select');

        const renderResults = (classNames=[]) => {
            if (!classNames || classNames.length === 0) {
                results.innerHTML = '<div class="empty-state">No matching results</div>';
                selectedClass = '';
                confirmBtn.disabled = true;
                return;
            }
            results.innerHTML = classNames.map(cn => `
                <div class="search-result-item" data-class="${cn}" style="padding:6px 8px;border-radius:3px;cursor:pointer;">
                    <span>${cn}</span>
                </div>
            `).join('');
            results.querySelectorAll('.search-result-item').forEach(item => {
                item.addEventListener('click', () => {
                    results.querySelectorAll('.search-result-item').forEach(i => i.style.background='');
                    item.style.background = '#2d2d30';
                    selectedClass = item.getAttribute('data-class');
                    confirmBtn.disabled = !selectedClass;
                });
            });
        };

        let timer;
        input.addEventListener('input', async () => {
            const kw = input.value.trim();
            clearTimeout(timer);
            timer = setTimeout(async () => {
                if (kw.length < 2) {
                    results.innerHTML = '<div class="empty-state">Please enter at least 2 characters</div>';
                    confirmBtn.disabled = true;
                    return;
                }
                try {
                    const list = await this.apiManager.searchClasses(kw);
                    const filtered = await this.filterClassesImplementing(list || [], interfaceName);
                    renderResults(filtered);
                } catch (e) {
                    results.innerHTML = `<div class="empty-state">Search failed: ${e.message}</div>`;
                    confirmBtn.disabled = true;
                }
            }, 250);
        });

        modal.querySelector('.close').addEventListener('click', () => modal.remove());
        modal.querySelector('.cancel-btn').addEventListener('click', () => modal.remove());

        confirmBtn.addEventListener('click', async () => {
            if (!selectedClass) return;
            try {
                await this.ensureClassInMockPlan(currentMockPlan, selectedClass);
                if (fieldName) {
                    this.refreshFieldTemplateOptions(parentModal, fieldName, selectedClass, currentMockPlan);
                }
                if (methodName) {
                    await this.refreshSingleMethodTemplateOptions(parentModal, methodName, interfaceName, currentMockPlan);
                }
                this.uiManager.showMessage('Implementation class templates loaded; please select from the dropdown', 'success');
            } catch (e) {
                this.uiManager.showMessage('Failed to load implementation class: ' + e.message, 'error');
            }
            modal.remove();
        });
    }

    async filterClassesImplementing(classNames, interfaceName) {
        const out = [];
        for (const cn of classNames) {
            try {
                if (await this.doesClassImplementInterface(cn, interfaceName)) {
                    out.push(cn);
                }
            } catch (e) { /* ignore */ }
        }
        return out;
    }

    async doesClassImplementInterface(className, interfaceName) {
        try {
            let current = className;
            const visited = new Set();
            while (current && !visited.has(current)) {
                visited.add(current);
                const info = await this.apiManager.getClassInfo(current);
                if (!info) break;
                const ifaceList = []
                    .concat(Array.isArray(info.interfaces) ? info.interfaces : [])
                    .concat(Array.isArray(info.allInterfaces) ? info.allInterfaces : []);
                if (ifaceList.includes(interfaceName)) return true;
                current = info.superClassName || info.superClass || info.superclass || '';
            }
        } catch (e) {
            // ignore
        }
        return false;
    }
}

export default ClassEditor;
