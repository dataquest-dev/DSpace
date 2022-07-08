/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.dspace.app.rest.matcher.HandleMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.handle.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
        Handle handle = publicItem.getHandles().get(0);
        getClient().perform(get(HANDLES_ENDPOINT)
                    .param("size", String.valueOf(5)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
                        HandleMatcher.matchHandleByKeys(handle.getHandle(), handle.getResourceTypeId())
                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString(HANDLES_ENDPOINT)))
                .andExpect(jsonPath("$.page.size", is(5)));
    }

    @Test
    public void findOne() throws Exception {
        Handle handle = publicItem.getHandles().get(0);

        getClient().perform(patch(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString(HANDLES_ENDPOINT)));
    }

    @Test
    public void deleteSuccess() throws Exception {
        Handle handle = publicItem.getHandles().get(0);

        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk());
        getClient(getAuthToken(admin.getEmail(), password))
                .perform(delete(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isNoContent());

        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isNotFound());
    }

    //ako poslat objekt?
//    @Test
//    public void editWithId() throws  Exception {
//        Handle handle = publicItem.getHandles().get(0);
//        Integer id = handle.getID();
//        Handle newHandle = handleClarinService.createHandle(context, null);
//        getClient().perform(patch("/api/core/handles/" + id))
//               .andExpect(status().isOk())
//               .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
//                       HandleMatcher.matchHandleByKeys(handle.getHandle(), handle.getResourceTypeId())
//               )));
//
//
//    }
}
