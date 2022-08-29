package org.dspace.handle.factory;

import org.dspace.handle.service.HandleClarinService;
import org.springframework.beans.factory.annotation.Autowired;

public class HandleClarinServiceFactoryImpl extends HandleClarinServiceFactory{

    @Autowired(required = true)
    private HandleClarinService handleClarinService;

    @Override
    public HandleClarinService getHandleClarinService() {
        return handleClarinService;
    }
}
