/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.factory;

import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Abstract factory to get services for the clarin package, use ClarinServiceFactory.getInstance() to retrieve an
 * implementation
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public abstract class ClarinServiceFactory {

    public abstract ClarinLicenseService getClarinLicenseService();

    public abstract ClarinLicenseLabelService getClarinLicenseLabelService();

    public static ClarinServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager()
                .getServiceByName("clarinServiceFactory", ClarinServiceFactory.class);
    }
}
