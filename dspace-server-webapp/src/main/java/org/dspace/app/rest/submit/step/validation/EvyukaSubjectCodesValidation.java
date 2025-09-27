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
 * Validates E-výuka subject codes using validation group-based field detection.
 * 
 * This validator handles subject code validation for evyuka forms by detecting fields 
 * with the "evyuka-subject-codes" validation group and ensuring at least one contains a value:
 * - evyuka.subject.version (Kód verze předmětu)
 * - evyuka.subject (Kód předmětu)
 * 
 * Validation Strategy:
 * - Searches for fields with validation-group="evyuka-subject-codes"
 * - Requires at least one subject field to have a non-empty value
 * - Generates both step-level and field-level error messages
 * 
 * Error Generation:
 * - Step-level error: General message for the entire submission step
 * - Field-level errors: Specific messages for each empty subject field
 * 
 * Note: This is a legacy validator. The current implementation uses the master
 * EvyukaCodesValidation class with intelligent form type detection.
 * 
 * Used by E-výuka collections that have subject code fields configured with validation groups.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class EvyukaSubjectCodesValidation extends AbstractValidation {

    // Error messages for individual fields
    private static final String ERROR_VALIDATION_EVYUKA_SUBJECT_VERSION_REQUIRED = "error.validation.evyuka.subject.version.required";
    private static final String ERROR_VALIDATION_EVYUKA_SUBJECT_REQUIRED = "error.validation.evyuka.subject.required";
    
    // Error message when no subject codes are provided at all
    private static final String ERROR_VALIDATION_EVYUKA_SUBJECT_CODES_REQUIRED = "error.validation.evyuka.subject.codes.required";

    // The metadata fields for evyuka subject codes
    private static final String EVYUKA_SUBJECT_VERSION = "evyuka.subject.version";
    private static final String EVYUKA_SUBJECT = "evyuka.subject";

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

        // Find all fields that belong to the "evyuka-subject-codes" validation group
        List<DCInput> evyukaSubjectFields = new ArrayList<>();
        
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                if ("evyuka-subject-codes".equals(input.getValidationGroup())) {
                    evyukaSubjectFields.add(input);
                }
            }
        }

        // If no fields have evyuka-subject-codes validation group, skip validation
        if (evyukaSubjectFields.isEmpty()) {
            return errors;
        }

        boolean hasSubjectVersionCode = false;
        boolean hasSubjectCode = false;
        boolean hasAnySubjectCode = false;
        
        // Check if any of the evyuka subject code fields have values
        for (DCInput input : evyukaSubjectFields) {
            String fieldName = input.getFieldName();
            List<MetadataValue> values = itemService.getMetadataByMetadataString(obj.getItem(), fieldName);
            
            if (CollectionUtils.isNotEmpty(values)) {
                // Check if any value is not empty
                for (MetadataValue value : values) {
                    if (value != null && value.getValue() != null && !value.getValue().trim().isEmpty()) {
                        hasAnySubjectCode = true;
                        
                        if (EVYUKA_SUBJECT_VERSION.equals(fieldName)) {
                            hasSubjectVersionCode = true;
                        } else if (EVYUKA_SUBJECT.equals(fieldName)) {
                            hasSubjectCode = true;
                        }
                        break;
                    }
                }
            }
        }

        // If no subject codes are provided at all, add general error for the step
        if (!hasAnySubjectCode) {
            // Add general error message that will be displayed at the top of the step
            addError(errors, ERROR_VALIDATION_EVYUKA_SUBJECT_CODES_REQUIRED,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId());
            
            // Also add specific field errors for individual fields
            for (DCInput input : evyukaSubjectFields) {
                String fieldName = input.getFieldName();
                String errorKey = getFieldSpecificErrorKey(fieldName);
                addError(errors, errorKey,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" + 
                        fieldName);
            }
        }

        return errors;
    }

    /**
     * Get field-specific error key based on the field name
     */
    private String getFieldSpecificErrorKey(String fieldName) {
        if (EVYUKA_SUBJECT_VERSION.equals(fieldName)) {
            return ERROR_VALIDATION_EVYUKA_SUBJECT_VERSION_REQUIRED;
        } else if (EVYUKA_SUBJECT.equals(fieldName)) {
            return ERROR_VALIDATION_EVYUKA_SUBJECT_REQUIRED;
        }
        return ERROR_VALIDATION_EVYUKA_SUBJECT_CODES_REQUIRED;
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