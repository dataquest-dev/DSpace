package org.dspace.app.rest.repository;

import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Component(ClarinLicenseLabelRest.CATEGORY + "." + ClarinLicenseLabelRest.NAME)
public class ClarinLicenseLabelRestRepository extends DSpaceRestRepository<ClarinLicenseLabelRest, Integer> {

    @Autowired
    ClarinLicenseLabelService clarinLicenseLabelService;

    @Override
    public ClarinLicenseLabelRest findOne(Context context, Integer integer) {
        return null;
    }

    @Override
    public Page<ClarinLicenseLabelRest> findAll(Context context, Pageable pageable) {
        try {
            List<ClarinLicenseLabel> clarinLicenseLabelList = clarinLicenseLabelService.findAll(context);
            return converter.toRestPage(clarinLicenseLabelList, pageable, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Class<ClarinLicenseLabelRest> getDomainClass() {
        return ClarinLicenseLabelRest.class;
    }
}
