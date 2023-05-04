/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step.validation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.service.MetadataAuthorityService;
import org.dspace.content.service.ItemService;
<<<<<<< HEAD
import org.dspace.core.Context;
=======
>>>>>>> dspace-7.5
import org.dspace.services.ConfigurationService;

/**
 * Execute three validation check on fields validation:
 * - mandatory metadata missing
 * - regex missing match
 * - authority required metadata missing
 *
 * @author Luigi Andrea Pascarelli (luigiandrea.pascarelli at 4science.it)
 */
public class MetadataValidation extends AbstractValidation {

    private static final String ERROR_VALIDATION_REQUIRED = "error.validation.required";

    private static final String ERROR_VALIDATION_AUTHORITY_REQUIRED = "error.validation.authority.required";

    private static final String ERROR_VALIDATION_REGEX = "error.validation.regex";

    private static final String LOCAL_METADATA_HAS_CMDI = "local.hasCMDI";

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataValidation.class);

    private DCInputsReader inputReader;

    private ItemService itemService;

    private MetadataAuthorityService metadataAuthorityService;

    private ConfigurationService configurationService;

    @Override
    public List<ErrorRest> validate(SubmissionService submissionService, InProgressSubmission obj,
                                    SubmissionStepConfig config) throws DCInputsReaderException, SQLException {

<<<<<<< HEAD
=======
        List<ErrorRest> errors = new ArrayList<>();
>>>>>>> dspace-7.5
        String documentTypeValue = "";
        DCInputSet inputConfig = getInputReader().getInputsByFormName(config.getId());
        List<MetadataValue> documentType = itemService.getMetadataByMetadataString(obj.getItem(),
                configurationService.getProperty("submit.type-bind.field", "dc.type"));
        if (documentType.size() > 0) {
            documentTypeValue = documentType.get(0).getValue();
        }
<<<<<<< HEAD
=======

        // Get list of all field names (including qualdrop names) allowed for this dc.type
        List<String> allowedFieldNames = inputConfig.populateAllowedFieldNames(documentTypeValue);

        // Begin the actual validation loop
>>>>>>> dspace-7.5
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                String fieldKey =
                    metadataAuthorityService.makeFieldKey(input.getSchema(), input.getElement(), input.getQualifier());
                boolean isAuthorityControlled = metadataAuthorityService.isAuthorityControlled(fieldKey);

                List<String> fieldsName = new ArrayList<String>();

                if (input.isQualdropValue()) {
                    boolean foundResult = false;
                    List<Object> inputPairs = input.getPairs();
                    //starting from the second element of the list and skipping one every time because the display
                    // values are also in the list and before the stored values.
                    for (int i = 1; i < inputPairs.size(); i += 2) {
                        String fullFieldname = input.getFieldName() + "." + (String) inputPairs.get(i);
                        List<MetadataValue> mdv = itemService.getMetadataByMetadataString(obj.getItem(), fullFieldname);
<<<<<<< HEAD
                        // If the input is not allowed for this type, strip it from item metadata.
                        if (!input.isAllowedFor(documentTypeValue)) {
                            itemService.removeMetadataValues(ContextUtil.obtainCurrentRequestContext(),
                                    obj.getItem(), mdv);
                        } else {
                            validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey);
=======

                        // Check the lookup list. If no other inputs of the same field name allow this type,
                        // then remove. This includes field name without qualifier.
                        if (!input.isAllowedFor(documentTypeValue) &&  (!allowedFieldNames.contains(fullFieldname)
                                && !allowedFieldNames.contains(input.getFieldName()))) {
                            itemService.removeMetadataValues(ContextUtil.obtainCurrentRequestContext(),
                                        obj.getItem(), mdv);
                        } else {
                            validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey, errors);
>>>>>>> dspace-7.5
                            if (mdv.size() > 0 && input.isVisible(DCInput.SUBMISSION_SCOPE)) {
                                foundResult = true;
                            }
                        }
                    }
<<<<<<< HEAD
                    // If the input is required but not allowed for this type, and we removed, don't throw
                    // an error - this way, a field can be required for "Book" to which it is bound, but not
                    // other types. A user may have switched between types before a final deposit
                    if (input.isRequired() && !foundResult && input.isAllowedFor(documentTypeValue)) {
=======
                    if (input.isRequired() && !foundResult) {
>>>>>>> dspace-7.5
                        // for this required qualdrop no value was found, add to the list of error fields
                        addError(errors, ERROR_VALIDATION_REQUIRED,
                                "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                                        input.getFieldName());
                    }
                } else {
                    fieldsName.add(input.getFieldName());
                }

                for (String fieldName : fieldsName) {
                    boolean valuesRemoved = false;
                    List<MetadataValue> mdv = itemService.getMetadataByMetadataString(obj.getItem(), fieldName);
                    if (!input.isAllowedFor(documentTypeValue)) {
<<<<<<< HEAD
                        itemService.removeMetadataValues(ContextUtil.obtainCurrentRequestContext(), obj.getItem(), mdv);
                        // Continue here, this skips the required check since we've just removed values that previously
                        // appeared, and the configuration already indicates this field shouldn't be included
                        continue;
                    }
                    validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey);
                    if (input.isRequired() && input.isVisible(DCInput.SUBMISSION_SCOPE) &&
                            (mdv.size() == 0 || !isValidComplexDefinitionMetadata(input, mdv))) {
                        // since this field is missing add to list of error
                        // fields
                        addError(ERROR_VALIDATION_REQUIRED,
                            "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                                input.getFieldName());
=======
                        // Check the lookup list. If no other inputs of the same field name allow this type,
                        // then remove. Otherwise, do not
                        if (!(allowedFieldNames.contains(fieldName))) {
                            itemService.removeMetadataValues(ContextUtil.obtainCurrentRequestContext(),
                                    obj.getItem(), mdv);
                            valuesRemoved = true;
                            log.debug("Stripping metadata values for " + input.getFieldName() + " on type "
                                    + documentTypeValue + " as it is allowed by another input of the same field " +
                                    "name");
                        } else {
                            log.debug("Not removing unallowed metadata values for " + input.getFieldName() + " on type "
                                    + documentTypeValue + " as it is allowed by another input of the same field " +
                                    "name");
                        }
                    }
                    validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey, errors);
                    if ((input.isRequired() && mdv.size() == 0) && input.isVisible(DCInput.SUBMISSION_SCOPE)
                                                                && !valuesRemoved) {
                        // Is the input required for *this* type? In other words, are we looking at a required
                        // input that is also allowed for this document type
                        if (input.isAllowedFor(documentTypeValue)) {
                            // since this field is missing add to list of error
                            // fields
                            addError(errors, ERROR_VALIDATION_REQUIRED, "/"
                                    + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                                            input.getFieldName());
                        }
>>>>>>> dspace-7.5
                    }
                    if (LOCAL_METADATA_HAS_CMDI.equals(fieldName)) {
                        try {
                            Context context = ContextUtil.obtainCurrentRequestContext();
                            CMDIFileBundleMaintainer.updateCMDIFileBundle(context, obj.getItem(), mdv);
                        } catch (AuthorizeException | IOException exception) {
                            log.error("Cannot update CMDI file bundle (ORIGINAL/METADATA) because: " +
                                    exception.getMessage());
                        }
                    }
                }
            }
        }
        return errors;
    }

<<<<<<< HEAD
    private boolean isValidComplexDefinitionMetadata(DCInput input, List<MetadataValue> mdv) {
        if (input.getInputType().equals("complex")) {
            int complexDefinitionIndex = 0;
            Map<String, Map<String, String>> complexDefinitionInputs = input.getComplexDefinition().getInputs();
            for (String complexDefinitionInputName : complexDefinitionInputs.keySet()) {
                Map<String, String> complexDefinitionInputValues =
                        complexDefinitionInputs.get(complexDefinitionInputName);

                List<String> filledInputValues = null;
                String isRequired = complexDefinitionInputValues.get("required");
                if (StringUtils.equals(BooleanUtils.toStringTrueFalse(true), isRequired)) {
                    filledInputValues = new ArrayList<>(Arrays.asList(
                            mdv.get(0).getValue().split(DCInput.ComplexDefinitions.getSeparator(),-1)));

                    if (StringUtils.isBlank(filledInputValues.get(complexDefinitionIndex))) {
                        return false;
                    }
                }
                complexDefinitionIndex++;
            }
        }
        return true;
    }
=======
>>>>>>> dspace-7.5

    private void validateMetadataValues(List<MetadataValue> mdv, DCInput input, SubmissionStepConfig config,
                                        boolean isAuthorityControlled, String fieldKey,
                                        List<ErrorRest> errors) {
        for (MetadataValue md : mdv) {
            if (! (input.validate(md.getValue()))) {
                addError(errors, ERROR_VALIDATION_REGEX,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                        input.getFieldName() + "/" + md.getPlace());
            }
            if (isAuthorityControlled) {
                String authKey = md.getAuthority();
                if (metadataAuthorityService.isAuthorityRequired(fieldKey) &&
                    StringUtils.isBlank(authKey)) {
                    addError(errors, ERROR_VALIDATION_AUTHORITY_REQUIRED,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() +
                            "/" + input.getFieldName() + "/" + md.getPlace());
                }
            }
        }
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }

    public void setMetadataAuthorityService(MetadataAuthorityService metadataAuthorityService) {
        this.metadataAuthorityService = metadataAuthorityService;
    }

    public DCInputsReader getInputReader() {
        if (inputReader == null) {
            try {
                inputReader = new DCInputsReader();
            } catch (DCInputsReaderException e) {
                log.error(e.getMessage(), e);
            }
        }
        return inputReader;
    }

    public void setInputReader(DCInputsReader inputReader) {
        this.inputReader = inputReader;
    }

}
