/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.dspace.app.rest.security.clarin.ShibHeaders;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * This class will filter /api/authn/login requests to try and authenticate them. Keep in mind, this filter runs *after*
 * StatelessAuthenticationFilter (which looks for authentication data in the request itself). So, in some scenarios
 * (e.g. after a Shibboleth login) the StatelessAuthenticationFilter does the actual authentication, and this Filter
 * just ensures the auth token (JWT) is sent back in an Authorization header.
 *
 * @author Frederic Van Reet (frederic dot vanreet at atmire dot com)
 * @author Tom Desair (tom dot desair at atmire dot com)
 */
public class StatelessLoginFilter extends AbstractAuthenticationProcessingFilter {
    private static final Logger log = LoggerFactory.getLogger(StatelessLoginFilter.class);
    private static final String USER_WITHOUT_EMAIL_EXCEPTION = "UserWithoutEmailException";

    protected AuthenticationManager authenticationManager;

    protected RestAuthenticationService restAuthenticationService;

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    public void afterPropertiesSet() {
    }

    public StatelessLoginFilter(String url, AuthenticationManager authenticationManager,
                                RestAuthenticationService restAuthenticationService) {
        super(new AntPathRequestMatcher(url));
        this.authenticationManager = authenticationManager;
        this.restAuthenticationService = restAuthenticationService;
    }

    /**
     * Attempt to authenticate the user by using Spring Security's AuthenticationManager.
     * The AuthenticationManager will delegate this task to one or more AuthenticationProvider classes.
     * <P>
     * For DSpace, our custom AuthenticationProvider is {@link EPersonRestAuthenticationProvider}, so that
     * is the authenticate() method which is called below.
     *
     * @param req current request
     * @param res current response
     * @return a valid Spring Security Authentication object if authentication succeeds
     * @throws AuthenticationException if authentication fails
     * @see EPersonRestAuthenticationProvider
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest req,
                                                HttpServletResponse res) throws AuthenticationException,
            ServletException, IOException {

        String user = req.getParameter("user");
        String password = req.getParameter("password");

        // If the Idp doesn't send the email in the request header, send the redirect order to the FE for the user
        // to fill in the email.
//        String emailHeader = configurationService.getProperty("authentication-shibboleth.email-header");
        // For testing
        String emailHeader = "";
        if (StringUtils.isBlank(emailHeader)) {
            this.redirectToStaticPage(req, res);
            return null;
        }

        // Attempt to authenticate by passing user & password (if provided) to AuthenticationProvider class(es)
        // NOTE: This method will check if the user was already authenticated by StatelessAuthenticationFilter,
        // and, if so, just refresh their token.
        return authenticationManager.authenticate(new DSpaceAuthentication(user, password));
    }

    /**
     * If the above attemptAuthentication() call was successful (no authentication error was thrown),
     * then this method will take the returned {@link DSpaceAuthentication} class (which includes all
     * the data from the authenticated user) and add the authentication data to the response.
     * <P>
     * For DSpace, this is calling our {@link org.dspace.app.rest.security.jwt.JWTTokenRestAuthenticationServiceImpl}
     * in order to create a JWT based on the authentication data & send that JWT back in the response.
     *
     * @param req current request
     * @param res response
     * @param chain FilterChain
     * @param auth Authentication object containing info about user who had a successful authentication
     * @throws IOException
     * @throws ServletException
     * @see org.dspace.app.rest.security.jwt.JWTTokenRestAuthenticationServiceImpl
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain,
                                            Authentication auth) throws IOException, ServletException {

        DSpaceAuthentication dSpaceAuthentication = (DSpaceAuthentication) auth;
        log.debug("Authentication successful for EPerson {}", dSpaceAuthentication.getName());
        restAuthenticationService.addAuthenticationDataForUser(req, res, dSpaceAuthentication, false);
    }

    protected void redirectToStaticPage(HttpServletRequest req,
                                            HttpServletResponse res) throws IOException, ServletException {
        String authenticateHeaderValue = restAuthenticationService.getWwwAuthenticateHeaderValue(req, res);

        // Load header keys from cfg
        String netidHeader = configurationService.getProperty("authentication-shibboleth.netid-header");
        String emailHeader = configurationService.getProperty("authentication-shibboleth.email-header");
        String fnameHeader = configurationService.getProperty("authentication-shibboleth.firstname-header");
        String lnameHeader = configurationService.getProperty("authentication-shibboleth.lastname-header");

        // Store header values in the ShibHeaders because of String issues.
        ShibHeaders shib_headers = new ShibHeaders(req);
//        String netid = shib_headers.get_single(netidHeader);
//        String email = shib_headers.get_single(emailHeader);
//        String fname = shib_headers.get_single(fnameHeader);
//        String lname = shib_headers.get_single(lnameHeader);

        // For testing
        String netid = "123456";
        String email = "";
        String fname = "Marcel";
        String lname = "Pospisil";

        // Create a new eperson with netid, firstname, lastname
        // Create token
        // Set eperson for the token

        // Send the token in the request

        // Add header values to the error message to retrieve them in the FE. That headers are needed for the
        // next processing.
        String separator = ",";
        String[] headers = new String[] {USER_WITHOUT_EMAIL_EXCEPTION, netid, email, fname, lname};
        String errorMessage = StringUtils.join(headers, separator);

        res.setHeader("WWW-Authenticate", authenticateHeaderValue);
        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, errorMessage);
    }

    /**
     * If the above attemptAuthentication() call was unsuccessful, then ensure that the response is a 401 Unauthorized
     * AND it includes a WWW-Authentication header. We use this header in DSpace to return all the enabled
     * authentication options available to the UI (along with the path to the login URL for each option)
     * @param request current request
     * @param response current response
     * @param failed exception that was thrown by attemptAuthentication()
     * @throws IOException
     * @throws ServletException
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response, AuthenticationException failed)
            throws IOException, ServletException {

        String authenticateHeaderValue = restAuthenticationService.getWwwAuthenticateHeaderValue(request, response);

        response.setHeader("WWW-Authenticate", authenticateHeaderValue);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed!");
        log.error("Authentication failed (status:{})",
                  HttpServletResponse.SC_UNAUTHORIZED, failed);
    }

}
