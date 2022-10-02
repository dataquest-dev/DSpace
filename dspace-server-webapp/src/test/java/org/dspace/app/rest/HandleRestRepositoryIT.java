/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.dspace.app.rest.matcher.HandleMatcher;
import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.handle.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the {@link org.dspace.app.rest.repository.HandleRestRepository}
 * This class will include all the tests for the logic with regards to the
 * {@link org.dspace.app.rest.repository.HandleRestRepository}
 */
public class HandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    private static final String AUTHOR = "Test author name";
    private static final String HANDLES_ENDPOINT = "/api/core/handles/";

    private JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);

    private Collection col;
    private Item publicItem;

    @Autowired
    private ItemService itemService;

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private HandleClarinService handleClarinService;

    @Autowired
    private HandleService handleService;
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
        // findAll method in the HandleRestRepository returns handles ordered by creating the handle with ID = 1
        // should be returned at first
        Handle handle = handleClarinService.findAll(context).get(0);

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get("/api/core/handles")
                .param("size", String.valueOf(100)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                    .andExpect(jsonPath("$._embedded.handles", Matchers.hasItem(
                        HandleMatcher.matchHandle(handle)
                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString("/api/core/handles")))
                .andExpect(jsonPath("$.page.size", is(100)));;
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

        //Item handle was deleted in test
        //delete just Community and Collection
        context.turnOffAuthorisationSystem();
        handleClarinService.delete(context, parentCommunity.getHandles().get(0));
        handleClarinService.delete(context, col.getHandles().get(0));
        context.restoreAuthSystemState();
    }

    @Test
    public void patchUpdateInternalHandleWithoutHandle() throws  Exception {
        //handle: ""
        //archive: false
        Handle handle = publicItem.getHandles().get(0);
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode(""));
        values.put("url", jsonNodeFactory.textNode(null));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        //exception UnprocessableEntityException
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                .content(patchBody)
                .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect((status().is(422)));
        this.cleanHandles();
    }

    @Test
    public void patchUpdateInternalHandleWithoutArchive() throws  Exception {
        //handle: 123
        //archive: false
        Handle handle = publicItem.getHandles().get(0);
        String oldHandleStr = handle.getHandle();

        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode("123"));
        values.put("url", jsonNodeFactory.textNode(null));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                        .andExpect(status().isOk());
        //update item
        publicItem = itemService.find(context, publicItem.getID());
        //control, if the handle has changed
        assertEquals(handleClarinService.findByHandle(context, "123"), publicItem.getHandles().get(0));
        //archive was false, archived handle does not exist
        assertNull(handleClarinService.findByHandle(context,oldHandleStr));
        this.cleanHandles();
    }

    @Test
    public void patchUpdateInternalHandleWithArchive() throws  Exception {
        //handle: 123
        //archive: true
        Handle handle = publicItem.getHandles().get(0);
        String oldHandleStr = handle.getHandle();

        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode("123"));
        values.put("url", jsonNodeFactory.textNode(null));
        values.put("archive", jsonNodeFactory.textNode("true"));
        ops.add(new ReplaceOperation("/updateHandle", values));



        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                .content(patchBody)
                .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        // Update item
        publicItem = itemService.find(context, publicItem.getID());
        // Control, if the handle has changed
        assertEquals(handleClarinService.findByHandle(context, "123"), publicItem.getHandles().get(0));
        // Archive was true, archived handle exists
        assertEquals(handleClarinService.findByHandle(context,oldHandleStr).getHandle(), oldHandleStr);
        // Archive was true, archived handle is external handle with correct url
        assertEquals(handleClarinService.findByHandle(context,oldHandleStr).getUrl(), "http://localhost:4000/handle/" + publicItem.getHandle());

        this.cleanHandles();
    }


    @Test
    public void patchUpdateExternalHandleWithoutHandle() throws  Exception {
        //handle: ""
        //url: www.test.com
        //archive: false
        Handle handle = this.createExternalHandle("987");
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode(""));
        values.put("url", jsonNodeFactory.textNode("www.test.com"));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        //exception UnprocessableEntityException
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect((status().is(422)));

        this.cleanHandles();
    }

    @Test
    public void patchUpdateExternalHandleWithoutUrl() throws  Exception {
        //handle: 123
        //url: ""
        //archive: false
        Handle handle = this.createExternalHandle("987");
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode("123"));
        values.put("url", jsonNodeFactory.textNode(""));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        //exception UnprocessableEntityException
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                        .andExpect((status().is(422)));

        this.cleanHandles();
    }

    @Test
    public void patchUpdateExternalHandleWithoutArchive() throws  Exception {
        //handle: 123
        //url: www.test.com
        //archive: false

        Handle handle = this.createExternalHandle("987");
        String oldHandleStr = handle.getHandle();

        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode("123"));
        values.put("url", jsonNodeFactory.textNode("www.test.com"));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        //control, if the handle has changed
        assertNotNull(handleClarinService.findByHandle(context, "123"));
        assertEquals(handleClarinService.findByHandle(context, "123").getUrl(), "www.test.com");
        //archive was false, archived handle does not exist
        assertNull(handleClarinService.findByHandle(context,oldHandleStr));

        this.cleanHandles();
    }

    @Test
    public void patchUpdateExternalHandleWithArchive() throws  Exception {
        //handle: 123
        //url: www.test.com
        //archive: true
        Handle handle = this.createExternalHandle("987");
        String oldHandleStr = handle.getHandle();

        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("handle", jsonNodeFactory.textNode("123"));
        values.put("url", jsonNodeFactory.textNode("www.test.com"));
        values.put("archive", jsonNodeFactory.textNode("true"));
        ops.add(new ReplaceOperation("/updateHandle", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(get(HANDLES_ENDPOINT + handle.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(handle)
                )));
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        //control, if the handle has changed
        assertNotNull(handleClarinService.findByHandle(context, "123"));
        assertEquals(handleClarinService.findByHandle(context, "123").getUrl(), "www.test.com");
        //archive was true, archived handle exists
        assertNotNull(handleClarinService.findByHandle(context,oldHandleStr));
        //archive was true, archived handle is external handle with correct url
        assertEquals(handleClarinService.findByHandle(context,oldHandleStr).getUrl(), "www.test.com");

        this.cleanHandles();
    }


    @Test
    public void patchSetPrefixWithoutArchive() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        Handle externalHandle = this.createExternalHandle("987");
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("oldPrefix", jsonNodeFactory.textNode("123456789"));
        values.put("newPrefix", jsonNodeFactory.textNode("987654321"));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/setPrefix", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        //set prefix
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        //update item
        publicItem = itemService.find(context, publicItem.getID());
        //set prefix in existing handles
        assertEquals(publicItem.getHandle().split("/")[0], "987654321");
        getClient(adminToken).perform(get(HANDLES_ENDPOINT + publicItem.getHandles().get(0).getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(publicItem.getHandles().get(0))
                )));
        //update column
        col = collectionService.find(context, col.getID());
        assertEquals(col.getHandle().split("/")[0], "987654321");
        //find handle of column
        getClient(adminToken).perform(get(HANDLES_ENDPOINT + col.getHandles().get(0).getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(col.getHandles().get(0)))
                ));

        //update external handle
        //handle: "987"
        externalHandle = handleClarinService.findByID(context, externalHandle.getID());
        assertNotEquals(externalHandle.getHandle().split("/")[0],"987654321");

        //changed prefix
        assertEquals(handleService.getPrefix(), "987654321");

        //create new item
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        Collection col2 =  CollectionBuilder.createCollection(context, community).build();
        Item newItem = ItemBuilder.createItem(context, col2)
                .withAuthor(AUTHOR)
                .build();
        context.restoreAuthSystemState();

        //handle of new item has changed prefix
        assertEquals(newItem.getHandle().split("/")[0],handleService.getPrefix());

        //archive was false
        assertNull(handleClarinService.findByHandle(context, "123456789/" + publicItem.getHandle().split("/")[1]));
        assertNull(handleClarinService.findByHandle(context, "123456789/" + col.getHandle().split("/")[1]));

        this.cleanHandles();
    }

    @Test
    public void patchSetPrefixBySamePrefix() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("oldPrefix", jsonNodeFactory.textNode("123456789"));
        values.put("newPrefix", jsonNodeFactory.textNode("123456789"));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/setPrefix", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        //set prefix
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().is(500));
        this.cleanHandles();
    }

    @Test
    public void patchSetPrefixWithoutPrefix() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("oldPrefix", jsonNodeFactory.textNode("123456789"));
        values.put("newPrefix", jsonNodeFactory.textNode(""));
        values.put("archive", jsonNodeFactory.textNode("false"));
        ops.add(new ReplaceOperation("/setPrefix", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        //update handle
        //exception UnprocessableEntityException
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().is(422));
        this.cleanHandles();
    }


    @Test
    public void patchSetPrefixWithArchive() throws  Exception {
        Handle handle = publicItem.getHandles().get(0);
        Handle externalHandle1 = this.createExternalHandle("987");
        Handle externalHandle2 = this.createExternalHandle("123456789/100");
        List<Operation> ops = new ArrayList<Operation>();
        LinkedHashMap<String, TextNode> values = new LinkedHashMap<>();
        values.put("oldPrefix", jsonNodeFactory.textNode("123456789"));
        values.put("newPrefix", jsonNodeFactory.textNode("987654321"));
        values.put("archive", jsonNodeFactory.textNode("true"));
        ops.add(new ReplaceOperation("/setPrefix", values));

        String patchBody = getPatchContent(ops);
        String adminToken = getAuthToken(admin.getEmail(), password);

        //set prefix
        getClient(adminToken).perform(patch(HANDLES_ENDPOINT + handle.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        //update item
        publicItem = itemService.find(context, publicItem.getID());
        //set prefix in existing handles
        assertEquals(publicItem.getHandle().split("/")[0], "987654321");
        getClient(adminToken).perform(get(HANDLES_ENDPOINT + publicItem.getHandles().get(0).getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(publicItem.getHandles().get(0))
                )));
        //update column
        col = collectionService.find(context, col.getID());
        assertEquals(col.getHandle().split("/")[0], "987654321");
        //find handle of column
        getClient(adminToken).perform(get(HANDLES_ENDPOINT + col.getHandles().get(0).getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.is(
                        HandleMatcher.matchHandle(col.getHandles().get(0)))
                ));
        //update external handle
        externalHandle1 = handleClarinService.findByID(context, externalHandle1.getID());
        assertNotEquals(externalHandle1.getHandle().split("/")[0],"987654321");
        externalHandle2 = handleClarinService.findByID(context, externalHandle2.getID());
        assertEquals(externalHandle2.getHandle().split("/")[0],"987654321");


        //changed prefix
        assertEquals(handleService.getPrefix(), "987654321");

        //create new item
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        Collection col2 =  CollectionBuilder.createCollection(context, community).build();
        Item newItem = ItemBuilder.createItem(context, col2)
                .withAuthor(AUTHOR)
                .build();
        context.restoreAuthSystemState();

        //handle of new item has changed prefix
        assertEquals(newItem.getHandle().split("/")[0],handleService.getPrefix());

        //archive was true
        Handle archivedItem = handleClarinService.findByHandle(context, "123456789/" +
                publicItem.getHandle().split("/")[1]);
        Handle archivedCol = handleClarinService.findByHandle(context, "123456789/" + col.getHandle().split("/")[1]);
        Handle archivedExternalHandle2 = handleClarinService.findByHandle(context, "123456789/" +
                externalHandle2.getHandle().split("/")[1]);
        assertNotNull(archivedItem);
        assertNotNull(archivedCol);
        assertNotNull(archivedExternalHandle2);

        assertEquals(archivedItem.getUrl(), "http://localhost:4000/handle/" + publicItem.getHandle());
        assertEquals(archivedCol.getUrl(), "http://localhost:4000/handle/" + col.getHandle());
        assertNotEquals(archivedExternalHandle2.getUrl(), "http://localhost:4000/handle/" + externalHandle2.getHandle());
        assertEquals(archivedExternalHandle2.getUrl(), externalHandle2.getUrl());

        this.cleanHandles();
    }

    // clean handles of the created Items, Communities, Collections because when is created
    // a new Item/Collection/Community in another test, the handle of the old Item/Collection/Community
    // lost DSpaceObject (dso is null) and it throws error in the HandleConverter
    private void
    cleanHandles() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        List<Handle> handles = handleClarinService.findAll(context);
        for (Handle handle: handles) {
            handleClarinService.delete(context, handle);
        }
//        handleClarinService.delete(context, parentCommunity.getHandles().get(0));
//        handleClarinService.delete(context, col.getHandles().get(0));
//        handleClarinService.delete(context, publicItem.getHandles().get(0));

        context.restoreAuthSystemState();
    }

    private Handle createExternalHandle(String strHandle) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HandleRest handleRest = new HandleRest();
        handleRest.setHandle(strHandle);
        handleRest.setUrl("www.externalHandle.com");
        Integer handleId = null;
        String adminToken = getAuthToken(admin.getEmail(), password);
        MvcResult mvcResult = getClient(adminToken).perform(post(HANDLES_ENDPOINT)
                        .content(mapper.writeValueAsBytes(handleRest))
                        .contentType(contentType))
                .andExpect(status().isCreated())
                .andReturn();

        String content = mvcResult.getResponse().getContentAsString();
        Map<String, Object> map = mapper.readValue(content, Map.class);
        handleId = Integer.valueOf(String.valueOf(map.get("id")));
        //find created handle
        Handle handle = handleClarinService.findByID(context, handleId);
        return handle;
    }

}
