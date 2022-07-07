package org.dspace.app.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.handle.service.HandleService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;


public class HandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    private static final String AUTHOR = "Test author name";
    private Item publicItem;

    public static final String HANDLES_ENDPOINT = "/api/core/handles/";

    @Autowired
    private ItemService itemService;

    @Autowired
    private HandleService handleService;



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
        // Get title metadata from the item
        getClient().perform(get("/api/core/handles"))
                .andExpect(status().isOk());
//                .andExpect(content().contentType(contentType));
//                .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
//                        HandleMatcher.matchHandleByKeys(handle.getHandle(),
//                                handle.getDSpaceObject(), handle.getResourceTypeId()))
//                ))
//                .andExpect(jsonPath("$._links.self.href",
//                        Matchers.containsString("/api/core/handles")))
//                .andExpect(jsonPath("$.page.size", is(100)));
    }

}
