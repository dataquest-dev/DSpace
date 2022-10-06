package org.dspace.app.rest.submit.step.validation;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

public class ClarinLicenseResourceValidation extends AbstractValidation {
    private static final String ERROR_VALIDATION_CLARIN_LICENSE_GRANTED = "error.validation.clarin-license.notgranted";
    private static final String ERROR_VALIDATION_RESOURCE_CLARIN_LICENSE_REQUIRED =
            "error.validation.clarin-license.resource.required";

    @Autowired
    ItemService itemService;

    @Override
    public List<? extends ErrorRest> validate(SubmissionService submissionService, InProgressSubmission obj, SubmissionStepConfig config) throws DCInputsReaderException, SQLException {
        Item item = obj.getItem();
        List<MetadataValue> licenseDefinition = itemService.getMetadataByMetadataString(item, "dc.rights.uri");
        List<MetadataValue> licenseName = itemService.getMetadataByMetadataString(item, "dc.rights");
        List<MetadataValue> licenseLabel = itemService.getMetadataByMetadataString(item, "dc.rights.label");

        if (CollectionUtils.isEmpty(licenseDefinition) || CollectionUtils.isEmpty(licenseName) ||
            CollectionUtils.isEmpty(licenseLabel)) {
            addError(ERROR_VALIDATION_CLARIN_LICENSE_GRANTED,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/"
                            + config.getId());
        }


        return getErrors();
    }

    public ItemService getItemService() {
        return itemService;
    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }
}
