/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/api/licenses/import")
public class ClarinLicenseImportRestController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(BitstreamRestController.class);

    private Dictionary<Integer, Integer> licenseLabelsIds = new Hashtable<>();

    private Dictionary<Integer, Set<ClarinLicenseLabel>> licenseToLicenseLabel = new Hashtable<>();
    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;

    @Autowired
    ClarinLicenseService clarinLicenseService;

    @RequestMapping(method = RequestMethod.POST, value = "/labels")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity importLincenseLabels(@RequestBody(required = false) List<JsonNode> licenseLabels,
                                              HttpServletRequest request, HttpServletResponse response)
            throws SQLException, AuthorizeException {

        if (licenseLabels == null || licenseLabels.size() < 1) {
            throw new BadRequestException("The new license labels should be included as json in the body of this request");
        }

        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            return new ResponseEntity<>("Context is null", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        ClarinLicenseLabelRest inputLicenseLabelRest;
        ClarinLicenseLabel licenseLabel;

            for (JsonNode jsonLicenseLabel : licenseLabels) {
                try {
                ObjectMapper mapper = new ObjectMapper();
                    inputLicenseLabelRest = mapper.readValue(jsonLicenseLabel.toString(), ClarinLicenseLabelRest.class);
                } catch (IOException e1) {
                    throw new UnprocessableEntityException("Error parsing request body", e1);
                }

                if (isBlank(inputLicenseLabelRest.getLabel()) || isBlank(inputLicenseLabelRest.getTitle())) {
                    throw new UnprocessableEntityException("Clarin License Label title, label, icon cannot be null or empty");
                }

                // create
                licenseLabel = clarinLicenseLabelService.create(context);
                licenseLabel.setLabel(inputLicenseLabelRest.getLabel());
                licenseLabel.setTitle(inputLicenseLabelRest.getTitle());
                licenseLabel.setExtended(inputLicenseLabelRest.isExtended());

                clarinLicenseLabelService.update(context, licenseLabel);

                this.licenseLabelsIds.put(inputLicenseLabelRest.getId(), licenseLabel.getID());
            }
        context.commit();
        return new ResponseEntity<>("Import License Labels were successful", HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/extendedMapping")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity importLincenseLabelExtendedMapping(@RequestBody(required = false) List<JsonNode> licenseLabelExtendedMappings,
                                          HttpServletRequest request, HttpServletResponse response)
            throws SQLException, AuthorizeException {

        if (licenseLabelExtendedMappings == null || licenseLabelExtendedMappings.size() < 1) {
            throw new BadRequestException("The new license label extended mappings should be included as json in the body of this request");
        }

        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            return new ResponseEntity<>("Context is null", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        for (JsonNode jsonLicenseLabelExtendedMapping : licenseLabelExtendedMappings) {
            if (jsonLicenseLabelExtendedMapping.has("license_id") && jsonLicenseLabelExtendedMapping.has("label_id")) {
                Set<ClarinLicenseLabel> licenseLabels = this.licenseToLicenseLabel.get(jsonLicenseLabelExtendedMapping.get("license_id").asInt());
                if (licenseLabels == null) {
                    licenseLabels = new HashSet<>();
                    this.licenseToLicenseLabel.put(jsonLicenseLabelExtendedMapping.get("license_id").asInt(), licenseLabels);
                }
                ClarinLicenseLabel clarinLicenseLabel = null;
                try {
                    Integer licenseLabelID = this.licenseLabelsIds.get(jsonLicenseLabelExtendedMapping.get("label_id").asInt());
                    if (licenseLabelID == null) {
                        return new ResponseEntity<>("License label doesn't exist", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    clarinLicenseLabel = clarinLicenseLabelService.find(context,licenseLabelID);
                    if (Objects.isNull(clarinLicenseLabel)) {
                        return new ResponseEntity<>("License label doesn't exist", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                licenseLabels.add(clarinLicenseLabel);
            }
        }
        return new ResponseEntity<>("Import License label extended mappings were successful", HttpStatus.OK);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/licenses")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity importLincenses(@RequestBody(required = false) List<JsonNode> licenses,
                                               HttpServletRequest request, HttpServletResponse response)
            throws SQLException, AuthorizeException {

        if (licenses == null || licenses.size() < 1) {
            throw new BadRequestException("The new licenses should be included as json in the body of this request");
        }

        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            return new ResponseEntity<>("Context is null", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        ClarinLicenseRest inputLicenseRest;
        ClarinLicense license;

        for (JsonNode jsonLicense : licenses) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                inputLicenseRest = mapper.readValue(jsonLicense.toString(), ClarinLicenseRest.class);
            } catch (IOException e1) {
                throw new UnprocessableEntityException("Error parsing request body", e1);
            }
            Set<ClarinLicenseLabel> licenseLabels = this.licenseToLicenseLabel.get(inputLicenseRest.getId());
            if (licenseLabels == null) {
                licenseLabels = new HashSet<>();
            }
            licenseLabels.add(this.clarinLicenseLabelService.find(context,inputLicenseRest.getClarinLicenseLabel().getId()));
            if (licenseLabels == null) {
                //the status???
                return new ResponseEntity<>("License labels for license haven't imported yet", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            license = clarinLicenseService.create(context);
            license.setName(inputLicenseRest.getName());
            license.setLicenseLabels(licenseLabels);
            license.setDefinition(inputLicenseRest.getDefinition());
            license.setConfirmation(inputLicenseRest.getConfirmation());
            license.setRequiredInfo(inputLicenseRest.getRequiredInfo());

            clarinLicenseService.update(context, license);
        }
        context.commit();

        return new ResponseEntity<>("Import Licenses were successful", HttpStatus.OK);
    }
}
