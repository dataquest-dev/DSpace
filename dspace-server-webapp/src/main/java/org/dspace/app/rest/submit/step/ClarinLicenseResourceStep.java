/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step;

import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.exception.ClarinLicenseNotFoundException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.step.ClarinDataLicenseRest;
import org.dspace.app.rest.submit.AbstractProcessingStep;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submission step exposing the CLARIN resource license selected for the
 * in-progress submission. Data is sourced from the item's {@code dc.rights*}
 * metadata; the selection is updated via a section-scoped patch
 * {@code /sections/clarin-license/select}.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinLicenseResourceStep extends AbstractProcessingStep {

    private static final Logger log = LoggerFactory.getLogger(ClarinLicenseResourceStep.class);

    /**
     * Sub-path of the section patch used to select a CLARIN license by name,
     * e.g. {@code /sections/clarin-license/select}.
     */
    public static final String LICENSE_SELECT_OPERATION_ENTRY = "select";

    @Override
    public ClarinDataLicenseRest getData(SubmissionService submissionService, InProgressSubmission obj,
            SubmissionStepConfig config) {
        ClarinDataLicenseRest result = new ClarinDataLicenseRest();
        Item item = obj.getItem();
        if (item == null) {
            return result;
        }

        List<MetadataValue> name = itemService.getMetadataByMetadataString(item, "dc.rights");
        List<MetadataValue> uri = itemService.getMetadataByMetadataString(item, "dc.rights.uri");
        List<MetadataValue> label = itemService.getMetadataByMetadataString(item, "dc.rights.label");

        if (CollectionUtils.isNotEmpty(name)) {
            result.setName(name.get(0).getValue());
        }
        if (CollectionUtils.isNotEmpty(uri)) {
            result.setDefinition(uri.get(0).getValue());
        }
        if (CollectionUtils.isNotEmpty(label)) {
            result.setLabel(label.get(0).getValue());
        }
        result.setGranted(CollectionUtils.isNotEmpty(name)
                && CollectionUtils.isNotEmpty(uri)
                && CollectionUtils.isNotEmpty(label));
        return result;
    }

    @Override
    public void doPatchProcessing(Context context, HttpServletRequest currentRequest, InProgressSubmission source,
            Operation op, SubmissionStepConfig stepConf) throws Exception {

        String path = op.getPath();

        if (path.endsWith("/" + LICENSE_SELECT_OPERATION_ENTRY)
                || path.endsWith("/" + stepConf.getId())) {
            String licenseName = extractLicenseName(op);
            try {
                ClarinLicenseSubmissionUtils.applyLicense(context, source.getItem(), licenseName);
            } catch (ClarinLicenseNotFoundException ex) {
                // Surface invalid client input as 422 instead of leaking as 500.
                throw new UnprocessableEntityException(ex.getMessage(), ex);
            }
            return;
        }

        if (path.endsWith(LICENSE_STEP_OPERATION_ENTRY)) {
            // `granted` patches are a no-op on this section; kept for older clients.
            log.info("Ignoring legacy '{}/granted' patch on the CLARIN license section.", stepConf.getId());
            return;
        }

        log.info("Ignoring unsupported patch path on CLARIN license section: {}", path);
    }

    private String extractLicenseName(Operation op) {
        Object value = op.getValue();
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof JsonValueEvaluator) {
            JsonNode valueNode = ((JsonValueEvaluator) value).getValueNode();
            if (valueNode == null) {
                return null;
            }
            JsonNode inner = valueNode.get("value");
            if (inner != null) {
                return inner.asText();
            }
            if (valueNode.isTextual()) {
                return valueNode.asText();
            }
        }
        return String.valueOf(value);
    }
}
