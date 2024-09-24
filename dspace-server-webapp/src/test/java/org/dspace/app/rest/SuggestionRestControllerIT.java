/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for the {@link org.dspace.app.rest.repository.SuggestionRestController}
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class SuggestionRestControllerIT extends AbstractControllerIntegrationTest {

    private Item publicItem;
    private Collection col;
    private final String SUBJECT_VALUE = "test subject";

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        // 2. Create item and add it to the collection
        publicItem = ItemBuilder.createItem(context, col)
                .withMetadata("dc", "subject", null, SUBJECT_VALUE )
                .build();

        context.restoreAuthSystemState();
    }

    /**
     * Should return formatted suggestions in the VocabularyEntryRest objects
     */
    @Test
    public void testSearchBySubjectAcSolrIndex() throws Exception {
        // substring = find only by the `test` value
        getClient().perform(get("/api/suggestions?autocompleteCustom=solr-subject_ac&searchValue=" +
                        SUBJECT_VALUE.substring(0, 4)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._embedded.vocabularyEntryRests", Matchers.hasItem(
                        allOf(
                                hasJsonPath("$.display", is(SUBJECT_VALUE)),
                                hasJsonPath("$.value", is(SUBJECT_VALUE)),
                                hasJsonPath("$.type", is("vocabularyEntry"))
                ))));
    }

    /**
     * Should return no suggestions
     */
    @Test
    public void testSearchBySubjectAcSolrIndex_noResults() throws Exception {
        // substring = find only by the `test` value
        getClient().perform(get("/api/suggestions?autocompleteCustom=solr-subject_ac&searchValue=" +
                        "no such subject"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.page.totalElements", is(0)))
                .andExpect(jsonPath("$._embedded.vocabularyEntryRests").doesNotExist());
    }
}
