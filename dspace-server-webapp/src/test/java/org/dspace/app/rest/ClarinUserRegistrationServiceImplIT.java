package org.dspace.app.rest;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.ClarinUserRegistrationBuilder;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ClarinUserRegistrationServiceImplIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinUserRegistrationService clarinUserRegistrationService;

    @Test
    public void testFind() throws Exception {
        context.turnOffAuthorisationSystem();
        ClarinUserRegistration clarinUserRegistration = ClarinUserRegistrationBuilder
                .createClarinUserRegistration(context).build();
        //find created handle
        Assert.assertEquals(clarinUserRegistration, clarinUserRegistrationService
                .find(context, clarinUserRegistration.getID()));
        ClarinUserRegistrationBuilder.deleteClarinUserRegistration(clarinUserRegistration.getID());
        Assert.assertNull(clarinUserRegistration);
        context.restoreAuthSystemState();
    }
}
