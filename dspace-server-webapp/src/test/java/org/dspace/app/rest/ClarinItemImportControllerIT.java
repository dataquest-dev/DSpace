package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.matcher.WorkflowItemMatcher;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.hamcrest.Matchers;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.jayway.jsonpath.JsonPath.read;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinItemImportControllerIT extends AbstractControllerIntegrationTest {

    private JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);
    @Autowired
    private CommunityService communityService;
    @Autowired
    private CollectionService collectionService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ConfigurationService configurationService;

    private Collection col;
    private Item item;

    @Before
    @Override
    public void setUp() throws Exception {

        super.setUp();

        //disable file upload mandatory
        configurationService.setProperty("webui.submit.upload.required", false);
    }

    @Test
    public void importWorkspaceItemAndItemTest() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();
        //2. create a normal user to use as submitter
        EPerson submitter = EPersonBuilder.createEPerson(context)
                .withEmail("submitter@example.com")
                .withPassword("dspace")
                .build();

        ObjectNode node = jsonNodeFactory.objectNode();
        node.set("discoverable", jsonNodeFactory.textNode("false"));
        node.set("inArchive", jsonNodeFactory.textNode("true"));
        node.set("lastModified", jsonNodeFactory.textNode(null));
        context.restoreAuthSystemState();

        ObjectMapper mapper = new ObjectMapper();
        String token = getAuthToken(admin.getEmail(), password);

        getClient(token).perform(post("/api/clarin/import/workspaceitem")
                .content(mapper.writeValueAsBytes(node))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .param("owningCollection", col.getID().toString())
                .param("multipleTitles", "true")
                .param("publishedBefore", "false")
                .param("multipleFiles", "false")
                .param("stageReached", "1")
                .param("pageReached", "123")
                .param("epersonUUID", submitter.getID().toString()))
                .andExpect(status().isOk());

        List<WorkspaceItem> workspaceItems = workspaceItemService.findAll(context);
        assertEquals(workspaceItems.size(), 1);
        getClient(token).perform(get("/api/clarin/import/" + workspaceItems.get(0).getID() + "/item")).andExpect(status().isOk());
    }

    @Test
    public void importWorkflowItemTest() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1")
                .withWorkflowGroup(1, admin).build();
//        WorkspaceItem workspaceItem = WorkspaceItemBuilder.createWorkspaceItem(context, col)
//                .withTitle("Submission Item")
//                .withIssueDate("2017-10-17")
//                .build();
        EPerson submitter = EPersonBuilder.createEPerson(context)
                .withEmail("submitter@example.com")
                .withPassword("dspace")
                .build();

        ObjectNode node = jsonNodeFactory.objectNode();
        node.set("discoverable", jsonNodeFactory.textNode("false"));
        node.set("inArchive", jsonNodeFactory.textNode("true"));
        node.set("lastModified", jsonNodeFactory.textNode(null));
        context.restoreAuthSystemState();

        ObjectMapper mapper = new ObjectMapper();
        String token = getAuthToken(admin.getEmail(), password);

        getClient(token).perform(post("/api/clarin/import/workspaceitem")
                        .content(mapper.writeValueAsBytes(node))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .param("owningCollection", col.getID().toString())
                        .param("multipleTitles", "true")
                        .param("publishedBefore", "false")
                        .param("multipleFiles", "false")
                        .param("stageReached", "1")
                        .param("pageReached", "123")
                        .param("epersonUUID", submitter.getID().toString()))
                .andExpect(status().isOk());
        List<WorkspaceItem> workspaceItems = workspaceItemService.findAll(context);
        // get the submitter auth token
       // String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/clarin/import/workflowitem")
                .contentType(MediaType.APPLICATION_JSON)
                .param("id", Integer.toString(workspaceItems.get(0).getID())))
                .andExpect(status().isOk());
    }

}
