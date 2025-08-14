/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.services.impl.resources.functions;

import static org.dspace.xoai.services.impl.resources.functions.StringXSLFunction.BASE;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;


/**
 * Serves as proxy for call from XSL engine.
 *
 * @author Marian Berger (marian.berger at dataquest.sk)
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public abstract class NodeListXslFunction implements ExtensionFunction {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(NodeListXslFunction.class);

    protected abstract String getFnName();
    protected abstract List<String> getList(String param);

    @Override
    final public QName getName() {
        return new QName(BASE, getFnName());
    }

    @Override
    final public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_MORE);
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
            log.warn("Empty value in call of function {}, returning empty sequence", getFnName());
            return XdmEmptySequence.getInstance();
        }

        try {
            List<String> list = getList(val);
            if (list == null || list.isEmpty()) {
                log.debug("Function {} returned empty list for parameter '{}', returning empty sequence", getFnName(), val);
                return XdmEmptySequence.getInstance();
            }

            List<XdmItem> items = new LinkedList<>();
            for (String item : list) {
                if (item != null) {
                    items.add(new XdmAtomicValue(item));
                }
            }

            if (items.isEmpty()) {
                return XdmEmptySequence.getInstance();
            }

            log.debug("Function {} successfully processed parameter '{}' and returned {} items", getFnName(), val, items.size());
            return new XdmValue(items);

        } catch (Exception e) {
            log.error("Error in function {} processing parameter '{}': {}", getFnName(), val, e.getMessage());
            log.debug("Full stack trace for function {} error:", getFnName(), e);
            return XdmEmptySequence.getInstance();
        }
    }
}
