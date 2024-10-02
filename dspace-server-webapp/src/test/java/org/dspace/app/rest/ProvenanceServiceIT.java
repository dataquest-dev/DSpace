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
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.rest.model.patch.AddOperation;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.RemoveOperation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.builder.ClarinLicenseResourceMappingBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.Charset.defaultCharset;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.springframework.data.rest.webmvc.RestMediaTypes.TEXT_URI_LIST_VALUE;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProvenanceServiceIT extends AbstractControllerIntegrationTest {
    static String PROVENANCE = "dc.description.provenance";
    private Collection  collection;
    private ObjectMapper objectMapper = new ObjectMapper();
    private ProvenanceMetadataCheck provenanceCheck = new ProvenanceMetadataCheck();


    @Autowired
    private ItemService itemService;
    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private ClarinLicenseResourceMappingService resourceMappingService;
    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;
    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    private Path tempDir;
    private String tempFilePath;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        //suite = objectMapper.readTree(getClass().getResourceAsStream("provenance-patch-suite.json"));
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity).build();

        tempDir = Files.createTempDirectory("bulkAccessTest");
        tempFilePath = tempDir + "/bulk-access.json";
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
                .build();
        context.restoreAuthSystemState();
        return item;
    }

    private Bundle createBundle(Item item, String bundleName) throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        Bundle bundle = BundleBuilder.createBundle(context, item).withName(bundleName).build();
        context.restoreAuthSystemState();
        return bundle;
    }

    private ClarinLicense createLicense(String name) throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        ClarinLicense clarinLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        clarinLicense.setName(name);
        context.restoreAuthSystemState();
        return clarinLicense;
    }

    private Bitstream createBitstream(Item item, String bundleName) throws SQLException, AuthorizeException, IOException {
        context.turnOffAuthorisationSystem();
        Bundle bundle = createBundle(item, Objects.isNull(bundleName) ? "test" : bundleName);
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, bundle,
                toInputStream("Test Content", defaultCharset())).build();
        context.restoreAuthSystemState();
        return bitstream;
    }

//    private void responseCheck(JsonNode response, String respKey) {
//        JsonNode expectedSubStr = suite.get(respKey);
//        JsonNode responseMetadata = response.get("metadata").get("dc.description.provenance");
//        for (JsonNode expNode : expectedSubStr) {
//            boolean contains = false;
//            for (JsonNode node : responseMetadata) {
//                if (node.get("value").asText().contains(expNode.asText())) {
//                    contains = true;
//                    break;
//                }
//            }
//            if (!contains) {
//                Assert.fail("Metadata provenance do not contain expected data: " + expNode.asText());
//            }
//        }
//    }



    @Test
    public void makeDiscoverableTest() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        Item item = createItem();
        List<Operation> ops = new ArrayList<>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/discoverable", true);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        // make discoverable
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.discoverable", Matchers.is(true)))
                .andReturn();
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "discoverable");
    }

    @Test
    public void makeNonDiscoverableTest() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        Item item = createItem();
        //item.setDiscoverable(true);
        List<Operation> ops = new ArrayList<>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/discoverable", false);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        // make non-discoverable
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "nonDiscoverable");
    }


    @Test
    public void addedToMappedCollTest() throws Exception {
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
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "mapped");
    }

    @Test
    public void deletedFromMappedCollTest() throws Exception {
    }

    @Test
    public void addMetadata() throws Exception {
        Item item = createItem();

        String adminToken = getAuthToken(admin.getEmail(), password);

        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        AddOperation addOperation = new AddOperation("/metadata/dc.title", "Test");
        ops.add(addOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "addMetadata");
    }

    @Test
    public void replaceMetadata() throws Exception {
        Item item = createItem();

        String adminToken = getAuthToken(admin.getEmail(), password);
        int index = 0;
        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/metadata/dc.title/" + index, "Test");
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "replaceMetadata");
    }

    @Test
    public void removeMetadata() throws Exception {
        Item item = createItem();
        String adminToken = getAuthToken(admin.getEmail(), password);
        int index = 0;
        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        RemoveOperation removeOperation = new RemoveOperation("/metadata/dc.title/" + index);
        ops.add(removeOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "removeMetadata");
    }

    @Test
    public void removeMetadataBitstream() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, "test");
        String adminToken = getAuthToken(admin.getEmail(), password);
        int index = 0;
        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        AddOperation addOperation = new AddOperation("/metadata/dc.description", "test");
        ops.add(addOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/bitstreams/" + bitstream.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "removeBitstreamMtd");
    }

    @Test
    public void addMetadataBitstream() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, "test");
        String adminToken = getAuthToken(admin.getEmail(), password);
        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        AddOperation addOperation = new AddOperation("/metadata/dc.description", "test");
        ops.add(addOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/bitstreams/" + bitstream.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "removeBitstreamMtd");
    }

    @Test
    public void updateMetadataBitstream() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, "test");
        String adminToken = getAuthToken(admin.getEmail(), password);
        int index = 0;
        //Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/metadata/dc.title", "test 1");
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/bitstreams/" + bitstream.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "replaceBitstreamMtd");
    }

    @Test
    public void removeBitstreamFromItem() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, "test");
        String adminToken = getAuthToken(admin.getEmail(), password);
        // Modify the entityType and verify the response already contains this modification
        List<Operation> ops = new ArrayList<>();
        RemoveOperation removeOperation = new RemoveOperation("/bitstreams/" + bitstream.getID());
        ops.add(removeOperation);
        String patchBody = getPatchContent(ops);
        getClient(adminToken).perform(patch("/api/core/bitstreams")
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON));
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "removeBitstream");
    }

    @Test
    public void addBitstreamToItem() throws Exception {
        Item item = createItem();
        Bundle bundle = createBundle(item, "test");

        String token = getAuthToken(admin.getEmail(), password);
        String input = "Hello, World!";
        context.turnOffAuthorisationSystem();
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", org.springframework.http.MediaType.TEXT_PLAIN_VALUE,
                input.getBytes());
        context.restoreAuthSystemState();
        getClient(token)
                .perform(MockMvcRequestBuilders.multipart("/api/core/bundles/" + bundle.getID() + "/bitstreams")
                        .file(file))
                .andExpect(status().isCreated());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "addBitstream");
    }

    /**
     * Create Clarin License Label object for testing purposes.
     */
    private ClarinLicenseLabel createClarinLicenseLabel(String label, boolean extended, String title)
            throws SQLException, AuthorizeException {
        ClarinLicenseLabel clarinLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        clarinLicenseLabel.setLabel(label);
        clarinLicenseLabel.setExtended(extended);
        clarinLicenseLabel.setTitle(title);

        clarinLicenseLabelService.update(context, clarinLicenseLabel);
        return clarinLicenseLabel;
    }

    private ClarinLicense createClarinLicense(String name, String definition)
            throws SQLException, AuthorizeException {
        ClarinLicense clarinLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        clarinLicense.setDefinition(definition);
        clarinLicense.setName(name);

        // add ClarinLicenseLabels to the ClarinLicense
        HashSet<ClarinLicenseLabel> clarinLicenseLabels = new HashSet<>();
        ClarinLicenseLabel clarinLicenseLabel = createClarinLicenseLabel("lbl", false, "Test Title");
        clarinLicenseLabels.add(clarinLicenseLabel);
        clarinLicense.setLicenseLabels(clarinLicenseLabels);

        clarinLicenseService.update(context, clarinLicense);
        return clarinLicense;
    }

    private ClarinLicenseResourceMapping createResourceMapping(ClarinLicense license, Bitstream bitstream) throws SQLException, AuthorizeException {
        ClarinLicenseResourceMapping resourceMapping = ClarinLicenseResourceMappingBuilder.createClarinLicenseResourceMapping(context).build();
        resourceMapping.setLicense(license);
        resourceMapping.setBitstream(bitstream);
        return resourceMapping;
    }

    @Test
    public void updateLicenseTest() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, Constants.LICENSE_BUNDLE_NAME);
        ClarinLicense clarinLicense1 = createClarinLicense("Test 1", "Test Def");
        ClarinLicenseResourceMapping resourceMapping = createResourceMapping(clarinLicense1, bitstream);
        ClarinLicense clarinLicense2 = createClarinLicense("Test 2", "Test Def");

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(put("/api/core/items/" + item.getID() + "/bundles")
                        .param("licenseID", clarinLicense2.getID().toString()))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "updateLicense");
    }

    @Test
    public void addLicenseTest() throws Exception {
        Item item = createItem();
        ClarinLicense clarinLicense = createClarinLicense("Test", "Test Def");
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(put("/api/core/items/" + item.getID() + "/bundles")
                        .param("licenseID", clarinLicense.getID().toString()))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "addLicense");
    }

    @Test
    public void removeLicenseTest() throws Exception {
        Item item = createItem();
        Bitstream bitstream = createBitstream(item, Constants.LICENSE_BUNDLE_NAME);
        ClarinLicense clarinLicense1 = createClarinLicense("Test", "Test Def");
        ClarinLicenseResourceMapping resourceMapping = createResourceMapping(clarinLicense1, bitstream);

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(put("/api/core/items/" + item.getID() + "/bundles"))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "removeLicense");
    }

    private void buildJsonFile(String json) throws IOException {
        File file = new File(tempDir + "/bulk-access.json");
        Path path = Paths.get(file.getAbsolutePath());
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    @Test
    public void itemResourcePoliciesTest() throws Exception {
//        context.turnOffAuthorisationSystem();
////        Item item = ItemBuilder.createItem(context, collection).withAdminUser(eperson).build();
////        context.restoreAuthSystemState();
////        String json = "{ \"item\": {\n" +
////                "      \"mode\": \"add\",\n" +
////                "      \"accessConditions\": [\n" +
////                "          {\n" +
////                "            \"name\": \"openaccess\"\n" +
////                "          }\n" +
////                "      ]\n" +
////                "   }}\n";
////
////        buildJsonFile(json);
////
////        String[] args = new String[] {"bulk-access-control", "-u", item.getID().toString(), "-f", tempFilePath,
////                "-e", eperson.getEmail()};
////        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
////        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
////
////        item = itemService.find(context, item.getID());
////        //objectCheck(item, "removeResPoliciesItem");
////        objectCheck(item, "addAccessCondItem");
//        context.turnOffAuthorisationSystem();
//
//        Community community = CommunityBuilder.createCommunity(context)
//                .withName("community")
//                .build();
//
//        Collection collection = CollectionBuilder.createCollection(context, community)
//                .withName("collection")
//                .build();
//
//        Item item = ItemBuilder.createItem(context, collection).build();
//
//        context.restoreAuthSystemState();
//        Item item = createItem();
//
//        String json = "{ \"item\": {\n" +
//                "      \"mode\": \"add\",\n" +
//                "      \"accessConditions\": [\n" +
//                "          {\n" +
//                "            \"name\": \"openaccess\"\n" +
//                "          }\n" +
//                "      ]\n" +
//                "   }}\n";
//
//        buildJsonFile(json);
//
//        String[] args = new String[] {"bulk-access-control", "-u", item.getID().toString(), "-f", tempFilePath, "-e", eperson.getEmail()};
//
//        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
//        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
//        objectCheck(itemService.find(context, item.getID()), "addAccessCondBitstream");
//        context.turnOffAuthorisationSystem();
//
//        Community community = CommunityBuilder.createCommunity(context)
//                .withName("community")
//                .build();
//
//        Collection collection = CollectionBuilder.createCollection(context, community)
//                .withName("collection")
//                .build();
//
//        Item item = ItemBuilder.createItem(context, collection).withAdminUser(eperson).build();
//
//        context.restoreAuthSystemState();
//
//        String json = "{ \"item\": {\n" +
//                "      \"mode\": \"add\",\n" +
//                "      \"accessConditions\": [\n" +
//                "          {\n" +
//                "            \"name\": \"openaccess\"\n" +
//                "          }\n" +
//                "      ]\n" +
//                "   }}\n";
//
//        buildJsonFile(json);
//
//        String[] args = new String[] {"bulk-access-control", "-u", item.getID().toString(), "-f", tempFilePath,
//                "-e", eperson.getEmail()};
//
//        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
//        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
//        objectCheck(itemService.find(context, item.getID()), "addAccessCondBitstream");
        context.turnOffAuthorisationSystem();

        Community community = CommunityBuilder.createCommunity(context)
                .withName("community")
                .build();

        Collection collection = CollectionBuilder.createCollection(context, community)
                .withName("collection")
                .build();

        Item item = ItemBuilder.createItem(context, collection).withAdminUser(eperson).build();

        context.restoreAuthSystemState();

        String json = "{ \"item\": {\n" +
                "      \"mode\": \"add\",\n" +
                "      \"accessConditions\": [\n" +
                "          {\n" +
                "            \"name\": \"openaccess\"\n" +
                "          }\n" +
                "      ]\n" +
                "   }}\n";

        buildJsonFile(json);

        String[] args = new String[] {"bulk-access-control", "-u", item.getID().toString(), "-f", tempFilePath,
                "-e", eperson.getEmail()};

        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
        provenanceCheck.objectCheck(itemService.find(context, item.getID()), "addAccessCondBitstream");
    }


    @Test
    public void addRemoveBitstreamResourcePoliciesTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection).withAdminUser(eperson).build();
        context.restoreAuthSystemState();
        Bundle bundle = createBundle(item, Constants.DEFAULT_BUNDLE_NAME);
        context.turnOffAuthorisationSystem();
        String bitstreamOneContent = "Dummy content one";
        Bitstream bitstreamOne;
        try (InputStream is = IOUtils.toInputStream(bitstreamOneContent, CharEncoding.UTF_8)) {
            bitstreamOne  = BitstreamBuilder.createBitstream(context, bundle, is)
                    .withName("bistream")
                    .build();
        }


        context.restoreAuthSystemState();

        String jsonOne = "{ \"bitstream\": {\n" +
                "      \"constraints\": {\n" +
                "          \"uuid\": [\"" + bitstreamOne.getID().toString() + "\"]\n" +
                "      },\n" +
                "      \"mode\": \"replace\",\n" +
                "      \"accessConditions\": [\n" +
                "          {\n" +
                "            \"name\": \"embargo\",\n" +
                "            \"startDate\": \"2024-06-24\"\n" +
                "          }\n" +
                "      ]\n" +
                "   }\n" +
                "}\n";

        buildJsonFile(jsonOne);

        String[] args =
                new String[] {"bulk-access-control",
                        "-u", item.getID().toString(),
                        "-f", tempFilePath,
                        "-e", admin.getEmail()};

        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
        //objectCheck(item, "removeResPoliciesBitstream");
        provenanceCheck.objectCheck(item, "addAccessCondBitstream");
    }

    private Collection createCollection() {
        context.turnOffAuthorisationSystem();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection 1").build();
        context.restoreAuthSystemState();
        return col;
    }

    @Test
    public void moveItemToColTest() throws Exception {
        Item item = createItem();
        Collection col = createCollection();

        String token = getAuthToken(admin.getEmail(), password);

        getClient(token)
                .perform(put("/api/core/items/" + item.getID() + "/owningCollection/")
                        .contentType(parseMediaType(TEXT_URI_LIST_VALUE))
                        .content(
                                "https://localhost:8080/spring-rest/api/core/collections/" + col.getID()
                        ))
                .andExpect(status().isOk());
        provenanceCheck.objectCheck(item, "movedToCol");
    }

    @After
    @Override
    public void destroy() throws Exception {
        PathUtils.deleteDirectory(tempDir);
        super.destroy();
    }

    @Test
    public void moveItemFromColTest() throws Exception {

    }
}
