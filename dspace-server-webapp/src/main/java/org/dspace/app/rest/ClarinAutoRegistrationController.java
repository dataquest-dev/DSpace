package org.dspace.app.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.rest.model.AuthnRest;
import org.dspace.app.rest.model.hateoas.AuthnResource;
import org.dspace.authenticate.ShibAuthentication;
import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.web.ContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

@RequestMapping(value = "/api/autoregistration")
@RestController
public class ClarinAutoRegistrationController {

    private static Logger log = Logger.getLogger(ClarinAutoRegistrationController.class);

    @Autowired
    ConfigurationService configurationService;

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity sendEmail(HttpServletRequest request, HttpServletResponse response,
                                    @RequestParam("netid") String netid,
                                    @RequestParam("email") String email,
                                    @RequestParam("fname") String fname,
                                    @RequestParam("lname") String lname) throws IOException, MessagingException {

        Context context = ContextUtil.obtainCurrentRequestContext();
        if (Objects.isNull(context)) {
            log.error("Cannot obtain the context from the request");
            throw new RuntimeException("Cannot obtain the context from the request");
        }

        String uiUrl = configurationService.getProperty("dspace.ui.url");
        if (StringUtils.isEmpty(uiUrl)) {
            log.error("Cannot load the `dspace.ui.url` property from the cfg.");
            throw new RuntimeException("Cannot load the `dspace.ui.url` property from the cfg.");
        }

        String autoregistrationURL = uiUrl + "/login/autoregistration?netid=" + netid + "&email=" + email +
                "&fname=" + fname + "&lname=" + lname;
        try {
            Locale locale = context.getCurrentLocale();
            Email bean = Email.getEmail(I18nUtil.getEmailFilename(locale, "clarin_autoregistration"));
            bean.addArgument(autoregistrationURL);
            bean.addRecipient(email);
            bean.send();
        } catch (Exception e) {
            log.error("Cannot send the email because: " + e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot send the email");
            return null;
        }

        return ResponseEntity.ok().build();

        // Generate token
        // Combine the URL request
        // Send e-mail
        // Return the response
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity confirmEmail(HttpServletRequest request, HttpServletResponse response,
                                       @RequestParam("netid") String netid,
                                       @RequestParam("email") String email,
                                       @RequestParam("fname") String fname,
                                       @RequestParam("lname") String lname) {

        // Register the new user - call Shibboleth authenticate method
        // Send response
        return null;
    }
}
