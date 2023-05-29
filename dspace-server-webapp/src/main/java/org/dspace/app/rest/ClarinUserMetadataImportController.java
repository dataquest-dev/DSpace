package org.dspace.app.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.repository.ClarinUserMetadataRestController;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserMetadata;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

@RestController
@RequestMapping("/api/clarin/import/userMetadata")
public class ClarinUserMetadataImportController {
    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(ClarinUserMetadataImportController.class);

    @Autowired
    private EPersonService ePersonService;

    private ClarinUserMetadataRestController clarinUserMetadataRestController;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method =  RequestMethod.POST)
    public BitstreamRest importUserMetadata(@RequestParam("bitstreamUUID") UUID bitstreamUUID,
                                            @RequestParam("eperson_id") UUID epersonUUID,
                                            HttpServletRequest request) throws SQLException, MessagingException, AuthorizeException, ParseException, IOException {
        //controlling of the input parameters
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        if (Objects.isNull(epersonUUID)) {
            log.error("Required parameter eperson_id is null!");
            throw new RuntimeException("EpersonId is null!");
        }
        if (Objects.isNull(bitstreamUUID)) {
            log.error("Required parameter bitstreamUUID is null!");
            throw new RuntimeException("BitstreamUUID is null!");
        }

        //set current user and turn off the authorization system
        EPerson ePerson = ePersonService.find(context, epersonUUID);
        if (Objects.isNull(ePerson)) {
            log.error("Eperson with id: " + epersonUUID + " doesn't exist!");
            throw new RuntimeException("Eperson with id: " + epersonUUID + " doesn't exist!");
        }
        //we cannot use existing method manageUserMetadata, because we cannot access created data
        //and we cannot send email to users after creating new token



    }

    /**
     * Convert String value to Integer.
     * @param value input value
     * @return input value converted to Integer
     */
    private Integer getIntegerFromString(String value) {
        Integer output = null;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        }
        return output;
    }
}
