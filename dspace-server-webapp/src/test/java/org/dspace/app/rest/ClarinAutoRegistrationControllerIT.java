package org.dspace.app.rest;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.hamcrest.Matchers;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import javax.ws.rs.core.MediaType;

import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinAutoRegistrationControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinUserRegistrationService clarinUserRegistrationService;
    @Autowired
    EPersonService ePersonService;

    @Test
    public void shouldRegisterNewEPerson() throws Exception {
        String netId = "123456";
        String email = "test@mail.epic";
        String firstname = "Test";
        String lastname = "Guy";

        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(tokenAdmin).perform(get("/api/autoregistration")
                        .header("SHIB-NETID", netId)
                        .header("SHIB-MAIL", email)
                        .header("SHIB-GIVENNAME", firstname)
                        .header("SHIB-SURNAME", lastname)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        EPerson ePerson = ePersonService.findByNetid(context, netId);
        assertTrue(Objects.nonNull(ePerson));
        assertEquals(ePerson.getEmail(), email);
        assertEquals(ePerson.getFirstName(), firstname);
        assertEquals(ePerson.getLastName(), lastname);

        List<ClarinUserRegistration> clarinUserRegistrationList =
                clarinUserRegistrationService.findByEPersonUUID(context, ePerson.getID());
        assertTrue(CollectionUtils.isNotEmpty(clarinUserRegistrationList));
        // There is only one user registration.
        ClarinUserRegistration clarinUserRegistration = clarinUserRegistrationList.get(0);
        assertTrue(Objects.nonNull(clarinUserRegistration));
        assertEquals(clarinUserRegistration.getEmail(), email);
    }
}
