package org.dspace.handle.factory;

import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.services.factory.DSpaceServicesFactory;

public abstract class HandleClarinServiceFactory {

    public abstract HandleClarinService getHandleClarinService();

    public static HandleClarinServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager()
                .getServiceByName("handleClarinServiceFactory", HandleClarinServiceFactory.class);
    }
}
