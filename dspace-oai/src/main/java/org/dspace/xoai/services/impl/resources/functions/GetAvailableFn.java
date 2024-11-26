/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.utils.SpecialItemService;

public class GetAvailableFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "getAvailable";
    }

    @Override
    protected String getStringResult(String param) {
        return SpecialItemService.getAvailable(param);
    }
}
