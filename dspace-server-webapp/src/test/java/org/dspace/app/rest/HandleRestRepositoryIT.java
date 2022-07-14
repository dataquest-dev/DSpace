/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import org.dspace.app.rest.matcher.HandleMatcher;
import org.dspace.app.rest.model.patch.AddOperation;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
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

    private Collection col;
    private Item publicItem;

    public static final String HANDLES_ENDPOINT = "/api/core/handles/";

    @Autowired
    private ItemService itemService;

    @Autowired
    private HandleClarinService handleClarinService;

    @Before
    public void setup() {
        context.turnOffAuthorisationSystem();
        // 1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        // 2. Create item and add it to the collection
        publicItem = ItemBuilder.createItem(context, col)
                .withAuthor(AUTHOR)
                .build();

        context.restoreAuthSystemState();
    }

    @Test
    public void findAll() throws Exception {
        Handle handle = publicItem.getHandles().get(0);

        getClient().perform(get("/api/core/handles"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                    .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
                        HandleMatcher.matchHandleByKeys(handle)
                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString("/api/core/handles")));

        this.cleanHandles();
    }

    @Test
    public void findOne() throws Exception {
        Handle handle = publicItem.getHandles().get(0);

        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString("/api/core/handles")));

        this.cleanHandles();
    }

    //does not work again
//    @Test
//    public void deleteSuccess() throws Exception {
//        Handle handle = publicItem.getHandles().get(0);
//
//        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
//                .andExpect(status().isOk());
//        getClient(getAuthToken(admin.getEmail(), password))
//                .perform(delete(HANDLES_ENDPOINT + handle.getID()))
//                .andExpect(status().isNoContent());
//
//        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
//                .andExpect(status().isNotFound());
//
//        this.cleanHandles();
//    }

    // clean handles of the created Items, Communities, Collections because when is created
    // a new Item/Collection/Community in another test, the handle of the old Item/Collection/Community
    // lost DSpaceObject (dso is null) and it throws error in the HandleConverter
    private void cleanHandles() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();

        handleClarinService.delete(context, parentCommunity.getHandles().get(0));
        handleClarinService.delete(context, col.getHandles().get(0));
        handleClarinService.delete(context, publicItem.getHandles().get(0));

        context.restoreAuthSystemState();
    }

    @Test
    public void patchEditHandle() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        List<Operation> ops = new ArrayList<Operation>();
        Map<String, String> value = new HashMap<String, String>();
        value.put("value", "123");
        ops.add(new AddOperation("/edit", value));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.not(Matchers.is(
                        HandleMatcher.matchHandle(handle))
                )));
        handle.setHandle("123");
        getClient().perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle))
                ));

        this.cleanHandles();
    }

    @Test
    public void patchSetPrefix() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        List<Operation> ops = new ArrayList<Operation>();
        Map<String, String> value = new HashMap<String, String>();
        value.put("value", "123");
        ops.add(new ReplaceOperation("/set", value));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        this.cleanHandles();
    }
}
