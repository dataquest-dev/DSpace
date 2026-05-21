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
import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.step.DataClarinLicense;
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
 * CLARIN license resource step for DSpace Spring REST.
 * <p>
 * Exposes the CLARIN license currently selected for the in-progress
 * submission (sourced from the item's {@code dc.rights*} metadata) and
 * accepts section-scoped patches under
 * {@code /sections/clarin-license/name} to update that selection.
 * <p>
 * This step is intentionally <strong>not</strong> a clone of
 * {@code LicenseStep} any more — the CLARIN license has nothing to do with
 * the distribution license stored in {@code LICENSE/license.txt}.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinLicenseResourceStep extends AbstractProcessingStep {

    private static final Logger log = LoggerFactory.getLogger(ClarinLicenseResourceStep.class);

    /**
     * Section patch path entry used to set the selected CLARIN license name,
     * e.g. {@code /sections/clarin-license/name}.
     */
    public static final String CLARIN_LICENSE_NAME_OPERATION_ENTRY = "name";

    @Override
    public DataClarinLicense getData(SubmissionService submissionService, InProgressSubmission obj,
            SubmissionStepConfig config) {
        DataClarinLicense result = new DataClarinLicense();
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

        if (path.endsWith("/" + CLARIN_LICENSE_NAME_OPERATION_ENTRY)
                || path.endsWith("/" + stepConf.getId())) {
            String licenseName = extractLicenseName(op);
            ClarinLicenseSubmissionUtils.applyLicense(context, source.getItem(), licenseName);
            return;
        }

        if (path.endsWith(LICENSE_STEP_OPERATION_ENTRY)) {
            // The CLARIN license section is no longer backed by the deposit
            // license bitstream, therefore a `granted` patch on this section
            // is a no-op kept only for backward compatibility with older
            // submission clients.
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
