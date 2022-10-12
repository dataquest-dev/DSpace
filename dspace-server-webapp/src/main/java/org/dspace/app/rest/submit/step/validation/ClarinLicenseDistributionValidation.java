package org.dspace.app.rest.submit.step.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.Bitstream;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Constants;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

public class ClarinLicenseDistributionValidation extends AbstractValidation {
    private static final String ERROR_VALIDATION_LICENSEREQUIRED = "error.validation.license.notgranted";

    private static final Logger log = LogManager.getLogger();

    @Autowired
    private BitstreamService bitstreamService;

    @Override
    public List<ErrorRest> validate(SubmissionService submissionService, InProgressSubmission obj,
                                    SubmissionStepConfig config) throws DCInputsReaderException, SQLException {

        this.getErrors().clear();
        Bitstream bitstream = bitstreamService
                .getBitstreamByName(obj.getItem(), Constants.LICENSE_BUNDLE_NAME, Constants.LICENSE_BITSTREAM_NAME);
        if (bitstream == null) {
            addError(ERROR_VALIDATION_LICENSEREQUIRED,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId());
        }
        return getErrors();
    }

    public BitstreamService getBitstreamService() {
        return bitstreamService;
    }

    public void setBitstreamService(BitstreamService bitstreamService) {
        this.bitstreamService = bitstreamService;
    }
}
