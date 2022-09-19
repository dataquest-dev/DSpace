package org.dspace.content.factory;

import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.springframework.beans.factory.annotation.Autowired;

public class ClarinServiceFactoryImpl extends ClarinServiceFactory {

    @Autowired(required = true)
    private ClarinLicenseService clarinLicenseService;

    @Autowired(required = true)
    private ClarinLicenseLabelService clarinLicenseLabelService;

    @Autowired(required = true)
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;


    @Override
    public ClarinLicenseService getClarinLicenseService() {
        return clarinLicenseService;
    }

    @Override
    public ClarinLicenseLabelService getClarinLicenseLabelService() {
        return clarinLicenseLabelService;
    }

    @Override
    public ClarinLicenseResourceMappingService getClarinResourceMappingService() {
        return clarinLicenseResourceMappingService;
    }
}
