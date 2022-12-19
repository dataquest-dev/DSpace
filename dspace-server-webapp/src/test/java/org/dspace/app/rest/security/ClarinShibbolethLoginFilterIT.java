/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.impl.CanChangePasswordFeature;
import org.dspace.app.rest.projection.DefaultProjection;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.clarin.ClarinVerificationToken;
import org.dspace.content.service.clarin.ClarinVerificationTokenService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.MediaType;
import java.util.Objects;

import static org.dspace.app.rest.security.clarin.ClarinShibbolethLoginFilter.MISSING_HEADERS_FROM_IDP;
import static org.dspace.app.rest.security.clarin.ClarinShibbolethLoginFilter.USER_WITHOUT_EMAIL_EXCEPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The test class for the customized Shibboleth Authentication Process.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk).
 */
public class ClarinShibbolethLoginFilterIT extends AbstractControllerIntegrationTest {

    public static final String[] SHIB_ONLY = {"org.dspace.authenticate.clarin.ClarinShibAuthentication"};

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    ClarinVerificationTokenService clarinVerificationTokenService;

    @Autowired
    EPersonService ePersonService;

    @Before
    public void setup() throws Exception {
        super.setUp();
        // Add a second trusted host for some tests
        configurationService.setProperty("rest.cors.allowed-origins",
                "${dspace.ui.url}, http://anotherdspacehost:4000");

        // Enable Shibboleth login for all tests
        configurationService.setProperty("plugin.sequence.org.dspace.authenticate.AuthenticationMethod", SHIB_ONLY);
    }

    /**
     * Test the IdP hasn't sent the `Shib-Identity-Provider` or `SHIB-NETID` header.
     */
    @Test
    public void shouldReturnMissingHeadersFromIdpExceptionBecauseOfMissingIdp() throws Exception {
        String netId = "123456";

        // Try to authenticate but the Shibboleth doesn't send the email in the header, so the user won't be registered
        // but the user will be redirected to the page where he will fill in the user email.
        getClient().perform(get("/api/authn/shibboleth")
                        .header("SHIB-NETID", netId))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason(MISSING_HEADERS_FROM_IDP));
    }

    /**
     * Test the IdP hasn't sent the `Shib-Identity-Provider` or `SHIB-NETID` header.
     */
    @Test
    public void shouldReturnMissingHeadersFromIdpExceptionBecauseOfMissingNetId() throws Exception {
        String idp = "Test Idp";

        // Try to authenticate but the Shibboleth doesn't send the email in the header, so the user won't be registered
        // but the user will be redirected to the page where he will fill in the user email.
        getClient().perform(get("/api/authn/shibboleth")
                        .header("Shib-Identity-Provider", idp))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason(MISSING_HEADERS_FROM_IDP));
    }

    /**
     * Test:
     * The IdP hasn't sent the `SHIB-EMAIL` header.
     * The request headers passed by IdP are stored into the `verification_token` table the `shib_headers` column.
     * The user is redirected to the page when he must fill his email.
     */
    @Test
    public void shouldReturnUserWithoutEmailException() throws Exception {
        String netId = "123456";
        String idp = "Test Idp";

        // Try to authenticate but the Shibboleth doesn't send the email in the header, so the user won't be registered
        // but the user will be redirected to the page where he will fill in the user email.
        getClient().perform(get("/api/authn/shibboleth")
                        .header("SHIB-NETID", netId)
                        .header("Shib-Identity-Provider", idp))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason(USER_WITHOUT_EMAIL_EXCEPTION + "," + netId));
    }

    /**
     * Test the authentication process:
     * 1. The IdP hasn't sent the `SHIB-EMAIL` header and the user is redirected to the page to fill in his email.
     * 2. Test to `ClarinAutoregistrationController.sendEmail` method to send the verification email to the users
     * email.
     * 3. Validate the users verification token and authenticate the user by the verification token. The user is
     * automatically registered and signed in.
     * 4. If the user is registered he is automatically signed in by the NETID which is passed from the IdP.
     * @throws Exception
     */
    @Test
    public void userFillInEmailAndShouldBeRegisteredByVerificationToken() throws Exception {
        String netId = "123456";
        String email = "test@mail.epic";
        String firstname = "Test";
        String lastname = "Buddy";
        String idp = "Test Idp";

        // Try to authenticate but the Shibboleth doesn't send the email in the header, so the user won't be registered
        // but the user will be redirected to the page where he will fill in the user email.
        getClient().perform(get("/api/authn/shibboleth")
                        .header("Shib-Identity-Provider", idp)
                        .header("SHIB-NETID", netId)
                        .header("SHIB-GIVENNAME", firstname)
                        .header("SHIB-SURNAME", lastname))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason(USER_WITHOUT_EMAIL_EXCEPTION + "," + netId));

        // Send the email with the verification token.
        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(tokenAdmin).perform(post("/api/autoregistration?netid=" + netId + "&email=" + email)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        // Load the created verification token.
        ClarinVerificationToken clarinVerificationToken = clarinVerificationTokenService.findByNetID(context, netId);
        assertTrue(Objects.nonNull(clarinVerificationToken));

        // Register the user by the verification token.
        getClient(tokenAdmin).perform(get("/api/autoregistration?verification-token=" +
                        clarinVerificationToken.getToken())
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        // Check if was created a user with such email and netid.
        EPerson ePerson = ePersonService.findByNetid(context, netId);
        assertTrue(Objects.nonNull(ePerson));
        assertEquals(ePerson.getEmail(), email);
        assertEquals(ePerson.getFirstName(), firstname);
        assertEquals(ePerson.getLastName(), lastname);

        // The user is registered now log him
        getClient().perform(get("/api/authn/shibboleth")
                        .header("Shib-Identity-Provider", idp)
                        .header("SHIB-NETID", netId)
                        .header("verification-token", clarinVerificationToken.getToken()))
                .andExpect(status().isOk());

        // Try to sign in the user by the email if the eperson exist
        getClient().perform(get("/api/authn/shibboleth")
                        .header("Shib-Identity-Provider", idp)
                        .header("SHIB-NETID", netId)
                        .header("SHIB-GIVENNAME", firstname)
                        .header("SHIB-SURNAME", lastname)
                        .header("SHIB-MAIL", email))
                .andExpect(status().isFound());

        // Try to sign in the user by the netid if the eperson exist
        getClient().perform(get("/api/authn/shibboleth")
                        .header("Shib-Identity-Provider", idp)
                        .header("SHIB-NETID", netId)
                        .header("SHIB-GIVENNAME", firstname)
                        .header("SHIB-SURNAME", lastname))
                .andExpect(status().isFound());
    }
}
