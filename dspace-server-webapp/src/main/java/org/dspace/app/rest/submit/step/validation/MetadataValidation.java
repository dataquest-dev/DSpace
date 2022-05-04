/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step.validation;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.ErrorRest;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.content.authority.service.MetadataAuthorityService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.storage.bitstore.service.BitstreamStorageService;

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

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataValidation.class);

    private DCInputsReader inputReader;

    private ItemService itemService;

    private MetadataAuthorityService metadataAuthorityService;

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private BitstreamFormatService bitstreamFormatService = ContentServiceFactory.getInstance().getBitstreamFormatService();

    @Override
    public List<ErrorRest> validate(SubmissionService submissionService, InProgressSubmission obj,
                                    SubmissionStepConfig config) throws DCInputsReaderException, SQLException {

        DCInputSet inputConfig = getInputReader().getInputsByFormName(config.getId());
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
                        validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey);
                        if (mdv.size() > 0 && input.isVisible(DCInput.SUBMISSION_SCOPE)) {
                            foundResult = true;
                        }
                    }
                    if (input.isRequired() && ! foundResult) {
                        // for this required qualdrop no value was found, add to the list of error fields
                        addError(ERROR_VALIDATION_REQUIRED,
                            "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                                input.getFieldName());
                    }

                } else {
                    fieldsName.add(input.getFieldName());
                }

                for (String fieldName : fieldsName) {
                    List<MetadataValue> mdv = itemService.getMetadataByMetadataString(obj.getItem(), fieldName);
                    validateMetadataValues(mdv, input, config, isAuthorityControlled, fieldKey);
                    if ((input.isRequired() && mdv.size() == 0) && input.isVisible(DCInput.SUBMISSION_SCOPE)) {
                        // since this field is missing add to list of error
                        // fields
                        addError(ERROR_VALIDATION_REQUIRED,
                            "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                                input.getFieldName());
                    }
                    if ("local.hasCMDI".equals(fieldName) && !mdv.isEmpty()) {
                        try {
                            maintainCMDIFileBundle(obj.getItem(), mdv.get(0));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return getErrors();
    }

    private void validateMetadataValues(List<MetadataValue> mdv, DCInput input, SubmissionStepConfig config,
                                        boolean isAuthorityControlled, String fieldKey) {
        for (MetadataValue md : mdv) {
            if (! (input.validate(md.getValue()))) {
                addError(ERROR_VALIDATION_REGEX,
                    "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() + "/" +
                        input.getFieldName() + "/" + md.getPlace());
            }
            if (isAuthorityControlled) {
                String authKey = md.getAuthority();
                if (metadataAuthorityService.isAuthorityRequired(fieldKey) &&
                    StringUtils.isBlank(authKey)) {
                    addError(ERROR_VALIDATION_AUTHORITY_REQUIRED,
                        "/" + WorkspaceItemRestRepository.OPERATION_PATH_SECTIONS + "/" + config.getId() +
                            "/" + input.getFieldName() + "/" + md.getPlace());
                }
            }
        }
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

    private void maintainCMDIFileBundle(Item item, MetadataValue mdv) throws SQLException, AuthorizeException, IOException {
        List<Bundle> bundleMETADATA = itemService.getBundles(item, Constants.METADATA_BUNDLE_NAME);
        List<Bundle> bundleORIGINAL = itemService.getBundles(item, Constants.CONTENT_BUNDLE_NAME);

        String targetBundle = "";
        List<Bundle> bundleToProcess = null;

        if (!bundleMETADATA.isEmpty() && !"yes".equals(mdv.getValue())) {
            targetBundle = Constants.CONTENT_BUNDLE_NAME;
            bundleToProcess = bundleMETADATA;
        } else if (!bundleORIGINAL.isEmpty() && "yes".equals(mdv.getValue())) {
            targetBundle = Constants.METADATA_BUNDLE_NAME;
            bundleToProcess = bundleORIGINAL;
        }

        for (Bundle bundle : CollectionUtils.emptyIfNull(bundleToProcess)) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                if (bitstream.getName().toLowerCase().endsWith(".cmdi")) {
                    Context context = ContextUtil.obtainCurrentRequestContext();

                    List<Bundle> targetBundles = itemService.getBundles(item, targetBundle);
                    InputStream inputStream = bitstreamService.retrieve(context, bitstream);

                    // Create a new Bitstream
                    Bitstream source = null;

                    if (targetBundles.size() < 1) {
                        source = itemService.createSingleBitstream(context, inputStream, item, targetBundle);
                    } else {
                        // we have a bundle already, just add bitstream
                        source = bitstreamService.create(context, targetBundles.get(0), inputStream);
                    }

                    source.setName(context, bitstream.getName());
                    source.setSource(context, bitstream.getSource());
                    source.setFormat(context, bitstream.getFormat(context));

                    // add the bitstream to the right bundle
                    bitstreamService.update(context, source);
                    itemService.update(context, item);

                    // remove the bitstream from the bundle where it shouldn't be
                    bitstreamService.delete(context, bitstream);
                }
            }
        }
    }
}
