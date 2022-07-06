package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.handle.Handle;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;


import static java.nio.file.Paths.get;


public class HandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    private static final String AUTHOR = "Test author name";
    private Item publicItem;

    public static final String HANDLES_ENDPOINT = "/api/core/handles/";

    @Autowired
    private ItemService itemService;

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        // 2. Create item and add it to the collection
        publicItem = ItemBuilder.createItem(context, col)
                .withAuthor(AUTHOR)
                .build();
        context.restoreAuthSystemState();
    }

    @Test
    public void findAll() throws Exception {
        // Get handle from the item
//        Handle titleMetadataValue = this.g;

//        getClient().perform(get("/api/core/handles")
////                        .param("size", String.valueOf(100)))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(contentType))
//                .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
//                        MetadataValueMatcher.matchMetadataValueByKeys(titleMetadataValue.getValue(),
//                                titleMetadataValue.getLanguage(), titleMetadataValue.getAuthority(),
//                                titleMetadataValue.getConfidence(), titleMetadataValue.getPlace()))
//                ))
//                .andExpect(jsonPath("$._links.self.href",
//                        Matchers.containsString("/api/core/metadatavalues")))
//                .andExpect(jsonPath("$.page.size", is(100)));

    }

}
