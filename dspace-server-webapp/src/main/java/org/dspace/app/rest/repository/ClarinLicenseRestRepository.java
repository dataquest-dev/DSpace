package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Context;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component(ClarinLicenseRest.CATEGORY + "." + ClarinLicenseRest.NAME)
public class ClarinLicenseRestRepository extends DSpaceRestRepository<ClarinLicenseRest, Integer> {

    @Autowired
    ClarinLicenseService clarinLicenseService;

    @Override
    @PreAuthorize("permitAll()")
    public ClarinLicenseRest findOne(Context context, Integer idValue) {
        ClarinLicense clarinLicense = null;
        try {
            clarinLicense = clarinLicenseService.find(context, idValue);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (Objects.isNull(clarinLicense)) {
            return null;
        }
        return converter.toRest(clarinLicense, utils.obtainProjection());
    }

    @Override
    public Page<ClarinLicenseRest> findAll(Context context, Pageable pageable) {
        try {
            List<ClarinLicense> clarinLicenseList = clarinLicenseService.findAll(context);
            return converter.toRestPage(clarinLicenseList, pageable, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected ClarinLicenseRest createAndReturn(Context context)
            throws AuthorizeException, SQLException {

        // parse request body
        ClarinLicenseRest clarinLicenseRest;
        JSONObject clarinLicenseJSON;
        try {
            clarinLicenseRest = new ObjectMapper().readValue(
                    getRequestService().getCurrentRequest().getHttpServletRequest().getInputStream(),
                    ClarinLicenseRest.class
            );
        } catch (IOException excIO) {
            throw new DSpaceBadRequestException("error parsing request body", excIO);
        }

//        // validate fields
//        if (isBlank(clarinLicenseRest.getName()) || isBlank(clarinLicenseRest.getDefinition())) {
//            throw new UnprocessableEntityException("Clarin License name, definition, " +
//                    "license label cannot be null or empty");
//        }

        // create
        ClarinLicense clarinLicense;
        clarinLicense = clarinLicenseService.create(context);
        clarinLicense.setName(clarinLicenseRest.getName());
        clarinLicense.setLicenseLabels(this.getClarinLicenseLabels(clarinLicenseRest.getClarinLicenseLabel(),
                clarinLicenseRest.getExtendedClarinLicenseLabels()));
        clarinLicense.setDefinition(clarinLicenseRest.getDefinition());
        clarinLicense.setConfirmation(clarinLicenseRest.getConfirmation());
        clarinLicense.setRequiredInfo(clarinLicenseRest.getRequiredInfo());

        clarinLicenseService.update(context, clarinLicense);
        // return
        return converter.toRest(clarinLicense, utils.obtainProjection());
    }

    @Override
    public Class<ClarinLicenseRest> getDomainClass() {
        return ClarinLicenseRest.class;
    }

    private Set<ClarinLicenseLabel> getClarinLicenseLabels(ClarinLicenseLabelRest clarinLicenseLabelRest,
                                                           List<ClarinLicenseLabelRest> extendedClarinLicenseLabels) {
        Set<ClarinLicenseLabel> clarinLicenseLabels = new HashSet<>();

        clarinLicenseLabels.add(getClarinLicenseLabelFromRest(clarinLicenseLabelRest));
        extendedClarinLicenseLabels.forEach(cllr -> {
            clarinLicenseLabels.add(getClarinLicenseLabelFromRest(cllr));
        });

//        clarinLicenseLabels.add(getClarinLicenseLabelFromRest(clarinLicenseRest.getClarinLicenseLabel()));
//        clarinLicenseRest.getExtendedClarinLicenseLabels().values().forEach(list -> {
//            list.forEach(clarinLicenseLabelRest -> {
//                clarinLicenseLabels.add(getClarinLicenseLabelFromRest(clarinLicenseLabelRest));
//            });
//        });

        return clarinLicenseLabels;
    }

    private ClarinLicenseLabel getClarinLicenseLabelFromRest(ClarinLicenseLabelRest clarinLicenseLabelRest) {
        ClarinLicenseLabel clarinLicenseLabel = new ClarinLicenseLabel();
        clarinLicenseLabel.setLabel(clarinLicenseLabelRest.getLabel());
        clarinLicenseLabel.setTitle(clarinLicenseLabelRest.getTitle());
        clarinLicenseLabel.setExtended(clarinLicenseLabelRest.isExtended());
        clarinLicenseLabel.setIcon(clarinLicenseLabelRest.getIcon());
        clarinLicenseLabel.setId(clarinLicenseLabelRest.getId());
        return clarinLicenseLabel;
    }

}
