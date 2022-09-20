package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.model.MetadataFieldRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MetadataField;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Context;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
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
    @PreAuthorize("hasAuthority('ADMIN')")
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
        try {
            clarinLicenseRest = new ObjectMapper().readValue(
                    getRequestService().getCurrentRequest().getHttpServletRequest().getInputStream(),
                    ClarinLicenseRest.class
            );
        } catch (IOException excIO) {
            throw new DSpaceBadRequestException("error parsing request body", excIO);
        }

        // validate fields
        if (isBlank(clarinLicenseRest.getName()) || isBlank(clarinLicenseRest.getDefinition())) {
            throw new UnprocessableEntityException("Clarin License name, definition, " +
                    "license label cannot be null or empty");
        }

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
    @PreAuthorize("hasAuthority('ADMIN')")
    protected ClarinLicenseRest put(Context context, HttpServletRequest request, String apiCategory, String model,
                                    Integer id, JsonNode jsonNode) throws SQLException, AuthorizeException {

        ClarinLicenseRest clarinLicenseRest = new Gson().fromJson(jsonNode.toString(), ClarinLicenseRest.class);

        if (Objects.isNull(clarinLicenseRest)) {
            throw new RuntimeException("Cannot parse ClarinLicenseRest object from request.");
        }

        if (Objects.isNull(clarinLicenseRest.getClarinLicenseLabel()) ||
                Objects.isNull(clarinLicenseRest.getExtendedClarinLicenseLabels()) ||
                StringUtils.isBlank(clarinLicenseRest.getName()) ||
                StringUtils.isBlank(clarinLicenseRest.getDefinition())) {
            throw new UnprocessableEntityException("The ClarinLicense doesn't have required properties or some " +
                    "some property is null.");
        }

        ClarinLicense clarinLicense = clarinLicenseService.find(context, id);
        if (Objects.isNull(clarinLicense)) {
            throw new ResourceNotFoundException("Clarin License with id: " + id + " not found");
        }

        clarinLicense.setName(clarinLicenseRest.getName());
        clarinLicense.setRequiredInfo(clarinLicenseRest.getRequiredInfo());
        clarinLicense.setDefinition(clarinLicenseRest.getDefinition());
        clarinLicense.setConfirmation(clarinLicenseRest.getConfirmation());
        clarinLicense.setLicenseLabels(this.getClarinLicenseLabels(clarinLicenseRest.getClarinLicenseLabel(),
                clarinLicenseRest.getExtendedClarinLicenseLabels()));

        clarinLicenseService.update(context, clarinLicense);

        return converter.toRest(clarinLicense, utils.obtainProjection());
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void delete(Context context, Integer id) throws AuthorizeException {

        try {
            ClarinLicense clarinLicense = clarinLicenseService.find(context, id);

            if (Objects.isNull(clarinLicense)) {
                throw new ResourceNotFoundException("Clarin license with id: " + id + " not found");
            }

            clarinLicenseService.delete(context, clarinLicense);
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to delete " + ClarinLicenseRest.NAME + " with id: " + id, e);
        }
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
