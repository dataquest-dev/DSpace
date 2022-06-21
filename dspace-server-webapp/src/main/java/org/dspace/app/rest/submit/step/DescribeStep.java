/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.submit.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mchange.lang.IntegerUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.Integers;
import org.apache.logging.log4j.core.util.JsonUtils;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.model.patch.AddOperation;
import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.RemoveOperation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.model.step.DataDescribe;
import org.dspace.app.rest.submit.AbstractProcessingStep;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.submit.factory.PatchOperationFactory;
import org.dspace.app.rest.submit.factory.impl.PatchOperation;
import org.dspace.app.util.DCInput;
import org.dspace.app.util.DCInputSet;
import org.dspace.app.util.DCInputsReader;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.app.util.SubmissionStepConfig;
import org.dspace.content.InProgressSubmission;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.springframework.util.ObjectUtils;
import org.w3c.dom.Text;

/**
 * Describe step for DSpace Spring Rest. Expose and allow patching of the in progress submission metadata. It is
 * configured via the config/submission-forms.xml file
 *
 * @author Luigi Andrea Pascarelli (luigiandrea.pascarelli at 4science.it)
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
public class DescribeStep extends AbstractProcessingStep {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(DescribeStep.class);

    private DCInputsReader inputReader;

    public DescribeStep() throws DCInputsReaderException {
        inputReader = new DCInputsReader();
    }

    @Override
    public DataDescribe getData(SubmissionService submissionService, InProgressSubmission obj,
            SubmissionStepConfig config) {
        DataDescribe data = new DataDescribe();
        try {
            DCInputSet inputConfig = inputReader.getInputsByFormName(config.getId());
            readField(obj, config, data, inputConfig);
        } catch (DCInputsReaderException e) {
            log.error(e.getMessage(), e);
        }
        return data;
    }

    private void readField(InProgressSubmission obj, SubmissionStepConfig config, DataDescribe data,
                           DCInputSet inputConfig) throws DCInputsReaderException {
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {

                List<String> fieldsName = new ArrayList<String>();
                if (input.isQualdropValue()) {
                    for (Object qualifier : input.getPairs()) {
                        fieldsName.add(input.getFieldName() + "." + (String) qualifier);
                    }
                } else {
                    fieldsName.add(input.getFieldName());
                }


                for (String fieldName : fieldsName) {
                    List<MetadataValue> mdv = itemService.getMetadataByMetadataString(obj.getItem(),
                                                                                      fieldName);
                    for (MetadataValue md : mdv) {
                        MetadataValueRest dto = new MetadataValueRest();
                        dto.setAuthority(md.getAuthority());
                        dto.setConfidence(md.getConfidence());
                        dto.setLanguage(md.getLanguage());
                        dto.setPlace(md.getPlace());
                        dto.setValue(md.getValue());

                        String[] metadataToCheck = Utils.tokenize(md.getMetadataField().toString());
                        if (data.getMetadata().containsKey(
                            Utils.standardize(metadataToCheck[0], metadataToCheck[1], metadataToCheck[2], "."))) {
                            data.getMetadata()
                                .get(Utils.standardize(md.getMetadataField().getMetadataSchema().getName(),
                                                       md.getMetadataField().getElement(),
                                                       md.getMetadataField().getQualifier(),
                                                       "."))
                                .add(dto);
                        } else {
                            List<MetadataValueRest> listDto = new ArrayList<>();
                            listDto.add(dto);
                            data.getMetadata()
                                .put(Utils.standardize(md.getMetadataField().getMetadataSchema().getName(),
                                                       md.getMetadataField().getElement(),
                                                       md.getMetadataField().getQualifier(),
                                                       "."), listDto);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void doPatchProcessing(Context context, HttpServletRequest currentRequest, InProgressSubmission source,
            Operation op, SubmissionStepConfig stepConf) throws Exception {

        String[] pathParts = op.getPath().substring(1).split("/");
        DCInputSet inputConfig = inputReader.getInputsByFormName(stepConf.getId());
        if ("remove".equals(op.getOp()) && pathParts.length < 3) {
            // manage delete all step fields
            String[] path = op.getPath().substring(1).split("/", 3);
            String configId = path[1];
            List<String> fieldsName = getInputFieldsName(inputConfig, configId);
            for (String fieldName : fieldsName) {
                String fieldPath = op.getPath() + "/" + fieldName;
                Operation fieldRemoveOp = new RemoveOperation(fieldPath);
                PatchOperation<MetadataValueRest> patchOperation = new PatchOperationFactory()
                     .instanceOf(DESCRIBE_STEP_METADATA_OPERATION_ENTRY, fieldRemoveOp.getOp());
                patchOperation.perform(context, currentRequest, source, fieldRemoveOp);
            }
        } else {
            PatchOperation<MetadataValueRest> patchOperation = new PatchOperationFactory()
                        .instanceOf(DESCRIBE_STEP_METADATA_OPERATION_ENTRY, op.getOp());
            String[] split = patchOperation.getAbsolutePath(op.getPath()).split("/");
            if (inputConfig.isFieldPresent(split[0])) {
                patchOperation.perform(context, currentRequest, source, op);
                String mappedToIfNotDefault = this.getMappedToIfNotDefault(split[0], inputConfig);
                // if the complex input field contains `mapped-to-if-not-default` definition
                // put additional data to the defined metadata from the `mapped-to-if-not-default`
                if (!StringUtils.isBlank(mappedToIfNotDefault)) {
                    Operation newOp = this.getOperationWithChangedMetadataField(op, mappedToIfNotDefault, source);
                    if (!ObjectUtils.isEmpty(newOp)) {
                        patchOperation.perform(context, currentRequest, source, newOp);
                    }
                }
            } else {
                throw new UnprocessableEntityException("The field " + split[0] + " is not present in section "
                                                                                   + inputConfig.getFormName());
            }
        }
    }

    private List<String> getInputFieldsName(DCInputSet inputConfig, String configId) throws DCInputsReaderException {
        List<String> fieldsName = new ArrayList<String>();
        for (DCInput[] row : inputConfig.getFields()) {
            for (DCInput input : row) {
                if (input.isQualdropValue()) {
                    for (Object qualifier : input.getPairs()) {
                        fieldsName.add(input.getFieldName() + "." + (String) qualifier);
                    }
                } else if (StringUtils.equalsIgnoreCase(input.getInputType(), "group") ||
                        StringUtils.equalsIgnoreCase(input.getInputType(), "inline-group")) {
                    log.info("Called child form:" + configId + "-" +
                        Utils.standardize(input.getSchema(), input.getElement(), input.getQualifier(), "-"));
                    DCInputSet inputConfigChild = inputReader.getInputsByFormName(configId + "-" + Utils
                        .standardize(input.getSchema(), input.getElement(), input.getQualifier(), "-"));
                    fieldsName.addAll(getInputFieldsName(inputConfigChild, configId));
                } else {
                    fieldsName.add(input.getFieldName());
                }
            }
        }
        return fieldsName;
    }

    /**
     *
     * @param oldOp old operation from the FE
     * @param mappedToIfNotDefault metadata where will be stored data from FE request
     * @return a new operation which is created from the old one but the metadata is changed
     */
    private Operation getOperationWithChangedMetadataField(Operation oldOp, String mappedToIfNotDefault,
                                                           InProgressSubmission source) {
        String[] oldOpPathArray = oldOp.getPath().split("/");
        String[] opPathArray = oldOpPathArray.clone();

        // change the metadata (e.g. `local.sponsor`) in the end of the path
        if (NumberUtils.isCreatable(opPathArray[opPathArray.length-1])) {
            // e.g. `traditional/section/local.sponsor/0`
            opPathArray[opPathArray.length-2] = mappedToIfNotDefault;
        } else {
            // e.g. `traditional/section/local.sponsor`
            opPathArray[opPathArray.length-1] = mappedToIfNotDefault;
        }

        // load the value of the input field from the old operation
        String oldOpValue = "";
        JsonNode jsonNodeValue = null;

        JsonValueEvaluator jsonValEvaluator = (JsonValueEvaluator) oldOp.getValue();
        Iterator<JsonNode> jsonNodes = jsonValEvaluator.getValueNode().elements();

        for (Iterator<JsonNode> it = jsonNodes; it.hasNext(); ) {
            JsonNode jsonNode = it.next();
            if (jsonNode instanceof ObjectNode) {
                jsonNodeValue = jsonNode.get("value");
            } else {
                jsonNodeValue = jsonValEvaluator.getValueNode().get("value");
            }
        }

        if (ObjectUtils.isEmpty(jsonNodeValue) || StringUtils.isBlank(jsonNodeValue.asText())) {
            throw new UnprocessableEntityException("Cannot load JsonNode value from the operation: " +
                    oldOp.getPath());
        }
        oldOpValue = jsonNodeValue.asText();

        // add the value from the old operation to the new operation
        String opValue = "";
        if (StringUtils.equals("local.sponsor", oldOpPathArray[oldOpPathArray.length-1]) ||
            StringUtils.equals("local.sponsor", oldOpPathArray[oldOpPathArray.length-2])) {
            // for the metadata `local.sponsor` change the `info:eu-repo...` value from the old value

            // load info:eu-repo* from the jsonNodeValue
            // the eu info is on the 4th index of the complexInputType
            List<String> complexInputValue = Arrays.asList(oldOpValue.split(";"));
            if (complexInputValue.size() > 4) {
                String euIdentifier = complexInputValue.get(4);
                // remove last value from the eu identifier - it should be in the metadata value
                List<String> euIdentifierSplit = new ArrayList<>(Arrays.asList(euIdentifier.split("/")));
                if (euIdentifierSplit.size() == 6) {
                    euIdentifierSplit.remove(5);
                }

                euIdentifier = String.join("/", euIdentifierSplit);
                opValue = euIdentifier;
            } else {
                // the `local.sponsor` is updating but without `info:eu-repo`. The `dc.relation` must be in the
                // eu info format.
                return null;
            }
        }

        // the opValue wasn't updated
        if (StringUtils.isBlank(opValue)) {
            // just copy old value to the new operation
            opValue = oldOpValue;
        }

        // create a new operation and add the new value there
        JsonNodeFactory js = new JsonNodeFactory(false);
        ArrayNode an = new ArrayNode(js);
        an.add(js.textNode(opValue));

        ObjectNode on = new ObjectNode(js);
        on.set("value", js.textNode(opValue));

        Operation newOp = null;
        String opPath = String.join("/", opPathArray);
        if (oldOp.getOp().equals("replace")) {
            List<MetadataValue> metadataByMetadataString = itemService.getMetadataByMetadataString(source.getItem(),
                    mappedToIfNotDefault);
            if (metadataByMetadataString.isEmpty()) { return null; }

            newOp = new ReplaceOperation(opPath, new JsonValueEvaluator(new ObjectMapper(), on));
        } else {
            newOp = new AddOperation(opPath, new JsonValueEvaluator(new ObjectMapper(), an));
        }

        return newOp;
    }

    /**
     * Load the `mapped-to-if-not-default` from the complex input field definition
     * @param complexDefinition
     * @return NULL - the `mapped-to-if-not-default` is not defined OR value - the mapped-to-if-not-default is defined
     */
    private String loadMappedToIfNotDefaultFromComplex(DCInput.ComplexDefinition complexDefinition) {
        Map<String, Map<String, String>> inputs = complexDefinition.getInputs();
        for (String inputName : inputs.keySet()) {
            Map<String, String> inputDefinition = inputs.get(inputName);
            for (String inputDefinitionValue : inputDefinition.keySet()) {
                if (StringUtils.equals(inputDefinitionValue, "mapped-to-if-not-default")) {
                    return inputDefinition.get(inputDefinitionValue);
                }
            }
        }
        return null;
    }

    /**
     * From the input configuration load the `mapped-to-if-not-default` definition in the complex input field
     * definitions
     * @param inputFieldMetadata the metadata where should be stored data from the FE request
     * @param inputConfig current input fields configuratino
     * @return NULL - the `mapped-to-if-not-default` is not defined OR value - the mapped-to-if-not-default is defined
     */
    private String getMappedToIfNotDefault(String inputFieldMetadata, DCInputSet inputConfig) {
        List<DCInput[]> inputsListOfList = Arrays.asList(inputConfig.getFields());
        for (DCInput[] inputsList : inputsListOfList) {
            List<DCInput> inputs = Arrays.asList(inputsList);
            for (DCInput input : inputs) {
                if (!StringUtils.equals("complex", input.getInputType())) { break; }

                String[] metadataFieldName = inputFieldMetadata.split("\\.");
                if (!StringUtils.equals(metadataFieldName[0], input.getSchema()) ||
                        !StringUtils.equals(metadataFieldName[1], input.getElement()) ||
                        (metadataFieldName.length > 2 &&
                                !StringUtils.equals(metadataFieldName[2], input.getQualifier()))) {
                    break;
                }

                String mappedToIfNotDefault = this.loadMappedToIfNotDefaultFromComplex(input.getComplexDefinition());
                if (StringUtils.isNotBlank(mappedToIfNotDefault)) {
                    return mappedToIfNotDefault;
                }
            }
        }
        return null;
    }
}
