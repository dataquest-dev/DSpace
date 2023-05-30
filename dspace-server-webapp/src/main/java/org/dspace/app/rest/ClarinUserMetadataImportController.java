package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.ClarinUserMetadataRest;
import org.dspace.app.rest.repository.ClarinUserMetadataRestController;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.content.clarin.ClarinUserMetadata;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.content.service.clarin.ClarinUserMetadataService;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ControllerUtils;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

@RestController
@RequestMapping("/api/clarin/import")
public class ClarinUserMetadataImportController {
    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(ClarinUserMetadataImportController.class);

    @Autowired
    private EPersonService ePersonService;
    @Autowired
    private ClarinLicenseResourceUserAllowanceService clarinLicenseResourceUserAllowanceService;
    @Autowired
    private ClarinUserRegistrationService clarinUserRegistrationService;
    @Autowired
    private ConverterService converter;
    @Autowired
    private Utils utils;
    @Autowired
    private ClarinUserMetadataRestController clarinUserMetadataRestController;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method =  RequestMethod.POST, value = "/usermetadata")
    public ClarinUserMetadataRest importUserMetadata(HttpServletRequest request) throws SQLException, IOException, java.text.ParseException {
        //controlling of the input parameters
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        String epersonUUIDString = request.getParameter("epersonId");
        if (StringUtils.isBlank(epersonUUIDString)) {
            log.error("Required parameter eperson_id is null!");
            throw new RuntimeException("EpersonId is null!");
        }
        UUID epersonUUID = UUID.fromString(epersonUUIDString);

        String bitstreamUUIDString = request.getParameter("bitstreamUUID");
        if (StringUtils.isBlank(bitstreamUUIDString)) {
            log.error("Required parameter bitstreamUUID is null!");
            throw new RuntimeException("BitstreamUUID is null!");
        }
        UUID bitstreamUUID = UUID.fromString(bitstreamUUIDString);

        String createdOnString = request.getParameter("createdOn");
        if (StringUtils.isBlank(createdOnString)) {
            log.error("Required parameter created_on is null!");
            throw new RuntimeException("Created_on is null!");
        }
        Date createdOn = getDateFromString(createdOnString);

        //we don't control token, because it can be null
        String token = request.getParameter("token");

        //set current user and turn off the authorization system
        EPerson ePerson = ePersonService.find(context, epersonUUID);
        if (Objects.isNull(ePerson)) {
            log.error("Eperson with id: " + epersonUUID + " doesn't exist!");
            throw new RuntimeException("Eperson with id: " + epersonUUID + " doesn't exist!");
        }

        // Get ClarinUserMetadataRest Array from the request body
        ClarinUserMetadataRest[] clarinUserMetadataRestArray =
                new ObjectMapper().readValue(request.getInputStream(), ClarinUserMetadataRest[].class);
        if (ArrayUtils.isEmpty(clarinUserMetadataRestArray)) {
            log.error("Cannot get clarinUserMetadataRestArray from request for eperson with id: " + epersonUUID +
                    " and bitstream with id: " + bitstreamUUID);
            throw new RuntimeException("Cannot get clarinUserMetadataRestArray from request for eperson with id: "
                    + epersonUUID + " and bitstream with id: " + bitstreamUUID);
        }
        // Convert Array to the List
        List<ClarinUserMetadataRest> clarinUserMetadataRestList = Arrays.asList(clarinUserMetadataRestArray);
        if (CollectionUtils.isEmpty(clarinUserMetadataRestList)) {
            log.error("Cannot convert clarinUserMetadataRestArray to array for eperson with id: " + epersonUUID +
                    " and bitstream id: " + bitstreamUUID);
            throw new RuntimeException("Cannot get clarinUserMetadataRestArray from request for eperson with id: "
                    + epersonUUID + " and bitstream with id: " + bitstreamUUID);
        }
        // Get mapping between clarin license and the bitstream
        ClarinLicenseResourceMapping clarinLicenseResourceMapping =
                clarinUserMetadataRestController.getLicenseResourceMapping(context, bitstreamUUID);
        if (Objects.isNull(clarinLicenseResourceMapping)) {
            log.error("Cannot find the license resource mapping between clarin license" +
                    " and the bitstream with id: " + bitstreamUUID);
            throw new NotFoundException("Cannot find the license resource mapping between clarin license" +
                    " and the bitstream with id: " + bitstreamUUID);
        }
        // The user is signed in
        List<ClarinUserMetadata> newClarinUserMetadataList = clarinUserMetadataRestController.processSignedInUser(context, ePerson, clarinUserMetadataRestList, clarinLicenseResourceMapping,
                bitstreamUUID, token);
        //set eperson_id (user registration) in user_metadata
        newClarinUserMetadataList.get(0).setEperson(clarinUserRegistrationService.findByEPersonUUID(context, epersonUUID).get(0));
        //set created_on for created license_resource_user_allowance
        //created list has to contain minimally one record
        ClarinLicenseResourceUserAllowance clarinLicenseResourceUserAllowance = newClarinUserMetadataList.get(0).getTransaction();
        clarinLicenseResourceUserAllowance.setCreatedOn(createdOn);
        clarinLicenseResourceUserAllowanceService.update(context, clarinLicenseResourceUserAllowance);

        ClarinUserMetadataRest clarinUserMetadataRest = converter.toRest(newClarinUserMetadataList.get(0), utils.obtainProjection());
        context.commit();
        return clarinUserMetadataRest;
    }

    /**
     * Convert String value to Date.
     * Expects two possible date formats, but more can be added.
     * @param value
     * @return converted input value to Date
     * @throws java.text.ParseException if parse error
     */
    private Date getDateFromString(String value) throws java.text.ParseException {
        Date output = null;
        if (StringUtils.isBlank(value)) {
            return null;
        }

        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        try {
            output = sdf.parse(value);
        } catch (java.text.ParseException e) {
            try {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSS");
                output = sdf.parse(value);
            } catch (java.text.ParseException e1) {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSS");
                output = sdf.parse(value);
            }
        }
        return output;
    }
}
