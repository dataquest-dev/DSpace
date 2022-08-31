/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.utils.LicenseUtil;

public class UriToLicenseFn extends StringXSLFunction {
    @Override
    protected String getFnName() {
        return "uriToAvailability";
    }

    @Override
    protected String getStringResult(String param) {
        return LicenseUtil.uriToAvailability(param);
    }
}
