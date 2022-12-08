package org.dspace.app.rest.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authenticate.ShibAuthentication;
import org.dspace.authenticate.factory.AuthenticateServiceFactory;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Objects;

@Component
public class ClarinShibAuthentication {
    /**
     * log4j category
     */
    private static final Logger log = LogManager.getLogger(ClarinShibAuthentication.class);

    /**
     * Maximum length for eperson metadata fields
     **/
    protected final int NAME_MAX_SIZE = 64;
    protected final int PHONE_MAX_SIZE = 32;

    /**
     * Maximum length for eperson additional metadata fields
     **/
    protected final int METADATA_MAX_SIZE = 1024;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    EPersonService ePersonService;

    @Autowired
    ClarinUserRegistrationService clarinUserRegistrationService;


    /**
     * Find a particular Shibboleth header value and return the all values.
     * The header name uses a bit of fuzzy logic, so it will first try case
     * sensitive, then it will try lowercase, and finally it will try uppercase.
     *
     * This method will not interpret the header value in any way.
     *
     * This method will return null if value is empty.
     *
     * @param request The HTTP request to look for values in.
     * @param name    The name of the attribute or header
     * @return The value of the attribute or header requested, or null if none found.
     */
    protected String findAttribute(HttpServletRequest request, String name) {
        if (name == null) {
            return null;
        }
        // First try to get the value from the attribute
        String value = (String) request.getAttribute(name);
        if (StringUtils.isEmpty(value)) {
            value = (String) request.getAttribute(name.toLowerCase());
        }
        if (StringUtils.isEmpty(value)) {
            value = (String) request.getAttribute(name.toUpperCase());
        }

        // Second try to get the value from the header
        if (StringUtils.isEmpty(value)) {
            value = request.getHeader(name);
        }
        if (StringUtils.isEmpty(value)) {
            value = request.getHeader(name.toLowerCase());
        }
        if (StringUtils.isEmpty(value)) {
            value = request.getHeader(name.toUpperCase());
        }

        // Added extra check for empty value of an attribute.
        // In case that value is Empty, it should not be returned, return 'null' instead.
        // This prevents passing empty value to other methods, stops the authentication process
        // and prevents creation of 'empty' DSpace EPerson if autoregister == true and it subsequent
        // authentication.
        if (StringUtils.isEmpty(value)) {
            log.debug("ShibAuthentication - attribute " + name + " is empty!");
            return null;
        }

        boolean reconvertAttributes =
                configurationService.getBooleanProperty(
                        "authentication-shibboleth.reconvert.attributes",
                        false);

        if (!StringUtils.isEmpty(value) && reconvertAttributes) {
            try {
                value = new String(value.getBytes("ISO-8859-1"), "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                log.warn("Failed to reconvert shibboleth attribute ("
                        + name + ").", ex);
            }
        }

        return value;
    }

    /**
     * Find a particular Shibboleth header value and return the first value.
     * The header name uses a bit of fuzzy logic, so it will first try case
     * sensitive, then it will try lowercase, and finally it will try uppercase.
     *
     * Shibboleth attributes may contain multiple values separated by a
     * semicolon. This method will return the first value in the attribute. If
     * you need multiple values use findMultipleAttributes instead.
     *
     * If no attribute is found then null is returned.
     *
     * @param request The HTTP request to look for headers values on.
     * @param name    The name of the header
     * @return The value of the header requested, or null if none found.
     */
    public String findSingleAttribute(HttpServletRequest request, String name) {
        if (name == null) {
            return null;
        }

        String value = findAttribute(request, name);


        if (value != null) {
            // If there are multiple values encoded in the shibboleth attribute
            // they are separated by a semicolon, and any semicolons in the
            // attribute are escaped with a backslash. For this case we are just
            // looking for the first attribute so we scan the value until we find
            // the first unescaped semicolon and chop off everything else.
            int idx = 0;
            do {
                idx = value.indexOf(';', idx);
                if (idx != -1 && value.charAt(idx - 1) != '\\') {
                    value = value.substring(0, idx);
                    break;
                }
            } while (idx >= 0);

            // Unescape the semicolon after splitting
            value = value.replaceAll("\\;", ";");
        }

        return value;
    }

    /**
     * Register a new eperson object. This method is called when no existing user was
     * found for the NetID or Email and autoregister is enabled. When these conditions
     * are met this method will create a new eperson object.
     *
     * In order to create a new eperson object there is a minimal set of metadata
     * required: Email, First Name, and Last Name. If we don't have access to these
     * three pieces of information then we will be unable to create a new eperson
     * object, such as the case when Tomcat's Remote User field is used to identify
     * a particular user.
     *
     * Note, that this method only adds the minimal metadata. Any additional metadata
     * will need to be added by the updateEPerson method.
     *
     * @param context The current DSpace database context
     * @param request The current HTTP Request
     * @return A new eperson object or null if unable to create a new eperson.
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    public EPerson registerNewEPerson(Context context, HttpServletRequest request)
            throws SQLException, AuthorizeException {

        // Header names
        String netidHeader = configurationService.getProperty("authentication-shibboleth.netid-header");
        String emailHeader = configurationService.getProperty("authentication-shibboleth.email-header");
        String fnameHeader = configurationService.getProperty("authentication-shibboleth.firstname-header");
        String lnameHeader = configurationService.getProperty("authentication-shibboleth.lastname-header");
        // CHANGE
        // Instead of this - load the header from the configurationService.getProperty("authentication-shibboleth.lastname-header");
//        String org = shib_headers.get_idp();
//        if ( org == null ) {
//            return null;
//        }

        // Header values
        String netid = findSingleAttribute(request, netidHeader);
        String email = findSingleAttribute(request, emailHeader);
        String fname = findSingleAttribute(request, fnameHeader);
        String lname = findSingleAttribute(request, lnameHeader);

        if (email == null || (fnameHeader != null && fname == null) || (lnameHeader != null && lname == null)) {
            // We require that there be an email, first name, and last name. If we
            // don't have at least these three pieces of information then we fail.
            String message = "Unable to register new eperson because we are unable to find an email address along " +
                    "with first and last name for the user.\n";
            message += "  NetId Header: '" + netidHeader + "'='" + netid + "' (Optional) \n";
            message += "  Email Header: '" + emailHeader + "'='" + email + "' \n";
            message += "  First Name Header: '" + fnameHeader + "'='" + fname + "' \n";
            message += "  Last Name Header: '" + lnameHeader + "'='" + lname + "'";
            log.error(message);

            return null; // TODO should this throw an exception?
        }

        // Truncate values of parameters that are too big.
        if (fname != null && fname.length() > NAME_MAX_SIZE) {
            log.warn(
                    "Truncating eperson's first name because it is longer than " + NAME_MAX_SIZE + ": '" + fname + "'");
            fname = fname.substring(0, NAME_MAX_SIZE);
        }
        if (lname != null && lname.length() > NAME_MAX_SIZE) {
            log.warn("Truncating eperson's last name because it is longer than " + NAME_MAX_SIZE + ": '" + lname + "'");
            lname = lname.substring(0, NAME_MAX_SIZE);
        }

        // Turn off authorizations to create a new user
        context.turnOffAuthorisationSystem();
        EPerson eperson = ePersonService.create(context);

        // Set the minimum attributes for the new eperson
        if (netid != null) {
            eperson.setNetid(netid);
        }
        eperson.setEmail(email.toLowerCase());
        if (fname != null) {
            eperson.setFirstName(context, fname);
        }
        if (lname != null) {
            eperson.setLastName(context, lname);
        }
        eperson.setCanLogIn(true);

        // Commit the new eperson
        AuthenticateServiceFactory.getInstance().getAuthenticationService().initEPerson(context, request, eperson);
        ePersonService.update(context, eperson);
        context.dispatchEvents();

        /* CLARIN
         *
         * Register User in the CLARIN license database
         *
         */
        // if no email the registration is postponed after entering and confirming mail
        if(Objects.nonNull(email)){
            try{
                ClarinUserRegistration clarinUserRegistration = new ClarinUserRegistration();
                clarinUserRegistration.setConfirmation(true);
                clarinUserRegistration.setEmail(email);
                clarinUserRegistration.setPersonID(eperson.getID());
                clarinUserRegistration.setOrganization(netid);
                clarinUserRegistrationService.create(context, clarinUserRegistration);
                eperson.setCanLogIn(false);
                ePersonService.update(context, eperson);
            }catch(Exception e){
                throw new AuthorizeException("User has not been added among registred users!") ;
            }
        }

        /* CLARIN */

        // Turn authorizations back on.
        context.restoreAuthSystemState();

        if (log.isInfoEnabled()) {
            String message = "Auto registered new eperson using Shibboleth-based attributes:";
            if (netid != null) {
                message += "  NetId: '" + netid + "'\n";
            }
            message += "  Email: '" + email + "' \n";
            message += "  First Name: '" + fname + "' \n";
            message += "  Last Name: '" + lname + "'";
            log.info(message);
        }

        return eperson;
    }

    /**
     * Identify an existing EPerson based upon the shibboleth attributes provided on
     * the request object. There are three cases where this can occurr, each as
     * a fallback for the previous method.
     *
     * 1) NetID from Shibboleth Header (best)
     *    The NetID-based method is superior because users may change their email
     *    address with the identity provider. When this happens DSpace will not be
     *    able to associate their new address with their old account.
     *
     * 2) Email address from Shibboleth Header (okay)
     *    In the case where a NetID header is not available or not found DSpace
     *    will fall back to identifying a user based upon their email address.
     *
     * 3) Tomcat's Remote User (worst)
     *    In the event that neither Shibboleth headers are found then as a last
     *    resort DSpace will look at Tomcat's remote user field. This is the least
     *    attractive option because Tomcat has no way to supply additional
     *    attributes about a user. Because of this the autoregister option is not
     *    supported if this method is used.
     *
     * If successful then the identified EPerson will be returned, otherwise null.
     *
     * @param context The DSpace database context
     * @param request The current HTTP Request
     * @return The EPerson identified or null.
     * @throws SQLException if database error
     * @throws AuthorizeException if authorization error
     */
    protected EPerson findEPerson(Context context, HttpServletRequest request) throws SQLException, AuthorizeException {

        boolean isUsingTomcatUser = configurationService
                .getBooleanProperty("authentication-shibboleth.email-use-tomcat-remote-user");
        String netidHeader = configurationService.getProperty("authentication-shibboleth.netid-header");
        String emailHeader = configurationService.getProperty("authentication-shibboleth.email-header");

        EPerson eperson = null;
        boolean foundNetID = false;
        boolean foundEmail = false;
        boolean foundRemoteUser = false;


        // 1) First, look for a netid header.
        if (netidHeader != null) {
            String netid = findSingleAttribute(request, netidHeader);

            if (netid != null) {
                foundNetID = true;
                eperson = ePersonService.findByNetid(context, netid);

                if (eperson == null) {
                    log.info(
                            "Unable to identify EPerson based upon Shibboleth netid header: '" + netidHeader + "'='" +
                                    netid + "'.");
                } else {
                    log.debug(
                            "Identified EPerson based upon Shibboleth netid header: '" + netidHeader + "'='" + netid + "'" +
                                    ".");
                }
            }
        }

        // 2) Second, look for an email header.
        if (eperson == null && emailHeader != null) {

            // CHANGE - MAYBE NOT this should be fixed by the method findSingleAttribute
            /* <UFAL>
             *
             * Checking for a valid email address.
             *
             */

//            IFunctionalities functionalityManager = DSpaceApi.getFunctionalityManager();
//            email = functionalityManager.getEmailAcceptedOrNull ( email );

            /* </UFAL> */
            // Change

            String email = findSingleAttribute(request, emailHeader);

            if (email != null) {
                foundEmail = true;
                email = email.toLowerCase();
                eperson = ePersonService.findByEmail(context, email);

                if (eperson == null) {
                    log.info(
                            "Unable to identify EPerson based upon Shibboleth email header: '" + emailHeader + "'='" +
                                    email + "'.");
                } else {
                    log.info(
                            "Identified EPerson based upon Shibboleth email header: '" + emailHeader + "'='" + email + "'" +
                                    ".");
                }

                if (eperson != null && eperson.getNetid() != null) {
                    // If the user has a netID it has been locked to that netid, don't let anyone else try and steal
                    // the account.
                    log.error(
                            "The identified EPerson based upon Shibboleth email header, '" + emailHeader + "'='" + email
                                    + "', is locked to another netid: '" + eperson
                                    .getNetid() + "'. This might be a possible hacking attempt to steal another users " +
                                    "credentials. If the user's netid has changed you will need to manually change it to the " +
                                    "correct value or unset it in the database.");
                    eperson = null;
                }
            }
        }

        // 3) Last, check to see if tomcat is passing a user.
        if (eperson == null && isUsingTomcatUser) {
            String email = request.getRemoteUser();

            if (email != null) {
                foundRemoteUser = true;
                email = email.toLowerCase();
                eperson = ePersonService.findByEmail(context, email);

                if (eperson == null) {
                    log.info("Unable to identify EPerson based upon Tomcat's remote user: '" + email + "'.");
                } else {
                    log.info("Identified EPerson based upon Tomcat's remote user: '" + email + "'.");
                }

                if (eperson != null && eperson.getNetid() != null) {
                    // If the user has a netID it has been locked to that netid, don't let anyone else try and steal
                    // the account.
                    log.error(
                            "The identified EPerson based upon Tomcat's remote user, '" + email + "', is locked to " +
                                    "another netid: '" + eperson
                                    .getNetid() + "'. This might be a possible hacking attempt to steal another users " +
                                    "credentials. If the user's netid has changed you will need to manually change it to the " +
                                    "correct value or unset it in the database.");
                    eperson = null;
                }
            }
        }

        if (!foundNetID && !foundEmail && !foundRemoteUser) {
            log.error(
                    "Shibboleth authentication was not able to find a NetId, Email, or Tomcat Remote user for which to " +
                            "indentify a user from.");
        }


        return eperson;
    }

}
