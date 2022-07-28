/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

/* Created for LINDAT/CLARIAH-CZ (UFAL) */
package org.dspace.xoai.services.impl.resources.functions;

import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.xoai.services.api.config.ConfigurationService;
import org.hibernate.engine.config.internal.ConfigurationServiceInitiator;
import org.springframework.beans.factory.annotation.Autowired;

public class GetPropertyFn extends StringXSLFunction {

    @Override
    protected String getFnName() {
        return "getProperty";
    }

    @Override
    protected String getStringResult(String param) {
        return DSpaceServicesFactory.getInstance().getConfigurationService().getProperty(param);
    }
}
