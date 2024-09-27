/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dspace.app.rest.matcher.CollectionMatcher;
import org.dspace.app.rest.matcher.ItemMatcher;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dspace.app.rest.matcher.MetadataMatcher.matchMetadata;
import static org.dspace.app.rest.matcher.MetadataMatcher.matchMetadataDoesNotExist;
import static org.springframework.data.rest.webmvc.RestMediaTypes.TEXT_URI_LIST_VALUE;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProvenanceServiceIT extends AbstractControllerIntegrationTest {
    static String PROVENANCE = "dc.description.provenance";
    private Collection  collection;
    private ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode suite;

    @Autowired
    private ItemService itemService;

    @Before
    public void setup() throws Exception {
        suite = objectMapper.readTree(getClass().getResourceAsStream("provenance-patch-suite.json"));
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity).build();
    }

    private JsonNode preprocessingProvenance(String responseBody) throws JsonProcessingException {
        //String responseBody = resultActions.andReturn().getResponse().getContentAsString();
        JsonNode responseJson =  objectMapper.readTree(responseBody);
        JsonNode responseMetadataJson = responseJson.get("metadata");
        if (responseMetadataJson.get(PROVENANCE) != null) {
            // In the provenance metadata, there is a timestamp indicating when they were added.
            // To ensure accurate comparison, remove that date.
            String rspProvenance = responseMetadataJson.get(PROVENANCE).toString();
            // Regex to match the date pattern
            String datePattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";
            Pattern pattern = Pattern.compile(datePattern);
            Matcher matcher = pattern.matcher(rspProvenance);
            String rspModifiedProvenance = rspProvenance;
            while (matcher.find()) {
                String dateString = matcher.group(0);
                rspModifiedProvenance = rspModifiedProvenance.replaceAll(dateString, "");
            }
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNodePrv = objectMapper.readTree(rspModifiedProvenance);
            // Replace the origin metadata with a value with the timestamp removed
            ((ObjectNode) responseJson.get("metadata")).put(PROVENANCE, jsonNodePrv);
            return responseJson;
        }
        return null;
    }

    private Item createItem() {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection)
                .withTitle("Public item 1")
                .withIssueDate("2017-10-17")
                .withAuthor("Smith, Donald")
                .makeUnDiscoverable()
                .build();
        context.restoreAuthSystemState();
        return item;
    }

    private void responseCheck(JsonNode response, String respKey) {
        JsonNode expectedSubStr = suite.get(respKey);
        JsonNode responseMetadata = response.get("metadata").get("dc.description.provenance");
        for (JsonNode expNode : expectedSubStr) {
            boolean contains = false;
            for (JsonNode node : responseMetadata) {
                if (node.get("value").asText().contains(expNode.asText())) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                Assert.fail("Metadata provenance do not contain expected data: " + expNode.asText());
            }
        }
    }

    private void itemCheck(Item item, String respKey) {
        String expectedSubStr = suite.get(respKey).asText();
        List<MetadataValue> metadata = item.getMetadata();
        boolean contain = false;
        for (MetadataValue value : metadata) {
            if (!Objects.equals(value.getMetadataField().toString(), "dc_description_provenance")) {
                continue;
            }
            if (value.getValue().contains(expectedSubStr)) {
                contain = true;
                break;
            }
        }
        if (!contain) {
            Assert.fail("Metadata provenance do not contain expected data: " + expectedSubStr);
        }
    }

    @Test
    public void makeDsicoverableTest() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        Item item = createItem();
//        List<Operation> ops = new ArrayList<Operation>();
//        ReplaceOperation replaceOperation = new ReplaceOperation("/discoverable", true);
//        ops.add(replaceOperation);
//        String patchBody = getPatchContent(ops);

        // make discoverable
        MvcResult mvcResult = getClient(token).perform(patch("/api/core/items/" + item.getID()
                        + "/discoverable")
//                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.discoverable", Matchers.is(true)))
                .andReturn();
        responseCheck(Objects.requireNonNull(preprocessingProvenance(mvcResult.getResponse().getContentAsString())),
                "discoverable");
    }

    @Test
    public void mappedCollection() throws Exception {
        context.turnOffAuthorisationSystem();
        Collection col1 = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();
        Item item = createItem();
        context.restoreAuthSystemState();

        String adminToken = getAuthToken(admin.getEmail(), password);

        getClient(adminToken).perform(
                post("/api/core/items/" + item.getID() + "/mappedCollections/")
                        .contentType(parseMediaType(TEXT_URI_LIST_VALUE))
                        .content(
                                "https://localhost:8080/spring-rest/api/core/collections/" + col1.getID() + "\n"
                        )
        );

        getClient().perform(get("/api/core/items/" + item.getID() + "/mappedCollections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.mappedCollections", Matchers.containsInAnyOrder(
                        CollectionMatcher.matchCollectionEntry("Collection 1", col1.getID(), col1.getHandle())
                )))
                .andExpect(jsonPath("$._links.self.href", Matchers.containsString("/api/core/items")))
        ;
        itemCheck(itemService.find(context, item.getID()), "mapped");
    }
}
