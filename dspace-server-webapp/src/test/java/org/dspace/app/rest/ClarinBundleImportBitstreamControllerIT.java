package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.utils.Obj;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.matcher.CommunityMatcher;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.test.AbstractEntityIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.app.util.factory.UtilServiceFactory;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinBundleImportBitstreamControllerIT extends AbstractEntityIntegrationTest {
    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private BitstreamService bitstreamService;
    private JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);
    @Autowired
    private ConverterService converter;
    @Autowired
    private Utils utils;

    @Autowired
    private BitstreamFormatService bitstreamFormatService;
    @Test
    public void importBitstreamForExistingFileTest() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                .withName("Sub Community")
                .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1).withName("Collection 1").build();
        Item item = ItemBuilder.createItem(context, col1)
                .withTitle("Author1")
                .withIssueDate("2017-10-17")
                .withAuthor("Smith, Donald")
                .build();
        Bundle bundle = BundleBuilder.createBundle(context, item)
                .withName("TESTINGBUNDLE")
                .build();
        String token = getAuthToken(admin.getEmail(), password);
        String input = "Hello, World!";
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE,
                input.getBytes());
        //create bitstreamformat
        BitstreamFormat bitstreamFormat = bitstreamFormatService.create(context);
        context.restoreAuthSystemState();
        //create bitstream and store file
        MvcResult mvcResult = getClient(token)
                .perform(MockMvcRequestBuilders.fileUpload("/api/core/bundles/" + bundle.getID() + "/bitstreams")
                        .file(file))
                .andExpect(status().isCreated())
                .andReturn();

        //get bitstream id
        ObjectMapper mapper = new ObjectMapper();

        String content = mvcResult.getResponse().getContentAsString();
        Map<String, Object> map = mapper.readValue(content, Map.class);
        String bitstreamId = String.valueOf(map.get("id"));

        //delete bitstream, but no file
        //save necessary values
        Bitstream bitstream = bitstreamService.find(context, UUID.fromString(bitstreamId));
        //create bitstream for existing file
        ObjectNode checksumNode = jsonNodeFactory.objectNode();
        checksumNode.set("checkSumAlgorithm", jsonNodeFactory.textNode("MD5"));
        checksumNode.set("value", jsonNodeFactory.textNode(bitstream.getChecksum()));
        ObjectNode node = jsonNodeFactory.objectNode();
        node.set("name", jsonNodeFactory.textNode("New bitstream"));
        node.set("sizeBytes", jsonNodeFactory.textNode(Long.toString(bitstream.getSizeBytes())));
        node.set("checkSum", checksumNode);
        String internalId = bitstream.getInternalId();
        String storeNumber = Integer.toString(bitstream.getStoreNumber());

        context.turnOffAuthorisationSystem();
        bitstreamService.delete(context, bitstream);
        bitstreamService.expunge(context, bitstream);
        context.restoreAuthSystemState();

        //control
        assertNull(bitstreamService.find(context, UUID.fromString(bitstreamId)));
        //is still file exist?


        getClient(token).perform(post("/api/clarin/import/core/bundles/" + bundle.getID() + "/bitstreams")
                        .content(mapper.writeValueAsBytes(node))
                        .contentType(contentType)
                        .param("internal_id", internalId)
                        .param("storeNumber", storeNumber)
                        .param("bitstreamFormat", Integer.toString(bitstreamFormat.getID()))
                        .param("deleted", "false")
                        .param("sequenceId", "5"))
                .andExpect(status().isOk());
        //add more checks

    }
}
