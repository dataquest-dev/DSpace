package org.dspace.app.rest.repository;

import org.dspace.app.rest.model.ClarinLicenseResourceMappingRest;
import org.dspace.app.rest.model.ClarinUserRegistrationRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Component(ClarinLicenseResourceMappingRest.CATEGORY + "." + ClarinLicenseResourceMappingRest.NAME)
public class ClarinLicenseResourceMappingRestRepository
        extends DSpaceRestRepository<ClarinLicenseResourceMappingRest, Integer> {

    @Autowired
    ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    @Override
    public ClarinLicenseResourceMappingRest findOne(Context context, Integer integer) {
        ClarinLicenseResourceMapping clarinLicenseResourceMapping;
        try {
            clarinLicenseResourceMapping = clarinLicenseResourceMappingService.find(context, integer);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (Objects.isNull(clarinLicenseResourceMapping)) {
            return null;
        }
        return converter.toRest(clarinLicenseResourceMapping, utils.obtainProjection());
    }

    @Override
    public Page<ClarinLicenseResourceMappingRest> findAll(Context context, Pageable pageable) {
        try {
            List<ClarinLicenseResourceMapping> clarinLicenseResourceMappings =
                    clarinLicenseResourceMappingService.findAll(context);
            return converter.toRestPage(clarinLicenseResourceMappings, pageable, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Class<ClarinLicenseResourceMappingRest> getDomainClass() {
        return ClarinLicenseResourceMappingRest.class;
    }
}
