/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.services.impl.resources.functions;

import java.util.ArrayList;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmArray;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class DistinctFn implements ExtensionFunction {
    @Override
    public QName getName() {
        return new QName(StringXSLFunction.BASE, "distinct");
    }

    @Override
    public SequenceType getResultType() {
        return SequenceType.makeSequenceType(ItemType.ANY_ARRAY, OccurrenceIndicator.ZERO_OR_MORE);
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_MORE)
        };
    }

    @Override
    public XdmValue call(XdmValue[] xdmValues) throws SaxonApiException {
        var iterator = xdmValues[0].iterator();
        ArrayList<Object> preRet = new ArrayList<>(xdmValues[0].size());
        ArrayList<XdmValue> retValues = new ArrayList<>(xdmValues[0].size());
        int position = 0;
        while (iterator.hasNext()) {
            XdmItem current = iterator.next();
            if (!preRet.contains(current.toString())) {
                preRet.add(current.toString());
                retValues.add(current);
            }
            position++;
        }
        return new XdmArray(retValues);
    }
}
