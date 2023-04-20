package org.dspace.app.rest.repository;

import org.apache.commons.lang3.StringUtils;

import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.EPersonRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.model.hateoas.BitstreamResource;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.Date;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

@RestController
@RequestMapping("/api/clarin/import/" + EPersonRest.EPERSON)
public class ClarinEPersonImportController {

    @Autowired
    private EPersonRestRepository ePersonRestRepository;

    @Autowired
    private ConverterService converter;

    @Autowired
    private EPersonService ePersonService;

    @Autowired
    private ClarinUserRegistrationService clarinUserRegistrationService;

    @Autowired
    private Utils utils;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST)
    public EPersonRest importEPerson(HttpServletRequest request)
            throws AuthorizeException, SQLException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        String selfRegisteredString = request.getParameter("selfRegistered");
        boolean selfRegistered = getBooleanFromString(selfRegisteredString);
        String lastActiveString = request.getParameter("lastActive");
        Date lastActive;
        try {
            lastActive = getDateFromString(lastActiveString);
        } catch (ParseException e) {
            throw new RuntimeException("Cannot import eperson, because the last_active is entered incorrectly!");
        }
        //salt and digest_algorithm are changing with password
        EPersonRest epersonRest = ePersonRestRepository.createAndReturn(context);
        EPerson eperson = ePersonService.find(context, UUID.fromString(epersonRest.getUuid()));
        eperson.setSelfRegistered(selfRegistered);
        eperson.setLastActive(lastActive);
        ePersonService.update(context, eperson);

        String hasUserRegistrationString = request.getParameter("userRegistration");
        boolean userRegistration = getBooleanFromString(hasUserRegistrationString);
        //create user registration if exists
        if (userRegistration) {
            String organization = request.getParameter("organization");
            String confirmationString = request.getParameter("confirmation");
            boolean confirmation = getBooleanFromString(confirmationString);

            ClarinUserRegistration clarinUserRegistration = new ClarinUserRegistration();
            clarinUserRegistration.setOrganization(organization);
            clarinUserRegistration.setConfirmation(confirmation);
            clarinUserRegistration.setEmail(eperson.getEmail());
            clarinUserRegistration.setPersonID(eperson.getID());
            clarinUserRegistrationService.create(context, clarinUserRegistration);
        }
        epersonRest = converter.toRest(eperson, utils.obtainProjection());
        context.complete();

        return epersonRest;
    }

    private boolean getBooleanFromString(String value) {
        boolean output = false;
        if (StringUtils.isNotBlank(value)) {
            output = Boolean.parseBoolean(value);
        }
        return output;
    }

    private Date getDateFromString(String value) throws ParseException {
        Date output = null;
        if (StringUtils.isNotBlank(value)) {
            DateFormat df;
            df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            try {
                output = df.parse(value);
            } catch (ParseException e) {
                df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                output = df.parse(value);
            }
        }
        return output;
    }
}
