# 📋 E-výuka Error Messages - Current Implementation

## 📊 **Complete Error Messages (10 Types)**

### **Level 4: Individual Field Errors** ⭐ (4x)
```properties
error.validation.evyuka.subject.version.required = "Kód verze předmětu je povinný."
error.validation.evyuka.subject.required = "Kód předmětu je povinný."
error.validation.evyuka.discipline.required = "Kód studijního oboru je povinný." 
error.validation.evyuka.programme.required = "Kód studijního programu je povinný."
```

### **Level 2: Master Step Errors** ⭐ (6x)

#### **Primary L2 Errors (Context-Aware)** (3x)
```properties
# Full form - primary message  
error.validation.evyuka.codes.all.required = 
"Vyplňte aspoň jeden z kódů: Kód verze předmětu, Kód předmětu, Kód studijního oboru, alebo Kód studijního programu"

# Subject-only form - primary message
error.validation.evyuka.codes.subject.only.required = 
"Vyplňte aspoň jeden z kódů: Kód verze předmětu alebo Kód předmětu"

# Discipline-only form - primary message
error.validation.evyuka.codes.discipline.only.required = 
"Vyplňte aspoň jeden z kódov: Kód studijního oboru alebo Kód studijního programu"
```

#### **Additional L2 Errors (Form-Specific)** (3x)
```properties
# Full form - additional HTML message
error.validation.evyuka.combined.groups.required = 
"Dostupné sú oba typy kódov. Vyplňte aspoň jeden z predmetovej ALEBO disciplínovej skupiny"

# Subject-only form - additional message
error.validation.evyuka.subject.codes.required = 
"Následující atributy dokumentu (kód verze předmětu a kód předmětu) slouží k propojení..."

# Discipline-only form - additional message  
error.validation.evyuka.discipline.programme.codes.required = 
"Následující atributy dokumentu (kód studijního oboru a kód studijního programu) slouží k propojení..."
```

## 🎯 **Form Types & Error Generation**

| Form Type | Fields Available | Errors Generated When Failed |
|-----------|-----------------|------------------------------|
| **FULL_FORM** | All 4 codes | L4 (4x individual) + L2 (2x: primary + additional) |
| **SUBJECT_ONLY** | 2 subject codes | L4 (2x individual) + L2 (2x: primary + additional) |
| **DISCIPLINE_ONLY** | 2 discipline codes | L4 (2x individual) + L2 (2x: primary + additional) |

## ✅ **Complete Test Matrix**

| Form | Subject Fields | Discipline Fields | Result | L2 Error Messages |
|------|---------------|------------------|--------|-------------------|
| **Full** | Empty ⭕ | Empty ⭕ | ❌ FAIL | `all.required` + `combined.groups.required` |
| **Full** | Filled ✅ | Empty ⭕ | ✅ PASS | - |
| **Full** | Empty ⭕ | Filled ✅ | ✅ PASS | - |
| **Full** | Filled ✅ | Filled ✅ | ✅ PASS | - |
| **Subject** | Empty ⭕ | N/A | ❌ FAIL | `subject.only.required` + `subject.codes.required` |
| **Subject** | Filled ✅ | N/A | ✅ PASS | - |
| **Discipline** | N/A | Empty ⭕ | ❌ FAIL | `discipline.only.required` + `discipline.programme.codes.required` |
| **Discipline** | N/A | Filled ✅ | ✅ PASS | - |

### Legend:
- **Empty ⭕**: Všetky polia v skupine sú prázdne
- **Filled ✅**: Aspoň jedno pole v skupine je vyplnené

## 📄 **Error Response Examples**

### Full Form (Both Groups Empty)
```json
{
  "errors": [
    // L4: Individual fields (4x)
    {"message": "error.validation.evyuka.subject.version.required", "paths": ["/field"]},
    {"message": "error.validation.evyuka.subject.required", "paths": ["/field"]},
    {"message": "error.validation.evyuka.discipline.required", "paths": ["/field"]},
    {"message": "error.validation.evyuka.programme.required", "paths": ["/field"]},
    
    // L2: Master step (2x)  
    {"message": "error.validation.evyuka.codes.all.required", "paths": ["/step"]},
    {"message": "error.validation.evyuka.combined.groups.required", "paths": ["/step"]}
  ]
}
```

### Subject Form (Fields Empty)
```json
{
  "errors": [
    // L4: Individual fields (2x)
    {"message": "error.validation.evyuka.subject.version.required", "paths": ["/field"]},
    {"message": "error.validation.evyuka.subject.required", "paths": ["/field"]},
    
    // L2: Master step (2x)  
    {"message": "error.validation.evyuka.codes.subject.only.required", "paths": ["/step"]},
    {"message": "error.validation.evyuka.subject.codes.required", "paths": ["/step"]}
  ]
}
```

### Discipline Form (Fields Empty)
```json
{
  "errors": [
    // L4: Individual fields (2x)
    {"message": "error.validation.evyuka.discipline.required", "paths": ["/field"]},
    {"message": "error.validation.evyuka.programme.required", "paths": ["/field"]},
    
    // L2: Master step (2x)  
    {"message": "error.validation.evyuka.codes.discipline.only.required", "paths": ["/step"]},
    {"message": "error.validation.evyuka.discipline.programme.codes.required", "paths": ["/step"]}
  ]
}
```

## 📊 **Summary: All 10 Error Types**

| Level | Error Code | Used When | Path |
|-------|------------|-----------|------|
| **L4** | `subject.version.required` | Individual field empty | `/field` |
| **L4** | `subject.required` | Individual field empty | `/field` |
| **L4** | `discipline.required` | Individual field empty | `/field` |
| **L4** | `programme.required` | Individual field empty | `/field` |
| **L2** | `codes.all.required` | Full form - primary | `/step` |
| **L2** | `combined.groups.required` | Full form - additional | `/step` |
| **L2** | `codes.subject.only.required` | Subject form - primary | `/step` |
| **L2** | `subject.codes.required` | Subject form - additional | `/step` |
| **L2** | `codes.discipline.only.required` | Discipline form - primary | `/step` |
| **L2** | `discipline.programme.codes.required` | Discipline form - additional | `/step` |

## 🔧 **Current Implementation Notes**

- **No L3 HTML Group Errors**: Removed per requirements
- **Double L2 Errors**: Each failing form type generates 2 L2 master step errors
- **All L2 errors use `/step` path**: No distinction between primary and additional
- **Form Detection**: Automatic based on available fields in form configuration
- **Validation Logic**: 
  - Empty = all fields in group are empty
  - Filled = at least one field in group has value

**Error Generation Pattern:** L4 (individual) + L2 (primary + additional) = **Dual-level validation feedback** ✅

## 🔍 **Technical Implementation Details**

### **Validator Class**: `EvyukaCodesValidation.java`
- **Master validator pattern** with single entry point
- **FormType enum** for automatic form detection
- **addIndividualFieldErrors()** method for L4 generation
- **addAdditionalL2Errors()** method for double L2 generation
- **Line 105 comment**: "L3 HTML group errors removed per requirements"

### **Error Messages**: `Messages.properties`
- **10 total error definitions** (4 L4 + 6 L2)
- **Context-aware messaging** for different form types
- **Encoding**: UTF-8 with proper Czech/Slovak characters

### **Form Configuration**: `evyuka_form_*.xml`
- **Automatic field detection** determines FormType
- **Spring integration** with validation framework
- **Maven build compatibility** maintained

## ✅ **Verification Status**

- ✅ **L3 HTML errors removed**: No longer generated
- ✅ **Double L2 errors implemented**: Primary + additional for each form type
- ✅ **10 error types total**: 4 L4 individual + 6 L2 master step
- ✅ **Documentation accurate**: Matches current implementation
- ✅ **Maven build successful**: No compilation issues

**Final Status**: Documentation now accurately reflects the current E-výuka validation implementation with 10 error types and no L3 HTML group errors.