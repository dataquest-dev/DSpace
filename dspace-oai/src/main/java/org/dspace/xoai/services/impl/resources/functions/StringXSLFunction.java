/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.services.impl.resources.functions;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmValue;
public abstract class StringXSLFunction implements ExtensionFunction {

    public static final String BASE = "http://custom.crosswalk.functions";

    protected String uncertainString(Object val) {
        return val == null ? "" : val.toString();
    }

    protected abstract String getFnName();

//    protected abstract String getStringResult(String param);
    protected String getStringResult(String param) {
        return "";
    }

    @Override
    final public QName getName() {
        return new QName(BASE, getFnName());
    }

    @Override
    final public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_ONE);
    }

    @Override
    final public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
                SequenceType.makeSequenceType(
                        ItemType.STRING, OccurrenceIndicator.ONE)};
    }

    @Override
    final public XdmValue call(XdmValue[] xdmValues) throws SaxonApiException {
        return new XdmAtomicValue(checks(getStringResult(xdmValues[0].itemAt(0).getStringValue())));
    }

    private String checks(String got) {
        if (got == null) {
            return "";
        }

        if (got.equalsIgnoreCase("[#document: null]")) {
            return "";
        }

        return got;
    }
}
