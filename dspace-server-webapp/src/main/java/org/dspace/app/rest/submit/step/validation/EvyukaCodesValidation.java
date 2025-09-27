/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step.validation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;

/**
 * Master validator for E-výuka codes with intelligent form detection and multi-level error generation.
 * 
 * This validator automatically detects the form type based on available evyuka fields and validates
 * that at least one required code is filled. Supports validation for:
 * - evyuka.subject.version (Kód verze předmětu)
 * - evyuka.subject (Kód předmětu)  
 * - evyuka.discipline (Kód studijního oboru)
 * - evyuka.programme (Kód studijního programu)
 * 
 * Form Type Detection:
 * - FULL_FORM: All 4 codes available - requires at least one from any group
 * - SUBJECT_ONLY: Only subject codes (2) - requires at least one subject field
 * - DISCIPLINE_ONLY: Only discipline codes (2) - requires at least one discipline field
 * - NO_EVYUKA_FIELDS: No evyuka fields - validation skipped
 * 
 * Error Generation Strategy:
 * - L4 Level: Individual field errors for each empty field (4 types max)
 * - L2 Level: Master step errors - double generation per form type (6 types total)
 *   * Primary L2: Context-aware message based on form type
 *   * Additional L2: Form-specific HTML description
 * 
 * No L3 HTML group errors are generated (removed per requirements).
 * 
 * Used by collections: 10084/139351 (subject-only), 10084/138949-138955 (full forms)
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class EvyukaCodesValidation extends AbstractValidation {

    // Error message constants for different form types
    private static final String ERROR_VALIDATION_EVYUKA_CODES_ALL_REQUIRED = "error.validation.evyuka.codes.all.required";
    private static final String ERROR_VALIDATION_EVYUKA_CODES_SUBJECT_ONLY_REQUIRED = "error.validation.evyuka.codes.subject.only.required";
    private static final String ERROR_VALIDATION_EVYUKA_CODES_DISCIPLINE_ONLY_REQUIRED = "error.validation.evyuka.codes.discipline.only.required";
    
    // The metadata fields for evyuka codes
    private static final String EVYUKA_SUBJECT_VERSION = "evyuka.subject.version";
    private static final String EVYUKA_SUBJECT = "evyuka.subject";
    private static final String EVYUKA_DISCIPLINE = "evyuka.discipline";
    private static final String EVYUKA_PROGRAMME = "evyuka.programme";
    
    // Form type enumeration
    private enum FormType {
        FULL_FORM,      // Both subject and discipline fields present
        SUBJECT_ONLY,   // Only subject fields present
        DISCIPLINE_ONLY, // Only discipline fields present
        NO_EVYUKA_FIELDS // No evyuka fields present
    }

    private DCInputsReader inputReader;
    private ItemService itemService;

    @Override
    public List<ErrorRest> validate(SubmissionService submissionService, InProgressSubmission obj,
                                    SubmissionStepConfig config) throws DCInputsReaderException, SQLException {

        List<ErrorRest> errors = new ArrayList<>();

        DCInputSet inputConfig = getInputReader().getInputsByFormName(config.getId());
        if (inputConfig == null) {
            return errors;
        }

        // Detect available evyuka fields in the form
        FieldAvailability fieldAvailability = detectEvyukaFields(inputConfig);
        FormType formType = determineFormType(fieldAvailability);
        
        // Skip validation if no evyuka fields are present
        if (formType == FormType.NO_EVYUKA_FIELDS) {
            return errors;
        }

        // Check which field groups have any values (for general validation)
        boolean hasAnySubjectValues = hasValuesInFields(obj, fieldAvailability.subjectFields);
        boolean hasAnyDisciplineValues = hasValuesInFields(obj, fieldAvailability.disciplineFields);
        
        // Check which field groups have ALL values filled (for complete validation)
        boolean hasAllSubjectValues = hasAllValuesInFields(obj, fieldAvailability.subjectFields);
        boolean hasAllDisciplineValues = hasAllValuesInFields(obj, fieldAvailability.disciplineFields);
        
        // For compatibility with existing code
        boolean hasSubjectCodes = hasAnySubjectValues; 
        boolean hasDisciplineCodes = hasAnyDisciplineValues;
        
        // Validate based on form type and field values
        if (!isValidationPassed(formType, hasAnySubjectValues, hasAnyDisciplineValues, hasAllSubjectValues, hasAllDisciplineValues)) {
            
            // Add individual field error messages
            addIndividualFieldErrors(errors, fieldAvailability, config, obj);
            
            // L3 HTML group errors removed per requirements
            
            // Add master validation errors at the beginning of the step (context-aware)
            String masterErrorCode = getErrorMessageCode(formType, hasSubjectCodes, hasDisciplineCodes);
            addError(errors, masterErrorCode,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId());
            
            // Add additional L2 error messages based on form type
            addAdditionalL2Errors(errors, config, formType, hasSubjectCodes, hasDisciplineCodes);
        }

        return errors;
    }
    
    /**
     * Detects which evyuka fields are available in the form configuration.
     */
    private FieldAvailability detectEvyukaFields(DCInputSet inputConfig) {
        FieldAvailability availability = new FieldAvailability();
        
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                String fieldName = input.getFieldName();
                
                // Check for subject fields (both validation groups)
                if (fieldName.equals(EVYUKA_SUBJECT_VERSION) || fieldName.equals(EVYUKA_SUBJECT)) {
                    availability.subjectFields.add(input);
                }
                
                // Check for discipline fields (both validation groups)  
                if (fieldName.equals(EVYUKA_DISCIPLINE) || fieldName.equals(EVYUKA_PROGRAMME)) {
                    availability.disciplineFields.add(input);
                }
            }
        }
        
        return availability;
    }
    
    /**
     * Determines the form type based on available fields.
     */
    private FormType determineFormType(FieldAvailability availability) {
        boolean hasSubjectFields = !availability.subjectFields.isEmpty();
        boolean hasDisciplineFields = !availability.disciplineFields.isEmpty();
        
        if (hasSubjectFields && hasDisciplineFields) {
            return FormType.FULL_FORM;
        } else if (hasSubjectFields) {
            return FormType.SUBJECT_ONLY;
        } else if (hasDisciplineFields) {
            return FormType.DISCIPLINE_ONLY;
        } else {
            return FormType.NO_EVYUKA_FIELDS;
        }
    }
    
    /**
     * Checks if any field in the given list has a non-empty value.
     */
    private boolean hasValuesInFields(InProgressSubmission obj, List<DCInput> fields) {
        for (DCInput field : fields) {
            List<MetadataValue> values = itemService.getMetadataByMetadataString(obj.getItem(), field.getFieldName());
            
            if (CollectionUtils.isNotEmpty(values)) {
                for (MetadataValue value : values) {
                    if (value != null && value.getValue() != null && !value.getValue().trim().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if all fields in the given list have non-empty values.
     */
    private boolean hasAllValuesInFields(InProgressSubmission obj, List<DCInput> fields) {
        for (DCInput field : fields) {
            if (!hasValueInField(obj, field)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Determines if validation passes based on form type and filled fields.
     */
    private boolean isValidationPassed(FormType formType, boolean hasAnySubjectValues, boolean hasAnyDisciplineValues, 
                                       boolean hasAllSubjectValues, boolean hasAllDisciplineValues) {
        switch (formType) {
            case FULL_FORM:
                // For full forms, at least one group must have any values
                return hasAnySubjectValues || hasAnyDisciplineValues;
            case SUBJECT_ONLY:
                // Subject-only forms: require at least one field filled
                return hasAnySubjectValues;
            case DISCIPLINE_ONLY:
                // Discipline-only forms: require at least one field filled
                return hasAnyDisciplineValues;
            default:
                return true; // No validation needed
        }
    }
    
    /**
     * Gets the appropriate error message code based on form type and field states.
     */
    private String getErrorMessageCode(FormType formType, boolean hasSubjectCodes, boolean hasDisciplineCodes) {
        switch (formType) {
            case FULL_FORM:
                return ERROR_VALIDATION_EVYUKA_CODES_ALL_REQUIRED;
            case SUBJECT_ONLY:
                return ERROR_VALIDATION_EVYUKA_CODES_SUBJECT_ONLY_REQUIRED;
            case DISCIPLINE_ONLY:
                return ERROR_VALIDATION_EVYUKA_CODES_DISCIPLINE_ONLY_REQUIRED;
            default:
                return ERROR_VALIDATION_EVYUKA_CODES_ALL_REQUIRED;
        }
    }
    
    /**
     * Gets all evyuka fields from the availability object.
     */
    private List<DCInput> getAllEvyukaFields(FieldAvailability availability) {
        List<DCInput> allFields = new ArrayList<>();
        allFields.addAll(availability.subjectFields);
        allFields.addAll(availability.disciplineFields);
        return allFields;
    }
    
    /**
     * Adds individual error messages for each empty field based on form type.
     */
    private void addIndividualFieldErrors(List<ErrorRest> errors, FieldAvailability fieldAvailability, 
                                        SubmissionStepConfig config, InProgressSubmission obj) {
        
        // Add errors for empty subject fields
        for (DCInput field : fieldAvailability.subjectFields) {
            if (!hasValueInField(obj, field)) {
                String fieldErrorCode = getIndividualFieldErrorCode(field.getFieldName());
                addError(errors, fieldErrorCode,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" + 
                        field.getFieldName());
            }
        }
        
        // Add errors for empty discipline fields
        for (DCInput field : fieldAvailability.disciplineFields) {
            if (!hasValueInField(obj, field)) {
                String fieldErrorCode = getIndividualFieldErrorCode(field.getFieldName());
                addError(errors, fieldErrorCode,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" + 
                        field.getFieldName());
            }
        }
    }
    
    /**
     * Adds group-specific error messages (HTML formatted for frontend).
     */
    private void addGroupSpecificErrors(List<ErrorRest> errors, FieldAvailability fieldAvailability, 
                                      SubmissionStepConfig config, InProgressSubmission obj, FormType formType,
                                      boolean hasSubjectCodes, boolean hasDisciplineCodes) {
        
        if (formType == FormType.FULL_FORM) {
            // For full forms with both groups available, add combined error message
            if (!hasSubjectCodes && !hasDisciplineCodes) {
                addError(errors, "error.validation.evyuka.combined.groups.required",
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/evyuka.combined.groups");
            }
        } else {
            // For forms with only one group, add individual group errors
            
            // Add subject group error if subject fields exist but are empty
            if (!fieldAvailability.subjectFields.isEmpty() && !hasSubjectCodes) {
                addError(errors, ERROR_VALIDATION_EVYUKA_CODES_SUBJECT_ONLY_REQUIRED,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/evyuka.subject.group");
            }
            
            // Add discipline group error if discipline fields exist but are empty
            if (!fieldAvailability.disciplineFields.isEmpty() && !hasDisciplineCodes) {
                addError(errors, ERROR_VALIDATION_EVYUKA_CODES_DISCIPLINE_ONLY_REQUIRED,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/evyuka.discipline.group");
            }
        }
    }
    
    /**
     * Adds additional L2 error messages based on form type.
     */
    private void addAdditionalL2Errors(List<ErrorRest> errors, SubmissionStepConfig config, FormType formType,
                                      boolean hasSubjectCodes, boolean hasDisciplineCodes) {
        String additionalErrorCode = null;
        
        switch (formType) {
            case FULL_FORM:
                if (!hasSubjectCodes && !hasDisciplineCodes) {
                    additionalErrorCode = "error.validation.evyuka.combined.groups.required";
                }
                break;
            case SUBJECT_ONLY:
                if (!hasSubjectCodes) {
                    additionalErrorCode = "error.validation.evyuka.subject.codes.required";
                }
                break;
            case DISCIPLINE_ONLY:
                if (!hasDisciplineCodes) {
                    additionalErrorCode = "error.validation.evyuka.discipline.programme.codes.required";
                }
                break;
        }
        
        if (additionalErrorCode != null) {
            addError(errors, additionalErrorCode,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId());
        }
    }

    /**
     * Gets individual field error code for specific field.
     */
    private String getIndividualFieldErrorCode(String fieldName) {
        switch (fieldName) {
            case EVYUKA_SUBJECT_VERSION:
                return "error.validation.evyuka.subject.version.required";
            case EVYUKA_SUBJECT:
                return "error.validation.evyuka.subject.required";
            case EVYUKA_DISCIPLINE:
                return "error.validation.evyuka.discipline.required";
            case EVYUKA_PROGRAMME:
                return "error.validation.evyuka.programme.required";
            default:
                throw new IllegalArgumentException("Unknown evyuka field: " + fieldName);
        }
    }
    
    /**
     * Checks if a single field has a non-empty value.
     */
    private boolean hasValueInField(InProgressSubmission obj, DCInput field) {
        List<MetadataValue> values = itemService.getMetadataByMetadataString(obj.getItem(), field.getFieldName());
        
        if (CollectionUtils.isNotEmpty(values)) {
            for (MetadataValue value : values) {
                if (value != null && value.getValue() != null && !value.getValue().trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Helper class to track available evyuka fields in the form.
     */
    private static class FieldAvailability {
        List<DCInput> subjectFields = new ArrayList<>();
        List<DCInput> disciplineFields = new ArrayList<>();
    }

    /**
     * Determines if this is an evyuka form based on the form name.
     * Evyuka forms typically have names like "e-vyuka-FAST", "e-vyuka-FBI", etc.
     * 
     * @param formName the name of the submission form
     * @return true if this is an evyuka form
     */
    private boolean isEvyukaForm(String formName) {
        return formName != null && formName.startsWith("e-vyuka");
    }

    public DCInputsReader getInputReader() {
        if (inputReader == null) {
            try {
                inputReader = new DCInputsReader();
            } catch (DCInputsReaderException e) {
                throw new IllegalStateException("Cannot initialize DCInputsReader", e);
            }
        }
        return inputReader;
    }

    public void setInputReader(DCInputsReader inputReader) {
        this.inputReader = inputReader;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }
}