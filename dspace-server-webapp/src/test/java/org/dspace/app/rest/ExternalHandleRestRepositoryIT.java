package org.dspace.app.rest;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.handle.service.HandleClarinService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ExternalHandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    HandleClarinService handleClarinService;

    @Before
    public void setup() {
        context.turnOffAuthorisationSystem();

        handleClarinService.createHandle()
        // create external Handles

        context.restoreAuthSystemState();
    }


    @Test
    public void findAllExternalHandles() throws Exception {
        Assert.assertNotNull("ff");
    }

    @Test
    public void shortenHandle() throws Exception {
        Assert.assertNotNull("ff");
    }

    @Test
    public void updateHandle() throws Exception {
        Assert.assertNotNull("ff");
    }
}
