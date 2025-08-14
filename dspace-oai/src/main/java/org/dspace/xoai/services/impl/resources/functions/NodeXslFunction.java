/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.services.impl.resources.functions;

import static org.dspace.xoai.services.impl.resources.functions.StringXSLFunction.BASE;

import java.util.Objects;
import javax.xml.transform.dom.DOMSource;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;
import org.dspace.xoai.services.impl.resources.SharedSaxonProcessor;
import org.w3c.dom.Node;


/**
 * Serves as proxy for call from XSL engine.
 * @author Marian Berger (marian.berger at dataquest.sk)
 */
public abstract class NodeXslFunction implements ExtensionFunction {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(NodeXslFunction.class);

<<<<<<< Updated upstream
    // Static processor and document builder to ensure configuration compatibility
    private static final Processor SHARED_PROCESSOR = new Processor(false);
    private static final DocumentBuilder SHARED_DOCUMENT_BUILDER = SHARED_PROCESSOR.newDocumentBuilder();

=======
>>>>>>> Stashed changes
    protected abstract String getFnName();

    protected abstract Node getNode(String param);

    @Override
    final public QName getName() {
        return new QName(BASE, getFnName());
    }

    @Override
    final public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.ANY_NODE, OccurrenceIndicator.ZERO_OR_MORE);
    }

    @Override
    final public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
                SequenceType.makeSequenceType(
                        ItemType.STRING, OccurrenceIndicator.ZERO_OR_MORE)};
    }

    @Override
    final public XdmValue call(XdmValue[] xdmValues) throws SaxonApiException {
        if (Objects.isNull(xdmValues) || Arrays.isNullOrContainsNull(xdmValues)) {
            log.debug("Null or empty parameters passed to {}, returning empty sequence", getFnName());
            return XdmEmptySequence.getInstance();
        }

        String val;
        try {
            val = xdmValues[0].itemAt(0).getStringValue();
        } catch (Exception e) {
            // e.g. when no parameter is passed and xdmValues[0] ends with index error
            log.warn("Empty value in call of function {}, returning empty sequence", getFnName());
            return XdmEmptySequence.getInstance();
        }

<<<<<<< Updated upstream
        Node node = getNode(val);
        if (Objects.isNull(node)) {
            try {
                node = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                log.error("Error creating new document in NodeXslFunction", e);
=======
        try {
            Node node = getNode(val);
            if (Objects.isNull(node)) {
                log.debug("Function {} returned null node for parameter '{}', returning empty sequence", getFnName(), val);
                return XdmEmptySequence.getInstance();
>>>>>>> Stashed changes
            }

            // Use the shared document builder to ensure configuration compatibility
            // and wrap in try-catch to handle any remaining configuration issues
            XdmNode xdmNode = SharedSaxonProcessor.getDocumentBuilder().build(new DOMSource(node));
            log.debug("Function {} successfully processed parameter '{}' and returned node", getFnName(), val);
            return xdmNode;

        } catch (Exception e) {
            log.error("Error in function {} processing parameter '{}': {}", getFnName(), val, e.getMessage());
            log.debug("Full stack trace for function {} error:", getFnName(), e);
            // Return empty sequence on any error to prevent XSLT processing failure
            return XdmEmptySequence.getInstance();
        }
<<<<<<< Updated upstream

        // Use the shared document builder instead of creating a new processor each time
        return SHARED_DOCUMENT_BUILDER.build(new DOMSource(node));
=======
>>>>>>> Stashed changes
    }
}
