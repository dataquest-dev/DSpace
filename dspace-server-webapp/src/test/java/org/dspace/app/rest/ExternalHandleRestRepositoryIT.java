/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.dspace.app.rest.repository.ExternalHandleRestRepository;
import org.dspace.app.rest.repository.RandomStringGenerator;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.ClarinHandleBuilder;
import org.dspace.handle.HandlePlugin;
import org.dspace.handle.external.ExternalHandleConstants;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.services.ConfigurationService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.ObjectUtils;

/**
 * Integration test class for the ExternalHandleRestRepository
 */
public class ExternalHandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    HandleClarinService handleClarinService;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    private ExternalHandleRestRepository externalHandleRestRepository;

    private String prefix;

    @SpyBean
    private RandomStringGenerator rnd;

    List<org.dspace.handle.Handle> handlesWithMagicURLs = new ArrayList<>();

    private final ObjectMapper mapper = new ObjectMapper();

    private List<org.dspace.handle.Handle> handlesBeforeTest;

    private final String urlBlacklistConfig = "shortener.post.url.blacklist.regexps";
    private final String hostBlacklistConfig = "shortener.post.host.blacklist.regexps";
    private static final String TEST_URL = "https://example.org/target";
    private static final String TEST_EMAIL = "submitter@example.org";
    // allowed by the default shortener.post.host.whitelist.regexps
    private static final String ALLOWED_URL = "https://lindat.cz/";
    private String[] urlBlacklist;
    private String[] hostBlacklist;

    @Before
    public void setup() throws SQLException, AuthorizeException {
        prefix  = configurationService.getProperty("shortener.handle.prefix") + "/" ;
        urlBlacklist = configurationService.getArrayProperty(urlBlacklistConfig);
        hostBlacklist = configurationService.getArrayProperty(hostBlacklistConfig);
        context.turnOffAuthorisationSystem();

        handlesBeforeTest = handleClarinService.findAll(context);

        List<String> magicURLs = this.createMagicURLs();

        // create Handles with magicURLs
        int index = 0;
        for (String magicURL : magicURLs) {
            // create Handle

            org.dspace.handle.Handle handle = ClarinHandleBuilder
                    .createHandle(context, prefix + index, magicURL)
                    .build();
            this.handlesWithMagicURLs.add(handle);
            index++;
        }

        context.commit();
        context.restoreAuthSystemState();
    }

    @After
    public void cleanUp() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        for (org.dspace.handle.Handle handle : handleClarinService.findAll(context)) {
            if (!handlesBeforeTest.contains(handle)) {
                handleClarinService.delete(context, handle);
            }
        }
        context.commit();
        context.restoreAuthSystemState();

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
        String newUrl = "https://lindat.mff.cuni.cz/#!/services/pmltq/";


        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        Map<String, String> updatedHandle = Map.of(
                "handle", handle,
                "url", newUrl,
                "token", "token0"
        );
        updateHandle(authTokenAdmin, updatedHandle);
    }

    // try create handle more than once
    @Test
    public void simulateHittingAnExistingHandleButThenSucceed() throws Exception {
        when(rnd.generate(4)).thenReturn("0")
                .thenReturn("1").thenReturn("2").thenReturn("3");
        MvcResult result = createHandle(null, Map.of(
                "url", "https://lindat.mff.cuni.cz/#!/services/pmltq/",
                "title", "title",
                "reportemail", TEST_EMAIL
        ));
        Map<String, String> handleResponse = mapper.readValue(result.getResponse().getContentAsString(),
                Map.class);
        Assert.assertEquals("Handle should be created", handleResponse.get("handle"),
                HandlePlugin.getCanonicalHandlePrefix() + prefix + "3");
    }

    @Test
    public void createAndUpdateHandleByAdmin() throws Exception {

        String authToken = getAuthToken(admin.getEmail(), password);

        String url = "https://lindat.mff.cuni.cz/#!/services/pmltq/";
        Map<String, String> handleJson = Map.of(
            "url", url,
            "title", "title",
            "reportemail", TEST_EMAIL,
            "subprefix", "subprefix",
            "datasetName", "datasetName",
            "datasetVersion", "datasetVersion",
            "query", "query"
        );
        // create new handle
        MvcResult createResult = createHandle(authToken, handleJson);

        Map<String, String> handleResponse = mapper.readValue(createResult.getResponse().getContentAsString(),
                Map.class);
        String handle = handleResponse.get("handle");

        Assert.assertTrue("Handle contains subprefix", handle.contains("subprefix"));

        String token = handleResponse.get("token");
        String newUrl = "https://lindat.cz/#!/services/pmltq/";
        Map<String, String> updatedHandleJson = Map.of(
            "handle", handle,
            "url", newUrl,
            "token", token,
            "title", "IGNORE",
            "reportemail", "IGNORE",
            "subprefix", "IGNORE",
            "datasetName", "IGNORE",
            "datasetVersion", "IGNORE",
            "query", "IGNORE"
        );

        // remember how many handles we have
        int count = handleClarinService.count(context);

        // update the handle
        MvcResult result = updateHandle(authToken, updatedHandleJson);
        Assert.assertFalse("Only the URL should be updated", result.getResponse().getContentAsString().contains(
                "IGNORE"));
        Assert.assertEquals("Update should not create new handles.", count, handleClarinService.count(context));
    }

    // create/update no login
    @Test
    public void createHandleAsAnonymous() throws Exception {
        String authToken = null;

        String url = "https://lindat.mff.cuni.cz/#!/services/pmltq/";
        Map<String, String> handleJson = Map.of(
                "url", url,
                "title", "title",
                "reportemail", TEST_EMAIL,
                "subprefix", "subprefix",
                "datasetName", "datasetName",
                "datasetVersion", "datasetVersion",
                "query", "query"
        );
        // create new handle
        MvcResult createResult = createHandle(authToken, handleJson);

        Map<String, String> handleResponse = mapper.readValue(createResult.getResponse().getContentAsString(),
                Map.class);
        String handle = handleResponse.get("handle");
        String token = handleResponse.get("token");
        String newUrl = "https://lindat.cz/#!/services/pmltq/";
        Map<String, String> updatedHandleJson = Map.of(
                "handle", handle,
                "url", newUrl,
                "token", token
        );

        // remember how many handles we have
        int count = handleClarinService.count(context);

        // update the handle
        updateHandle(authToken, updatedHandleJson);

        Assert.assertEquals("Update should not create new handles.", count, handleClarinService.count(context));
    }

    // update works only with token
    @Test
    public void updateWithValidTokenOnly() throws Exception {
        String hdl = prefix + "0";
        Map<String, String> updateWithInvalid = Map.of(
                "handle", hdl,
                "token", "INVALID",
                "url", "https://lindat.cz"
        );
        getClient().perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(updateWithInvalid))
                .contentType(contentType))
                .andExpect(status().isNotFound());

        Map<String, String> updateWithValid = Map.of(
                "handle", HandlePlugin.getCanonicalHandlePrefix() + hdl,
                "token", "token0",
                "url", "https://lindat.cz"
        );
        MvcResult result = updateHandle(null, updateWithValid);
        Map<String, String> map = mapper.readValue(result.getResponse().getContentAsString(), Map.class);

        // did we get a token that actually works?
        Map<String, String> updateWithValid2 = Map.of(
                "handle", HandlePlugin.getCanonicalHandlePrefix() + hdl,
                "token", map.get("token"),
                "url", "https://lindat.cz/test1"
        );

        updateHandle(null, updateWithValid2);

    }

    // the token or magic url does not leak (it has the token)
    @Test
    public void noTokenInGetResponse() throws Exception {
        MvcResult result = getClient().perform(get("/api/services/handles/magic"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/json;charset=UTF-8")))
                .andReturn()
        ;
        String content = result.getResponse().getContentAsString();
        Assert.assertFalse("token key should not be in the response", content.contains("\"token\":"));
        Assert.assertFalse("Token should not be in the response", content.contains("token0"));
    }

    @Test
    public void coreHandlesEndpointHidesToken() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        String hiddenUrl = handle.getUrl().replace("token0", "");
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/handles/" + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is(hiddenUrl)));

        // the "sort" param is a filter: only handles with the shortener prefix, whatever else is in the DB
        getClient(adminToken).perform(get("/api/core/handles").param("sort", "handle:" + prefix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(handlesWithMagicURLs.size())))
                .andExpect(jsonPath("$._embedded.handles[*].url", hasItem(hiddenUrl)))
                .andExpect(content().string(not(containsString(ExternalHandleConstants.MAGIC_BEAN + "token"))));
    }

    @Test
    public void tokenSetByAdminIsHiddenAndWorksForUpdate() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        patchUrlAndCheckToken(handle, handle.getUrl().replace("token0", "adminToken"), "adminToken");

        allowAnyUrl();
        updateHandle(null, Map.of("handle", handle.getHandle(), "token", "adminToken", "url", TEST_URL));
    }

    @Test
    public void adminSaveOfHiddenUrlKeepsToken() throws Exception {
        org.dspace.handle.Handle handle = handlesWithMagicURLs.get(0);
        // admin saves the url exactly as the REST API shows it
        patchUrlAndCheckToken(handle, handle.getUrl().replace("token0", ""), "token0");

        allowAnyUrl();
        updateHandle(null, Map.of("handle", handle.getHandle(), "token", "token0", "url", TEST_URL));
    }

    @Test
    public void updateOfHandleWithoutTokenIsNotFound() throws Exception {
        String urlWithoutToken = handlesWithMagicURLs.get(0).getUrl().replace("token0", "");
        context.turnOffAuthorisationSystem();
        org.dspace.handle.Handle handle = ClarinHandleBuilder
                .createHandle(context, prefix + "notoken", urlWithoutToken)
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

    @Test
    public void noMagicInCreateResponse() throws Exception {
        String url = "https://lindat.mff.cuni.cz/#!/services/pmltq/";
        Map<String, String> handleJson = Map.of(
                "url", url,
                "title", "title",
                "reportemail", TEST_EMAIL,
                "subprefix", "subprefix",
                "datasetName", "datasetName",
                "datasetVersion", "datasetVersion",
                "query", "query"
        );
        MvcResult result = createHandle(null, handleJson);

        String content = result.getResponse().getContentAsString();
        Assert.assertFalse("magic bean should not be in the response",
                content.contains(ExternalHandleConstants.MAGIC_BEAN));
    }

    @Test
    public void hostBlacklist() throws Exception {
        configurationService.setProperty(urlBlacklistConfig, null);
        configurationService.setProperty(hostBlacklistConfig, ".*\\.com;.*\\.cz;.*\\.app");
        Assert.assertEquals(1, configurationService.getArrayProperty(hostBlacklistConfig).length);
        for (String tld : new String[] {".com", ".cz", ".app"}) {
            getClient().perform(post("/api/services/handles")
                            .content(mapper.writeValueAsBytes(Map.of(
                                    "url", "https://example" + tld,
                                    "title", "title",
                                    "reportemail", TEST_EMAIL
                            )))
                            .contentType(contentType))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test
    public void urlBlacklist() throws Exception {
        getClient().perform(post("/api/services/handles")
                        .content(mapper.writeValueAsBytes(Map.of(
                                "url", "https://junk",
                                "title", "title",
                                "reportemail", TEST_EMAIL
                        )))
                        .contentType(contentType))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void urlBlacklistRegexpWithRange() throws Exception {
        configurationService.setProperty(hostBlacklistConfig, null);
        configurationService.setProperty(urlBlacklistConfig, "http://.*;.*\\..{3\\,4}$,.*/wp-login$");
        Assert.assertEquals(2, configurationService.getArrayProperty(urlBlacklistConfig).length);
        List<String> shouldBeBlocked = List.of("http://junk", "https://example.com", "https://localhost:4000/wp-login");
        for (String url : shouldBeBlocked) {
            getClient().perform(post("/api/services/handles")
                            .content(mapper.writeValueAsBytes(Map.of(
                                    "url", url,
                                    "title", "title",
                                    "reportemail", TEST_EMAIL
                            )))
                            .contentType(contentType))
                    .andExpect(status().isBadRequest());
        }
        //should not be blocked
        createHandle(null, Map.of(
                "url", "https://example.cz",
                "title", "title",
                "reportemail", TEST_EMAIL
        ));
    }

    @Test
    public void createRejectsMagicDelimiterInMetadata() throws Exception {
        // the resolver reads the destination after the 9th delimiter of the stored record, i.e. the
        // text after the 8 delimiters injected in the title, so the host rules would not apply to it
        String title = "title" + ExternalHandleConstants.MAGIC_BEAN.repeat(8) + "https://evil.example/";
        postHandle(ALLOWED_URL, title, TEST_EMAIL)
                .andExpect(content().string(not(containsString("\"url\":\"https://evil.example/"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createRejectsUrlWithBackslashBeforeAt() throws Exception {
        // java.net.URL reads the allowed host after '@', browsers end the host at the backslash
        postHandle("https://evil.example\\@lindat.cz/", "title", TEST_EMAIL)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createRejectsNonHttpUrl() throws Exception {
        postHandle("file://lindat.cz/etc/passwd", "title", TEST_EMAIL)
                .andExpect(status().isBadRequest());
    }

    @Test
    public void hostRulesIgnoreCaseRootDotAndUnicodeSpelling() throws Exception {
        configurationService.setProperty(urlBlacklistConfig, null);
        configurationService.setProperty(hostBlacklistConfig, "blocked\\.example");
        // the last one is blocked.example in fullwidth letters, which browsers map to blocked.example
        for (String url : List.of("https://BLOCKED.example/", "https://blocked.example./",
                "https://ｂｌｏｃｋｅｄ.example/")) {
            postHandle(url, "title", TEST_EMAIL).andExpect(status().isBadRequest());
        }
        // with the default deny-all rules only the lowercase allow rule can let this host through
        configurationService.setProperty(urlBlacklistConfig, urlBlacklist);
        configurationService.setProperty(hostBlacklistConfig, hostBlacklist);
        createHandle(null, Map.of("url", "https://LINDAT.CZ/", "title", "title", "reportemail", TEST_EMAIL));
    }

    @Test
    public void createRejectsInvalidReportEmail() throws Exception {
        postHandle(ALLOWED_URL, "title", "not-an-email")
                .andExpect(status().isBadRequest());
    }

    @Test
    public void recordLongerThanUrlColumnIsRejected() throws Exception {
        // the stored record would exceed the handle.url column (varchar(8192) in the H2 schema)
        String longText = "a".repeat(8192);
        postHandle(ALLOWED_URL, longText, TEST_EMAIL)
                .andExpect(status().isBadRequest());
        getClient().perform(put("/api/services/handles")
                        .content(mapper.writeValueAsBytes(Map.of(
                                "handle", prefix + "0",
                                "token", "token0",
                                "url", ALLOWED_URL + longText
                        )))
                        .contentType(contentType))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void updateErrorDoesNotRevealDatabaseDetails() throws Exception {
        HandleClarinService failingService = mock(HandleClarinService.class);
        when(failingService.findByHandleAndMagicToken(any(), any(), any()))
                .thenThrow(new SQLException("synthetic database detail"));
        ReflectionTestUtils.setField(externalHandleRestRepository, "handleClarinService", failingService);
        try {
            getClient().perform(put("/api/services/handles")
                            .content(mapper.writeValueAsBytes(Map.of(
                                    "handle", prefix + "0",
                                    "token", "token0",
                                    "url", ALLOWED_URL
                            )))
                            .contentType(contentType))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(not(containsString("synthetic database detail"))));
        } finally {
            ReflectionTestUtils.setField(externalHandleRestRepository, "handleClarinService", handleClarinService);
        }
    }

    private ResultActions postHandle(String url, String title, String reportemail) throws Exception {
        return getClient().perform(post("/api/services/handles")
                .content(mapper.writeValueAsBytes(Map.of("url", url, "title", title, "reportemail", reportemail)))
                .contentType(contentType));
    }

    private MvcResult updateHandle(String authToken, Map<String, String> updatedHandleJson) throws Exception {
        return getClient(authToken).perform(put("/api/services/handles")
                        .content(mapper.writeValueAsBytes(updatedHandleJson))
                        .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle", anyOf(is(updatedHandleJson.get("handle")),
                        is(HandlePlugin.getCanonicalHandlePrefix() + updatedHandleJson.get("handle")))))
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.url", is(updatedHandleJson.get("url"))))
                .andReturn()
        ;
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

    private void allowAnyUrl() {
        configurationService.setProperty(urlBlacklistConfig, null);
        configurationService.setProperty(hostBlacklistConfig, null);
    }

    private MvcResult createHandle(String authToken, Map<String, String> handleJson) throws Exception {
        return getClient(authToken).perform(post("/api/services/handles")
                        .content(mapper.writeValueAsBytes(handleJson))
                        .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle", notNullValue()))
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.url", is(handleJson.get("url"))))
                .andReturn();
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
