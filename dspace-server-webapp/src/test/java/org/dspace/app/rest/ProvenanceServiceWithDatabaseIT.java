package org.dspace.app.rest;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProvenanceServiceWithDatabaseIT extends AbstractIntegrationTestWithDatabase {

    private Path tempDir;
    private String tempFilePath;
    private ProvenanceMetadataCheck provenanceCheck = new ProvenanceMetadataCheck();
    @Autowired
    private ItemService itemService;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        tempDir = Files.createTempDirectory("bulkAccessTest");
        tempFilePath = tempDir + "/bulk-access.json";
    }

    @After
    @Override
    public void destroy() throws Exception {
        PathUtils.deleteDirectory(tempDir);
        super.destroy();
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
}
