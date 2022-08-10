package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HelpDeskControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    String HELP_DESK_MAIL = "test@test.com";

    @Test
    public void getHelpDeskEmail()
            throws Exception {

        getClient().perform(get("/api/help-desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mail", is(HELP_DESK_MAIL)));
    }
}
