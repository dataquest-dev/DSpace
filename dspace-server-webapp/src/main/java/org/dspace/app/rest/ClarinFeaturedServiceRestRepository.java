package org.dspace.app.rest;

import org.dspace.app.rest.model.ClarinFeaturedServiceRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.repository.DSpaceRestRepository;
import org.dspace.content.clarin.ClarinFeaturedService;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.core.Context;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Objects;

@Component(ClarinFeaturedServiceRest.CATEGORY + "." + ClarinFeaturedServiceRest.NAME)
public class ClarinFeaturedServiceRestRepository extends DSpaceRestRepository<ClarinFeaturedServiceRest, Integer> {
    @Override
    @PreAuthorize("permitAll()")
    public ClarinFeaturedServiceRest findOne(Context context, Integer integer) {
        return null;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<ClarinFeaturedServiceRest> findAll(Context context, Pageable pageable) {
        return null;
    }

    @Override
    public Class<ClarinFeaturedServiceRest> getDomainClass() {
        return ClarinFeaturedServiceRest.class;
    }
}
