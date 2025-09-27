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
 * Validates E-výuka discipline and programme codes using validation group-based field detection.
 *
 * This validator handles discipline/programme code validation for evyuka forms by detecting fields
 * with the "evyuka-discipline-programme-codes" validation group and ensuring at least one contains a value:
 * - evyuka.discipline (Kód studijního oboru)
 * - evyuka.programme (Kód studijního programu)
 *
 * Validation Strategy:
 * - Searches for fields with validation-group="evyuka-discipline-programme-codes"
 * - Requires at least one discipline/programme field to have a non-empty value
 * - Generates both step-level and field-level error messages
 *
 * Error Generation:
 * - Step-level error: General message for the entire submission step
 * - Field-level errors: Specific messages for each empty discipline/programme field
 *
 * Note: This is a legacy validator. The current implementation uses the master
 * EvyukaCodesValidation class with intelligent form type detection and multi-level error generation.
 *
 * Used by E-výuka collections that have discipline/programme code fields configured with validation groups.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class EvyukaDisciplineProgrammeCodesValidation extends AbstractValidation {

    // Error messages for individual fields
    private static final String ERROR_VALIDATION_EVYUKA_DISCIPLINE_REQUIRED =
            "error.validation.evyuka.discipline.required";
    private static final String ERROR_VALIDATION_EVYUKA_PROGRAMME_REQUIRED =
            "error.validation.evyuka.programme.required";

    // Error message when no discipline/programme codes are provided at all
    private static final String ERROR_VALIDATION_EVYUKA_DISCIPLINE_PROGRAMME_CODES_REQUIRED =
            "error.validation.evyuka.discipline.programme.codes.required";

    // The metadata fields for evyuka discipline/programme codes
    private static final String EVYUKA_DISCIPLINE = "evyuka.discipline";
    private static final String EVYUKA_PROGRAMME = "evyuka.programme";

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

        // Find all fields that belong to the "evyuka-discipline-programme-codes" validation group
        List<DCInput> evyukaDisciplineProgrammeFields = new ArrayList<>();

        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                if ("evyuka-discipline-programme-codes".equals(input.getValidationGroup())) {
                    evyukaDisciplineProgrammeFields.add(input);
                }
            }
        }

        // If no fields have evyuka-discipline-programme-codes validation group, skip validation
        if (evyukaDisciplineProgrammeFields.isEmpty()) {
            return errors;
        }

        boolean hasDisciplineCode = false;
        boolean hasProgrammeCode = false;
        boolean hasAnyDisciplineProgrammeCode = false;

        // Check if any of the evyuka discipline/programme code fields have values
        for (DCInput input : evyukaDisciplineProgrammeFields) {
            String fieldName = input.getFieldName();
            List<MetadataValue> values = itemService.getMetadataByMetadataString(obj.getItem(), fieldName);

            if (CollectionUtils.isNotEmpty(values)) {
                // Check if any value is not empty
                for (MetadataValue value : values) {
                    if (value != null && value.getValue() != null && !value.getValue().trim().isEmpty()) {
                        hasAnyDisciplineProgrammeCode = true;

                        if (EVYUKA_DISCIPLINE.equals(fieldName)) {
                            hasDisciplineCode = true;
                        } else if (EVYUKA_PROGRAMME.equals(fieldName)) {
                            hasProgrammeCode = true;
                        }
                        break;
                    }
                }
            }
        }

        // If no discipline/programme codes are provided at all, add general error for the step
        if (!hasAnyDisciplineProgrammeCode) {
            // Add general error message that will be displayed at the top of the step
            addError(errors, ERROR_VALIDATION_EVYUKA_DISCIPLINE_PROGRAMME_CODES_REQUIRED,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId());

            // Also add specific field errors for individual fields
            for (DCInput input : evyukaDisciplineProgrammeFields) {
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
        if (EVYUKA_DISCIPLINE.equals(fieldName)) {
            return ERROR_VALIDATION_EVYUKA_DISCIPLINE_REQUIRED;
        } else if (EVYUKA_PROGRAMME.equals(fieldName)) {
            return ERROR_VALIDATION_EVYUKA_PROGRAMME_REQUIRED;
        }
        return ERROR_VALIDATION_EVYUKA_DISCIPLINE_PROGRAMME_CODES_REQUIRED;
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
