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
 * Validates that at least one of the evyuka codes is filled in:
 * - evyuka.subject.version (Kód verze předmětu)
 * - evyuka.subject (Kód předmětu)
 * - evyuka.discipline (Kód studijního oboru)
 * - evyuka.programme (Kód studijního programu)
 * 
 * This validation ensures that for evyuka forms, at least one code must be provided
 * to properly categorize and link the document in the E-výuka portal.
 *
 * @author VSB-TUO DSpace Team
 */
public class EvyukaCodesValidation extends AbstractValidation {

    private static final String ERROR_VALIDATION_EVYUKA_CODES_REQUIRED = "error.validation.evyuka.codes.required";

    // The metadata fields for evyuka codes
    private static final String EVYUKA_SUBJECT_VERSION = "evyuka.subject.version";
    private static final String EVYUKA_SUBJECT = "evyuka.subject";
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

        // Find all fields that belong to the "evyuka-codes" validation group
        List<DCInput> evyukaCodeFields = new ArrayList<>();
        
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                if ("evyuka-codes".equals(input.getValidationGroup())) {
                    evyukaCodeFields.add(input);
                }
            }
        }

        // If no fields have evyuka-codes validation group, skip validation
        if (evyukaCodeFields.isEmpty()) {
            return errors;
        }

        boolean hasEvyukaCodes = false;
        List<String> fieldsWithCodes = new ArrayList<>();
        
        // Check if any of the evyuka code fields have values
        for (DCInput input : evyukaCodeFields) {
            String fieldName = input.getFieldName();
            List<MetadataValue> values = itemService.getMetadataByMetadataString(obj.getItem(), fieldName);
            
            if (CollectionUtils.isNotEmpty(values)) {
                // Check if any value is not empty
                for (MetadataValue value : values) {
                    if (value != null && value.getValue() != null && !value.getValue().trim().isEmpty()) {
                        hasEvyukaCodes = true;
                        fieldsWithCodes.add(fieldName);
                        break;
                    }
                }
            }
        }

        // If no evyuka codes are provided, add validation error for each field
        if (!hasEvyukaCodes) {
            for (DCInput input : evyukaCodeFields) {
                addError(errors, ERROR_VALIDATION_EVYUKA_CODES_REQUIRED,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" + 
                        input.getFieldName());
            }
        }

        return errors;
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