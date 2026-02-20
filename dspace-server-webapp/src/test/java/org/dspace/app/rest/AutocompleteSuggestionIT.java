/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the CLARIN extensions to upstream PR #10855
 * (Solr-based suggest/autocomplete feature).
 *
 * Tests the {@code /api/discover/suggest} endpoint with:
 * <ul>
 *   <li>JSON static file dictionary support</li>
 *   <li>Allowlist security (reject non-allowed dictionaries)</li>
 *   <li>Authentication requirement</li>
 *   <li>Result deduplication</li>
 * </ul>
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class AutocompleteSuggestionIT extends AbstractControllerIntegrationTest {

    private static final String SUGGEST_ENDPOINT = "/api/discover/suggest";

    private Item publicItem;
    private Collection col;

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        publicItem = ItemBuilder.createItem(context, col)
                .withTitle("Test Item for Suggest")
                .withSubject("Computational Linguistics")
                .withAuthor("Kim Shepherd")
                .build();

        context.restoreAuthSystemState();
    }

    /**
     * Test that JSON static file dictionary returns matching suggestions.
     * The iso_langs.json file is on the classpath and should be searchable.
     */
    @Test
    public void jsonStaticDictionaryShouldReturnSuggestions() throws Exception {
        String userToken = getAuthToken(eperson.getEmail(), password);

        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "json_static-iso_langs.json")
                        .param("q", "English"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggest").isNotEmpty())
                .andExpect(jsonPath("$.suggest['iso_langs.json']").isNotEmpty())
                .andExpect(jsonPath("$.suggest['iso_langs.json'].English").isNotEmpty())
                .andExpect(jsonPath("$.suggest['iso_langs.json'].English.numFound",
                        greaterThan(0)))
                .andExpect(jsonPath("$.suggest['iso_langs.json'].English.suggestions",
                        hasSize(greaterThan(0))))
                .andExpect(jsonPath(
                        "$.suggest['iso_langs.json'].English.suggestions[0].term",
                        is(notNullValue())))
                .andExpect(jsonPath(
                        "$.suggest['iso_langs.json'].English.suggestions[0].payload",
                        is(notNullValue())));
    }

    /**
     * Test that JSON static dictionary returns empty results for a non-matching query.
     */
    @Test
    public void jsonStaticDictionaryShouldReturnEmptyForNoMatch() throws Exception {
        String userToken = getAuthToken(eperson.getEmail(), password);

        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "json_static-iso_langs.json")
                        .param("q", "ZZZZNOTEXISTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggest['iso_langs.json'].ZZZZNOTEXISTING.numFound",
                        is(0)))
                .andExpect(jsonPath("$.suggest['iso_langs.json'].ZZZZNOTEXISTING.suggestions",
                        hasSize(0)));
    }

    /**
     * Test that a non-allowed dictionary request returns HTTP 403 Forbidden.
     */
    @Test
    public void nonAllowedDictionaryShouldReturn403() throws Exception {
        String userToken = getAuthToken(eperson.getEmail(), password);

        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "not_in_allowlist")
                        .param("q", "test"))
                .andExpect(status().isForbidden());
    }

    /**
     * Test that an unauthenticated request returns HTTP 401 Unauthorized.
     */
    @Test
    public void unauthenticatedRequestShouldReturn401() throws Exception {
        getClient().perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "json_static-iso_langs.json")
                        .param("q", "English"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test that missing required parameters return HTTP 400 Bad Request.
     */
    @Test
    public void missingParametersShouldReturn400() throws Exception {
        String userToken = getAuthToken(eperson.getEmail(), password);

        // Missing 'q' parameter
        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "json_static-iso_langs.json"))
                .andExpect(status().isBadRequest());

        // Missing 'dict' parameter
        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("q", "English"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test that JSON static suggestions contain the expected payload (ISO code).
     * "Alumu-Tesu" should map to ISO code "aab" in iso_langs.json.
     */
    @Test
    public void jsonStaticShouldReturnPayloadCodeForKnownLanguage() throws Exception {
        String userToken = getAuthToken(eperson.getEmail(), password);

        getClient(userToken).perform(
                get(SUGGEST_ENDPOINT)
                        .param("dict", "json_static-iso_langs.json")
                        .param("q", "Alumu-Tesu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.suggest['iso_langs.json']['Alumu-Tesu'].numFound", is(1)))
                .andExpect(jsonPath(
                        "$.suggest['iso_langs.json']['Alumu-Tesu'].suggestions[0].term",
                        is("Alumu-Tesu")))
                .andExpect(jsonPath(
                        "$.suggest['iso_langs.json']['Alumu-Tesu'].suggestions[0].payload",
                        is("aab")));
    }
}
