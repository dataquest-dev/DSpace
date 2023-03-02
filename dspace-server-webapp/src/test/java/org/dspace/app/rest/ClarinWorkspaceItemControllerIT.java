package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
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
import org.json.simple.JSONObject;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinWorkspaceItemControllerIT extends AbstractControllerIntegrationTest {

    private JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);
    @Autowired
    private CommunityService communityService;
    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;
    private Collection col;
    private Item item;
    @Test
    public void createAndReturnTest() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();
        ObjectNode node = jsonNodeFactory.objectNode();
        node.set("discoverable", jsonNodeFactory.textNode("false"));
        node.set("inArchive", jsonNodeFactory.textNode("true"));
        node.set("lastModified", jsonNodeFactory.textNode(null));
        context.restoreAuthSystemState();

        ObjectMapper mapper = new ObjectMapper();
        String token = getAuthToken(admin.getEmail(), password);

        getClient(token).perform(post("/api/clarin/submission/workspaceitem"))
                //.content(mapper.writeValueAsBytes(node))
                //.param("owningCollection", col.getID().toString())
                //.param("multipleTitles", "true")
                //.param("publishedBefore", "false")
                //.param("multipleFiles", "false")
                //.param("stageReached", "1")
                //.param("pageReached", "123"))
                .andExpect(status().isCreated());

        this.cleanAll(context);
    }

    private void cleanAll(Context context) throws SQLException, AuthorizeException, IOException {
        context.turnOffAuthorisationSystem();
        List<WorkspaceItem> workspaceItems = workspaceItemService.findAll(context);
        for (WorkspaceItem wi: workspaceItems) {
            workspaceItemService.deleteAll(context, wi);
        }
        itemService.delete(context, item);
        collectionService.delete(context, col);
        communityService.delete(context, parentCommunity);
        context.restoreAuthSystemState();
    }
}
