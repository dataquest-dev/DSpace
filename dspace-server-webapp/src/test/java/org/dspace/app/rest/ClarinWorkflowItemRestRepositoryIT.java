package org.dspace.app.rest;

import org.checkerframework.checker.units.qual.A;
import org.dspace.app.rest.matcher.VersionMatcher;
import org.dspace.app.rest.matcher.WorkflowItemMatcher;
import org.dspace.app.rest.model.patch.AddOperation;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.VersionBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.eperson.EPerson;
import org.dspace.license.service.CreativeCommonsService;
import org.dspace.services.ConfigurationService;
import org.dspace.xmlworkflow.factory.XmlWorkflowFactory;
import org.dspace.xmlworkflow.storedcomponents.service.CollectionRoleService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.RestMediaTypes;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.jsonpath.JsonPath.read;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * For testing the ClarinVersionedHandleIdentifierProvider and ClarinDOIIdentifierProvider
 */
public class ClarinWorkflowItemRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private XmlWorkflowFactory xmlWorkflowFactory;

    @Autowired
    private CollectionRoleService collectionRoleService;

    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private CreativeCommonsService creativeCommonsService;

    @Before
    @Override
    public void setUp() throws Exception {

        super.setUp();

        //disable file upload mandatory
        configurationService.setProperty("webui.submit.upload.required", false);
    }

    @Test
    public void depositWorkspaceItemWithoutWorkflowTest() throws Exception {
        context.turnOffAuthorisationSystem();

        // hold the id of the item version
        AtomicReference<Integer> idItemVersionRef = new AtomicReference<Integer>();
        try {
            //** GIVEN **
            //1. A community with one collection.
            parentCommunity = CommunityBuilder.createCommunity(context)
                    .withName("Parent Community")
                    .build();
            Collection col1 = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1")
                    .build();

            //2. create a normal user to use as submitter
            EPerson submitter = EPersonBuilder.createEPerson(context)
                    .withEmail("submitter@example.com")
                    .withPassword("dspace")
                    .build();

            context.setCurrentUser(submitter);

            // BSN_LICENSE_RDF
            //3. a workspace item
//            WorkspaceItem wsitem = WorkspaceItemBuilder.createWorkspaceItem(context, col1)
//                    .withTitle("Submission Item")
//                    .withIssueDate("2017-10-17")
//                    .withAuthor("Doe, John")
//                    .withSubject("ExtraEntry")
//                    .withArchivedItem(true)
//                    .build();

            Item item = ItemBuilder.createItem(context, col1)
                    .withTitle("Submission Item")
                    .withIssueDate("2017-10-17")
                    .withAuthor("Doe, John")
                    .withSubject("ExtraEntry")
                    .withCCLicense("http://creativecommons.org/licenses/by-nc-sa/4.0/")
                    .build();
            context.restoreAuthSystemState();

            String adminToken = getAuthToken(admin.getEmail(), password);

            // get the submitter auth token
            String authToken = getAuthToken(submitter.getEmail(), "dspace");

            // Create a history object of the item
            AtomicReference<Integer> idVersionRef = new AtomicReference<Integer>();
            try {
                getClient(adminToken).perform(get("/api/submission/workspaceitems"))
                        .andExpect(status().isOk());

                // Create the item version history record - this object is created after clicking on the `Create new version`
                // button, but in testing it must be called manually before creating the item version.
                getClient(adminToken).perform(post("/api/versioning/versions")
                                .contentType(MediaType.parseMediaType(RestMediaTypes.TEXT_URI_LIST_VALUE))
                                .content("/api/core/items/" + item.getID()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$", Matchers.allOf(
                                hasJsonPath("$.version", is(2)),
                                hasJsonPath("$.type", is("version"))
                        )))
                        .andDo(result -> idVersionRef.set(read(result.getResponse().getContentAsString(), "$.id")));


                // Creating of the item version history creates the new workspaceitem - it should have the id = 2
                getClient(adminToken).perform(get("/api/submission/workspaceitems/2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id", is(2)));
                String wsiID = "2";

                // VYTVORY MI NOVY WORKSPACE ITEM AJ HISTORIU
                // TERAZ POTREBUJEM ZISTIT AKE ID MA NOVY ITEM
                // PO VYTVORENI NOVEJ VERZIE, BY SA MAL VYMAZAT WSI?


                // Create a new version of the item
                getClient(adminToken)
                        .perform(post(BASE_REST_SERVER_URL + "/api/workflow/workflowitems")
                                .content("/api/submission/workspaceitems/" + wsiID)
                                .contentType(textUriContentType))
                        .andExpect(status().isCreated());

                // Get a new version of the item

                // Load version object from the Item
                getClient(adminToken).perform(get("/api/core/items/" + item.getID() + "/version"))
                        .andExpect(status().isOk())
                        .andDo(result -> idItemVersionRef.set(read(result.getResponse().getContentAsString(),
                                "$.id")));

                // Load the version history id
                getClient(adminToken).perform(get("/api/versioning/versions/" + idItemVersionRef +
                                "/item"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id", not(item.getID())));
            } finally {
//                VersionBuilder.delete(idVersionRef.get());
            }



//            getClient(adminToken).perform(get("/api/core/items"))
//                    .andExpect(status().isOk());
        } finally {
            // remove the workflowitem if any
//            WorkflowItemBuilder.deleteWorkflowItem(idRef.get());
        }
    }
}
