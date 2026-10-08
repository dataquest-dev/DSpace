/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static com.jayway.jsonpath.JsonPath.read;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.dspace.app.rest.matcher.ProcessMatcher;
import org.dspace.app.rest.matcher.ScriptMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.ProcessBuilder;
import org.dspace.content.ProcessStatus;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.scripts.service.ScriptService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the REST registration and permissions of the {@code orcid-authority-assign} script.
 */
public class OrcidAuthorityAssignScriptIT extends AbstractControllerIntegrationTest {

    private static final String SCRIPT_NAME = "orcid-authority-assign";
    private static final String SCRIPT_URL = "/api/system/scripts/" + SCRIPT_NAME;
    private static final String PROCESSES_URL = SCRIPT_URL + "/processes";

    @Autowired
    private ScriptService scriptService;

    @Test
    public void adminFindsTheScript() throws Exception {
        ScriptConfiguration configuration = scriptService.getScriptConfiguration(SCRIPT_NAME);
        String token = getAuthToken(admin.getEmail(), password);

        getClient(token).perform(get(SCRIPT_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", ScriptMatcher.matchScript(SCRIPT_NAME,
                                                                           configuration.getDescription())));
        getClient(token).perform(get("/api/system/scripts").param("size", "100"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.scripts", hasItem(
                            ScriptMatcher.matchScript(SCRIPT_NAME, configuration.getDescription()))));
    }

    @Test
    public void anonymousCannotFindOrStartTheScript() throws Exception {
        getClient().perform(get(SCRIPT_URL))
                   .andExpect(status().isUnauthorized());
        getClient().perform(multipart(PROCESSES_URL))
                   .andExpect(status().isUnauthorized());
    }

    @Test
    public void nonAdminCannotFindOrStartTheScript() throws Exception {
        String token = getAuthToken(eperson.getEmail(), password);

        getClient(token).perform(get(SCRIPT_URL))
                        .andExpect(status().isForbidden());
        getClient(token).perform(get("/api/system/scripts").param("size", "100"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(not(containsString(SCRIPT_NAME))));
        getClient(token).perform(multipart(PROCESSES_URL))
                        .andExpect(status().isForbidden());
    }

    @Test
    public void adminRunsTheScript() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        AtomicReference<Integer> processId = new AtomicReference<>();

        try {
            getClient(token).perform(multipart(PROCESSES_URL))
                            .andExpect(status().isAccepted())
                            .andExpect(jsonPath("$", is(ProcessMatcher.matchProcess(SCRIPT_NAME,
                                String.valueOf(admin.getID()), Collections.emptyList(), ProcessStatus.COMPLETED))))
                            .andDo(result -> processId.set(read(result.getResponse().getContentAsString(),
                                                                "$.processId")));
        } finally {
            ProcessBuilder.deleteProcess(processId.get());
        }
    }
}
