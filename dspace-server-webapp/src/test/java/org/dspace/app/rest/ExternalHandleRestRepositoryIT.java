/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.matcher.ExternalHandleMatcher;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.ClarinHandleBuilder;
import org.dspace.handle.external.ExternalHandleConstants;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.services.ConfigurationService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;

/**
 * Integration test class for the ExternalHandleRestRepository
 */
public class ExternalHandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    HandleClarinService handleClarinService;

    @Autowired
    ConfigurationService configurationService;

    List<org.dspace.handle.Handle> handlesWithMagicURLs = new ArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper();

    private final String urlBlacklistConfig = "shortener.post.url.blacklist.regexps";
    private final String hostBlacklistConfig = "shortener.post.host.blacklist.regexps";
    private static final String TEST_URL = "https://example.org/target";
    private String[] urlBlacklist;
    private String[] hostBlacklist;

    @Before
    public void setup() throws SQLException, AuthorizeException {
        urlBlacklist = configurationService.getArrayProperty(urlBlacklistConfig);
        hostBlacklist = configurationService.getArrayProperty(hostBlacklistConfig);
        context.turnOffAuthorisationSystem();

        List<String> magicURLs = this.createMagicURLs();

        // create Handles with magicURLs
        int index = 0;
        for (String magicURL : magicURLs) {
            // create Handle

            org.dspace.handle.Handle handle = ClarinHandleBuilder
                    .createHandle(context, "123/" + index, magicURL)
                    .build();
            this.handlesWithMagicURLs.add(handle);
            index++;
        }

        context.commit();
        context.restoreAuthSystemState();
    }

    @After
    public void cleanUp() {
        configurationService.setProperty(urlBlacklistConfig, urlBlacklist);
        configurationService.setProperty(hostBlacklistConfig, hostBlacklist);
    }

    @Test
    public void findAllExternalHandles() throws Exception {
        // call endpoint which should return external handles
        List<Handle> expectedExternalHandles =
                this.handleClarinService.convertHandleWithMagicToExternalHandle(this.handlesWithMagicURLs);

        // expectedExternalHandles should not be empty
        Assert.assertFalse(ObjectUtils.isEmpty(expectedExternalHandles));
        Handle externalHandle = expectedExternalHandles.get(0);

        getClient().perform(get("/api/services/handles/magic"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andExpect(jsonPath("$", ExternalHandleMatcher.matchListOfExternalHandles(
                        expectedExternalHandles
                )))
        ;
    }

    @Test
    public void updateHandle() throws Exception {
        org.dspace.handle.Handle handleToUpdate = this.handlesWithMagicURLs.get(0);
        String handle = handleToUpdate.getHandle();

        String repositoryName = configurationService.getProperty("dspace.name");
        String token = "token0";
        String updatedMagicURL = "@magicLindat@@magicLindat@" + repositoryName +
                "@magicLindat@@magicLindat@@magicLindat@@magicLindat@@magicLindat@@magicLindat@" + token +
                "@magicLindat@https://lindat.mff.cuni.cz/#!/services/pmltq/";
        Handle updatedHandle = new Handle(handle, updatedMagicURL);

        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(authTokenAdmin).perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(updatedHandle))
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(updatedHandle.url)))
        ;
    }

    @Test
    public void coreHandlesEndpointHidesToken() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        String hiddenUrl = handle.getUrl().replace("token0", "");
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/handles/" + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(hiddenUrl)));

        getClient(adminToken).perform(get("/api/core/handles").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.handles[*].url", hasItem(hiddenUrl)))
                .andExpect(content().string(not(containsString(ExternalHandleConstants.MAGIC_BEAN + "token"))));
    }

    @Test
    public void tokenSetByAdminIsHiddenAndWorksForUpdate() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        patchUrlAndCheckToken(handle, handle.getUrl().replace("token0", "adminToken"), "adminToken");

        allowAnyUrl();
        putHandle(Map.of("handle", handle.getHandle(), "token", "adminToken", "url", TEST_URL));
    }

    @Test
    public void adminSaveOfHiddenUrlKeepsToken() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        // admin saves the url exactly as the REST API shows it
        patchUrlAndCheckToken(handle, handle.getUrl().replace("token0", ""), "token0");

        allowAnyUrl();
        putHandle(Map.of("handle", handle.getHandle(), "token", "token0", "url", TEST_URL));
    }

    @Test
    public void updateWithWrongTokenIsNotFound() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        allowAnyUrl();

        Map<String, String> update = Map.of("handle", handle.getHandle(), "token", "wrongToken", "url", TEST_URL);
        getClient().perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(update))
                .contentType(contentType))
                .andExpect(status().isNotFound());

        // the stored token still works, so nothing was changed
        putHandle(Map.of("handle", handle.getHandle(), "token", "token0", "url", TEST_URL));
    }

    @Test
    public void updateOfHandleWithoutTokenIsNotFound() throws Exception {
        String urlWithoutToken = handlesWithMagicURLs.get(0).getUrl().replace("token0", "");
        context.turnOffAuthorisationSystem();
        org.dspace.handle.Handle handle = ClarinHandleBuilder
                .createHandle(context, "123/notoken", urlWithoutToken)
                .build();
        context.commit();
        context.restoreAuthSystemState();
        getClient(getAuthToken(admin.getEmail(), password)).perform(get("/api/core/handles/" + handle.getID()))
                .andExpect(status().isOk());

        allowAnyUrl();
        Map<String, String> update = Map.of("handle", handle.getHandle(), "token", "token0", "url", TEST_URL);
        getClient().perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(update))
                .contentType(contentType))
                .andExpect(status().isNotFound());
    }

    /**
     * Update the url as admin, then check that the token is stored but is not in the response.
     */
    private void patchUrlAndCheckToken(org.dspace.handle.Handle handle, String url, String token) throws Exception {
        Map<String, String> values = Map.of("handle", handle.getHandle(), "url", url, "archive", "false");
        getClient(getAuthToken(admin.getEmail(), password))
                .perform(patch("/api/core/handles/" + handle.getID())
                        .content(getPatchContent(List.of(new ReplaceOperation("/updateHandle", values))))
                        .contentType(javax.ws.rs.core.MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(token))));
        Assert.assertTrue("Token should be stored in the database",
                handleClarinService.findByHandle(context, handle.getHandle()).getUrl().contains(token));
    }

    private void putHandle(Map<String, String> update) throws Exception {
        getClient().perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(update))
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(update.get("url"))));
    }

    private void allowAnyUrl() {
        configurationService.setProperty(urlBlacklistConfig, null);
        configurationService.setProperty(hostBlacklistConfig, null);
    }

    private List<String> createMagicURLs() {
        // External handle attributes
        String url = "url";
        String title = "title";
        String repository = "repository";
        String submitDate = "submitDate";
        String reporteMail = "reporteMail";
        String datasetName = "datasetName";
        String datasetVersion = "datasetVersion";
        String query = "query";
        String token = "token";
        String subprefix = "subprefix";

        List<String> magicURLs = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            // create mock object
            String magicURL =
                            ExternalHandleConstants.MAGIC_BEAN + title + i +
                            ExternalHandleConstants.MAGIC_BEAN + repository + i +
                            ExternalHandleConstants.MAGIC_BEAN + submitDate + i +
                            ExternalHandleConstants.MAGIC_BEAN + reporteMail + i +
                            ExternalHandleConstants.MAGIC_BEAN + datasetName + i +
                            ExternalHandleConstants.MAGIC_BEAN + datasetVersion + i +
                            ExternalHandleConstants.MAGIC_BEAN + query + i +
                            ExternalHandleConstants.MAGIC_BEAN + token + i +
                            ExternalHandleConstants.MAGIC_BEAN + url + i;
            // add mock object to the magicURLs
            magicURLs.add(magicURL);
        }

        return magicURLs;
    }
}
