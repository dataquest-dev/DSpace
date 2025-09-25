# 📋 E-výuka Validation System - UI Developer Guide

## 🎯 Overview

This guide provides comprehensive documentation for frontend developers working with the E-výuka submission form validation system in DSpace 7.6.5. The backend implements a two-step validation process for E-výuka forms with HTML-formatted error messages.

## 🏗️ Backend Architecture

### Validation Groups
The system implements two independent validation groups:

1. **`evyuka-subject-codes`** - Validates subject-related codes:
   - `local.evyuka.subject.version` (Kód verze předmětu)
   - `local.evyuka.subject` (Kód předmětu)

2. **`evyuka-discipline-programme-codes`** - Validates discipline/programme codes:
   - `local.evyuka.discipline` (Kód studijního oboru)
   - `local.evyuka.programme` (Kód studijního programu)

### Validation Classes

| Class | Purpose | Validation Group |
|-------|---------|------------------|
| `EvyukaSubjectCodesValidation.java` | Validates at least one subject code is filled | `evyuka-subject-codes` |
| `EvyukaDisciplineProgrammeCodesValidation.java` | Validates at least one discipline/programme code is filled | `evyuka-discipline-programme-codes` |

### Forms Configuration

All E-výuka forms are located in `/dspace/config/vsb/` and include validation groups:

- `evyuka_form_FBI.xml` - Fakulta bezpečnostního inženýrství
- `evyuka_form_EKF.xml` - Ekonomická fakulta
- `evyuka_form_FEI.xml` - Fakulta elektrotechniky a informatiky
- `evyuka_form_HGF.xml` - Hornicko-geologická fakulta
- `evyuka_form_FMT.xml` - Fakulta materiálově-technologická
- `evyuka_form_FS.xml` - Fakulta strojní
- `evyuka_form_USP.xml` - Ústav soudního inženýrství
- `evyuka_form_AUD.xml` - Auditorium
- `evyuka_form_9270.xml` - Special form 9270
- `evyuka_form_FAST.xml` - Fakulta stavební

## 📊 API Response Format

### Error Response Structure

When validation fails, the API returns validation errors in the following format:

```json
{
  "errors": [
    {
      "message": "Následující atributy dokumentu (kód verze předmětu a kód předmětu) slouží k propojení a <b>zveřejnění odkazu na dokument na stránkách detailu předmětu</b> v rámci portálu <a href=\"https://www.vsb.cz/e-vyuka\">E-výuka</a>. Jedná-li se o studijní oporu určenou pouze pro obor/program, tak následující kódy nevyplňujte.",
      "paths": [
        "/sections/e-vyuka-FBIpageone/local.evyuka.subject.version",
        "/sections/e-vyuka-FBIpageone/local.evyuka.subject"
      ],
      "code": "error.validation.evyuka.subject.codes.required"
    },
    {
      "message": "Následující atributy dokumentu (kód studijního oboru a kód studijního programu) slouží k propojení a <b>zveřejnění odkazu na dokument na stránkách detailu oboru/programu</b> v rámci portálu <a href=\"https://www.vsb.cz/e-vyuka\">E-výuka</a>. Jedná-li se o studijní oporu určenou pouze pro předmět, tak následující kódy nevyplňujte.",
      "paths": [
        "/sections/e-vyuka-FBIpageone/local.evyuka.discipline",
        "/sections/e-vyuka-FBIpageone/local.evyuka.programme"
      ],
      "code": "error.validation.evyuka.discipline.programme.codes.required"
    }
  ]
}
```

### Error Message Types

The backend provides localized error messages in `Messages.properties`:

```properties
# Group-level validation errors
error.validation.evyuka.subject.codes.required = Následující atributy dokumentu (kód verze předmětu a kód předmětu) slouží k propojení a <b>zveřejnění odkazu na dokument na stránkách detailu předmětu</b> v rámci portálu <a href="https://www.vsb.cz/e-vyuka">E-výuka</a>. Jedná-li se o studijní oporu určenou pouze pro obor/program, tak následující kódy nevyplňujte.

error.validation.evyuka.discipline.programme.codes.required = Následující atributy dokumentu (kód studijního oboru a kód studijního programu) slouží k propojení a <b>zveřejnění odkazu na dokument na stránkách detailu oboru/programu</b> v rámci portálu <a href="https://www.vsb.cz/e-vyuka">E-výuka</a>. Jedná-li se o studijní oporu určenou pouze pro předmět, tak následující kódy nevyplňujte.

# Individual field errors
error.validation.evyuka.subject.version.required = Kód verze předmětu je povinný.
error.validation.evyuka.subject.required = Kód předmětu je povinný.
error.validation.evyuka.discipline.required = Kód studijního oboru je povinný.
error.validation.evyuka.programme.required = Kód studijního programu je povinný.
```

## 🎨 Frontend Implementation Guide

### 1. Form Field Configuration

For each E-výuka form, implement the following field structure:

```html
<!-- Step 1: Subject Codes -->
<div class="validation-group" data-group="evyuka-subject-codes">
  <h4>1. Kódy předmětu</h4>
  
  <div class="form-field">
    <label for="subject-version">Kód verze předmětu</label>
    <input type="text" 
           id="subject-version"
           name="local.evyuka.subject.version"
           class="form-control">
  </div>
  
  <div class="form-field">
    <label for="subject-code">Kód předmětu</label>
    <input type="text" 
           id="subject-code"
           name="local.evyuka.subject"
           class="form-control">
  </div>
</div>

<!-- Step 2: Discipline/Programme Codes -->
<div class="validation-group" data-group="evyuka-discipline-programme-codes">
  <h4>2. Kódy oboru a programu</h4>
  
  <div class="form-field">
    <label for="discipline-code">Kód studijního oboru</label>
    <input type="text" 
           id="discipline-code"
           name="local.evyuka.discipline"
           class="form-control">
  </div>
  
  <div class="form-field">
    <label for="programme-code">Kód studijního programu</label>
    <input type="text" 
           id="programme-code"
           name="local.evyuka.programme"
           class="form-control">
  </div>
</div>
```

### 2. JavaScript Validation Handler

```javascript
class EvyukaValidationHandler {
  constructor() {
    this.validationGroups = {
      'evyuka-subject-codes': {
        fields: ['local.evyuka.subject.version', 'local.evyuka.subject'],
        isValid: false
      },
      'evyuka-discipline-programme-codes': {
        fields: ['local.evyuka.discipline', 'local.evyuka.programme'],
        isValid: false
      }
    };
    
    this.init();
  }
  
  init() {
    // Setup real-time validation
    this.setupRealTimeValidation();
    
    // Setup form submission handler
    this.setupSubmissionHandler();
  }
  
  setupRealTimeValidation() {
    Object.keys(this.validationGroups).forEach(groupName => {
      const group = this.validationGroups[groupName];
      
      group.fields.forEach(fieldName => {
        const field = document.querySelector(`[name="${fieldName}"]`);
        if (field) {
          field.addEventListener('input', () => {
            this.validateGroup(groupName);
          });
        }
      });
    });
  }
  
  validateGroup(groupName) {
    const group = this.validationGroups[groupName];
    let hasValue = false;
    
    group.fields.forEach(fieldName => {
      const field = document.querySelector(`[name="${fieldName}"]`);
      if (field && field.value.trim()) {
        hasValue = true;
      }
    });
    
    group.isValid = hasValue;
    this.updateGroupUI(groupName);
    
    return hasValue;
  }
  
  validateAllGroups() {
    let allValid = true;
    
    Object.keys(this.validationGroups).forEach(groupName => {
      if (!this.validateGroup(groupName)) {
        allValid = false;
      }
    });
    
    return allValid;
  }
  
  updateGroupUI(groupName) {
    const groupElement = document.querySelector(`[data-group="${groupName}"]`);
    const isValid = this.validationGroups[groupName].isValid;
    
    if (groupElement) {
      groupElement.classList.toggle('has-errors', !isValid);
      groupElement.classList.toggle('is-valid', isValid);
    }
  }
  
  displayServerErrors(errors) {
    // Clear previous errors
    this.clearErrors();
    
    errors.forEach(error => {
      const groupName = this.getGroupFromErrorCode(error.code);
      if (groupName) {
        this.displayGroupError(groupName, error.message);
      }
    });
  }
  
  getGroupFromErrorCode(errorCode) {
    const groupMapping = {
      'error.validation.evyuka.subject.codes.required': 'evyuka-subject-codes',
      'error.validation.evyuka.discipline.programme.codes.required': 'evyuka-discipline-programme-codes'
    };
    
    return groupMapping[errorCode];
  }
  
  displayGroupError(groupName, message) {
    const groupElement = document.querySelector(`[data-group="${groupName}"]`);
    if (!groupElement) return;
    
    // Remove existing error messages
    const existingError = groupElement.querySelector('.validation-error');
    if (existingError) {
      existingError.remove();
    }
    
    // Create error message element
    const errorElement = document.createElement('div');
    errorElement.className = 'validation-error alert alert-danger';
    errorElement.innerHTML = `
      <i class="fas fa-exclamation-triangle me-2"></i>
      <span>${message}</span>
    `;
    
    // Insert error at the beginning of the group
    groupElement.insertBefore(errorElement, groupElement.firstChild);
  }
  
  clearErrors() {
    document.querySelectorAll('.validation-error').forEach(error => {
      error.remove();
    });
    
    document.querySelectorAll('.validation-group').forEach(group => {
      group.classList.remove('has-errors');
    });
  }
  
  setupSubmissionHandler() {
    const form = document.getElementById('submission-form');
    if (form) {
      form.addEventListener('submit', (e) => {
        if (!this.validateAllGroups()) {
          e.preventDefault();
          this.showValidationSummary();
        }
      });
    }
  }
  
  showValidationSummary() {
    const invalidGroups = Object.keys(this.validationGroups)
      .filter(groupName => !this.validationGroups[groupName].isValid);
    
    if (invalidGroups.length > 0) {
      alert('Prosím, vyplňte povinné kódy před odesláním formuláře.');
    }
  }
}

// Initialize validation handler when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  if (document.querySelector('[data-group^="evyuka-"]')) {
    new EvyukaValidationHandler();
  }
});
```

### 3. CSS Styling

```css
/* E-výuka Validation Styles */
.validation-group {
  margin-bottom: 2rem;
  padding: 1.5rem;
  border-radius: 0.5rem;
  background-color: #f8f9fa;
  border: 2px solid transparent;
  transition: all 0.3s ease;
}

.validation-group.has-errors {
  border-color: #dc3545;
  background-color: #fff5f5;
}

.validation-group.is-valid {
  border-color: #28a745;
  background-color: #f0fff4;
}

.validation-group h4 {
  margin-bottom: 1rem;
  color: #495057;
  font-weight: 600;
}

.validation-error {
  margin-bottom: 1rem;
  padding: 1rem;
  border-radius: 0.25rem;
  border: 1px solid #f5c6cb;
}

.validation-error i {
  color: #721c24;
}

/* Preserve HTML formatting in error messages */
.validation-error b,
.validation-error strong {
  font-weight: 600;
}

.validation-error a {
  color: #004085;
  text-decoration: underline;
}

.validation-error a:hover {
  color: #002752;
  text-decoration: none;
}

/* Form field styling */
.form-field {
  margin-bottom: 1rem;
}

.form-field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #495057;
}

.form-control {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ced4da;
  border-radius: 0.25rem;
  transition: border-color 0.15s ease-in-out;
}

.form-control:focus {
  border-color: #80bdff;
  outline: 0;
  box-shadow: 0 0 0 0.2rem rgba(0, 123, 255, 0.25);
}

.has-errors .form-control {
  border-color: #dc3545;
}

.is-valid .form-control:not(:focus) {
  border-color: #28a745;
}

/* Progress indicator */
.validation-progress {
  display: flex;
  align-items: center;
  margin-top: 1rem;
  padding: 0.75rem;
  background-color: #e9ecef;
  border-radius: 0.25rem;
}

.validation-progress.completed {
  background-color: #d4edda;
  color: #155724;
}

.validation-progress.error {
  background-color: #f8d7da;
  color: #721c24;
}

.step-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.step-indicator i {
  font-size: 1.2em;
}

.step-label {
  font-weight: 500;
}

/* Responsive design */
@media (max-width: 768px) {
  .validation-group {
    padding: 1rem;
    margin-bottom: 1rem;
  }
  
  .validation-error {
    padding: 0.75rem;
    font-size: 0.9rem;
  }
}
```

### 4. Angular Implementation Example

For Angular applications, create a service and component:

```typescript
// evyuka-validation.service.ts
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface ValidationGroup {
  name: string;
  fields: string[];
  isValid: boolean;
  errorMessage?: string;
}

@Injectable({
  providedIn: 'root'
})
export class EvyukaValidationService {
  private groups = new BehaviorSubject<ValidationGroup[]>([
    {
      name: 'evyuka-subject-codes',
      fields: ['local.evyuka.subject.version', 'local.evyuka.subject'],
      isValid: true
    },
    {
      name: 'evyuka-discipline-programme-codes',
      fields: ['local.evyuka.discipline', 'local.evyuka.programme'],
      isValid: true
    }
  ]);

  getGroups(): Observable<ValidationGroup[]> {
    return this.groups.asObservable();
  }

  updateGroupValidation(groupName: string, isValid: boolean, errorMessage?: string): void {
    const groups = this.groups.value;
    const group = groups.find(g => g.name === groupName);
    if (group) {
      group.isValid = isValid;
      group.errorMessage = errorMessage;
      this.groups.next([...groups]);
    }
  }

  processServerErrors(errors: any[]): void {
    const groups = this.groups.value;
    
    // Reset all groups
    groups.forEach(group => {
      group.isValid = true;
      group.errorMessage = undefined;
    });

    // Process server errors
    errors.forEach(error => {
      const groupName = this.getGroupFromErrorCode(error.code);
      if (groupName) {
        this.updateGroupValidation(groupName, false, error.message);
      }
    });
  }

  private getGroupFromErrorCode(errorCode: string): string | undefined {
    const mapping: { [key: string]: string } = {
      'error.validation.evyuka.subject.codes.required': 'evyuka-subject-codes',
      'error.validation.evyuka.discipline.programme.codes.required': 'evyuka-discipline-programme-codes'
    };
    return mapping[errorCode];
  }
}
```

## 🧪 Testing Guidelines

### Manual Testing Checklist

- [ ] **Form Detection**: Verify E-výuka forms are properly identified
- [ ] **Real-time Validation**: Test client-side validation as user types
- [ ] **Server Validation**: Test server-side validation on form submission
- [ ] **Error Display**: Verify HTML-formatted error messages display correctly
- [ ] **Error Clearing**: Ensure errors clear when validation passes
- [ ] **Accessibility**: Test with screen readers and keyboard navigation
- [ ] **Responsive Design**: Test on mobile and tablet devices
- [ ] **Cross-browser**: Test in Chrome, Firefox, Safari, Edge

### Test Scenarios

1. **No codes filled**: Should show both group errors
2. **Only subject code filled**: Should show discipline/programme error
3. **Only discipline code filled**: Should show subject error  
4. **All codes filled**: Should pass validation
5. **Mixed valid/invalid combinations**: Test various combinations

### Unit Test Example (Jest)

```javascript
describe('EvyukaValidationHandler', () => {
  let handler;
  
  beforeEach(() => {
    document.body.innerHTML = `
      <div data-group="evyuka-subject-codes">
        <input name="local.evyuka.subject.version" />
        <input name="local.evyuka.subject" />
      </div>
    `;
    handler = new EvyukaValidationHandler();
  });
  
  test('should validate group when one field has value', () => {
    const field = document.querySelector('[name="local.evyuka.subject.version"]');
    field.value = 'TEST123';
    
    const isValid = handler.validateGroup('evyuka-subject-codes');
    
    expect(isValid).toBe(true);
  });
  
  test('should invalidate group when no fields have values', () => {
    const isValid = handler.validateGroup('evyuka-subject-codes');
    
    expect(isValid).toBe(false);
  });
});
```

## 📝 Integration Checklist

- [ ] **Backend APIs**: Ensure REST endpoints return proper error format
- [ ] **Field Names**: Match exact field names from backend configuration
- [ ] **Error Codes**: Handle all error codes defined in Messages.properties
- [ ] **HTML Sanitization**: Allow specific HTML tags in error messages (`<b>`, `<a href>`)
- [ ] **Internationalization**: Support Czech language error messages
- [ ] **Loading States**: Show appropriate loading indicators during validation
- [ ] **Progress Tracking**: Implement step-by-step validation feedback
- [ ] **Help Text**: Add links to E-výuka portal where appropriate

## 🚀 Deployment Notes

1. **Content Security Policy**: Ensure CSP allows inline HTML in error messages
2. **External Links**: E-výuka portal links should open in new tab/window
3. **Performance**: Implement debouncing for real-time validation (300ms recommended)
4. **Error Logging**: Log validation errors for debugging purposes
5. **Accessibility**: Ensure ARIA attributes are properly set for error announcements

## 🔧 Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| HTML not rendering in errors | Check HTML sanitization settings |
| Validation not triggering | Verify field names match backend config |
| Errors not clearing | Ensure clearErrors() is called on field changes |
| Mobile layout issues | Check responsive CSS media queries |

### Debug Tools

```javascript
// Debug validation state
console.log('Validation Groups:', handler.validationGroups);

// Test specific group validation
handler.validateGroup('evyuka-subject-codes');

// Simulate server error
handler.displayServerErrors([{
  code: 'error.validation.evyuka.subject.codes.required',
  message: 'Test error message'
}]);
```

## 📚 Additional Resources

- [DSpace 7 REST API Documentation](https://wiki.lyrasis.org/display/DSDOC7x/REST+API)
- [E-výuka Portal](https://www.vsb.cz/e-vyuka)
- [VSB-TUO DSpace Implementation](https://github.com/dataquest-dev/DSpace)

## 📞 Support

For technical support or questions, contact the VSB-TUO DSpace development team.

---

**Last Updated**: September 25, 2025  
**Version**: 1.0  
**DSpace Version**: 7.6.5