/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for embargo functionality in SAF Import feature.
 * Tests the automatic detection and application of embargo policies based on
 * metadata schema fields (dc.rights.access and dc.date.embargoend).
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class EmbargoImportIT extends AbstractIntegrationTestWithDatabase {

    private static final String EMBARGOEND_DATE_FUTURE = "2026-06-30";
    // The resource policy start date should be embargoend + 1 day
    private static final String EXPECTED_POLICY_START_DATE = "2026-07-01";
    private static final String EMBARGOEND_DATE_PAST = "2020-01-01";
    private static final String ITEM_TITLE = "Test Embargo Item";

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();
    private MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();

    private Collection collection;
    private Path tempDir;
    private Path workDir;
    private Group anonymousGroup;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        // Create custom metadata fields needed for embargo functionality
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");

        // Create dc.rights.access field if it doesn't exist
        try {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, "rights", "access", null).build();
        } catch (Exception e) {
            // Field might already exist, that's okay
        }
        // Create dc.date.embargoend field if it doesn't exist
        try {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, "date", "embargoend", null).build();
        } catch (Exception e) {
            // Field might already exist, that's okay
        }

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Test Collection")
                .build();

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("embargoTest");
        File file = new File(configurationService.getProperty("org.dspace.app.batchitemimport.work.dir"));
        if (!file.exists()) {
            Files.createDirectory(Path.of(file.getAbsolutePath()));
        }
        workDir = Path.of(file.getAbsolutePath());
    }

    @After
    @Override
    public void destroy() throws Exception {
        PathUtils.deleteDirectory(tempDir);
        for (Path path : Files.list(workDir).collect(Collectors.toList())) {
            PathUtils.delete(path);
        }
        super.destroy();
    }

    /**
     * Test standard embargo case: dc.rights.access="embargoedAccess" + dc.date.embargoend
     */
    @Test
    public void testStandardEmbargoImport() throws Exception {
        // Create SAF with standard embargo metadata
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        // Create dublin_core.xml with embargo metadata
        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_FUTURE + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add bitstream to test embargo application
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT FOR EMBARGO");

        // Perform import
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // Verify item was created
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);
        assertEquals("Item title should match", ITEM_TITLE, item.getName());

        // Verify bitstream embargo policies
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        assertEquals("Should have one bitstream", 1, bitstreams.size());

        Bitstream bitstream = bitstreams.get(0);
        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);

        // Should have embargo policy for Anonymous group
        ResourcePolicy embargoPolicy = policies.stream()
                .filter(p -> p.getGroup() != null && p.getGroup().equals(anonymousGroup))
                .findFirst()
                .orElse(null);

        assertNotNull("Should have embargo policy for Anonymous group", embargoPolicy);
        assertNotNull("Embargo policy should have start date", embargoPolicy.getStartDate());

        // Verify start date is embargoend + 1 day (file becomes accessible day after embargo ends)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("Embargo policy start date should be embargoend + 1 day",
                EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));
    }

    /**
     * Test that no embargo is applied when embargo date is in the past
     */
    @Test
    public void testPastEmbargoDateNoPolicy() throws Exception {
        // Create SAF with past embargo date
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_PAST + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add bitstream
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT NO EMBARGO");

        // Perform import
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // Verify item was created
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);

        // Verify NO embargo policies for past dates
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        Bitstream bitstream = bitstreams.get(0);
        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);

        // Should not have any embargo policies with start dates for Anonymous group
        boolean hasEmbargoPolicy = policies.stream()
                .anyMatch(p -> p.getGroup() != null &&
                         p.getGroup().equals(anonymousGroup) &&
                         p.getStartDate() != null);

        assertTrue("Should not have embargo policy for past dates", !hasEmbargoPolicy);
    }

    /**
     * Test that no embargo is applied when there's no embargo metadata
     */
    @Test
    public void testNoEmbargoMetadataNoPolicy() throws Exception {
        // Create SAF without embargo metadata
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"contributor\" qualifier=\"author\">Test Author</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add bitstream
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT NO EMBARGO METADATA");

        // Perform import
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // Verify item was created
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);

        // Verify no embargo policies
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        Bitstream bitstream = bitstreams.get(0);
        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);

        boolean hasEmbargoPolicy = policies.stream()
                .anyMatch(p -> p.getGroup() != null &&
                         p.getGroup().equals(anonymousGroup) &&
                         p.getStartDate() != null);

        assertTrue("Should not have embargo policy without embargo metadata", !hasEmbargoPolicy);
    }

    /**
     * Test embargo with invalid date format
     */
    @Test
    public void testInvalidEmbargoDateFormat() throws Exception {
        // Create SAF with invalid embargo date format
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">invalid-date-format</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add bitstream
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT INVALID DATE");

        // Perform import - should not fail but should not apply embargo
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // Verify item was created (import should not fail)
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created even with invalid date format", item);

        // Verify no embargo policies due to invalid date
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        Bitstream bitstream = bitstreams.get(0);
        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);

        boolean hasEmbargoPolicy = policies.stream()
                .anyMatch(p -> p.getGroup() != null &&
                         p.getGroup().equals(anonymousGroup) &&
                         p.getStartDate() != null);

        assertTrue("Should not have embargo policy with invalid date format", !hasEmbargoPolicy);
    }

    /**
     * Test embargo application to multiple bitstreams
     */
    @Test
    public void testMultipleBitstreamsEmbargo() throws Exception {
        // Create SAF with embargo metadata and multiple bitstreams
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_FUTURE + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add multiple bitstreams
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test1.txt\ntest2.pdf");
        Files.writeString(Files.createFile(Path.of(itemDir.toString() + "/test1.txt")), "TEST CONTENT 1");
        Files.writeString(Files.createFile(Path.of(itemDir.toString() + "/test2.pdf")), "TEST CONTENT 2");

        // Perform import
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // Verify item was created
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);

        // Verify embargo policies on all bitstreams
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        assertEquals("Should have two bitstreams", 2, bitstreams.size());

        for (Bitstream bitstream : bitstreams) {
            List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);

            ResourcePolicy embargoPolicy = policies.stream()
                    .filter(p -> p.getGroup() != null && p.getGroup().equals(anonymousGroup))
                    .findFirst()
                    .orElse(null);

            assertNotNull("Each bitstream should have embargo policy", embargoPolicy);
            assertNotNull("Each embargo policy should have start date", embargoPolicy.getStartDate());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            assertEquals("Each embargo start date should be embargoend + 1 day",
                    EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));
        }
    }

    /**
     * Test SAF update: updating metadata and embargo on existing item
     * without deleting the item (as opposed to replace which deletes and re-creates).
     */
    @Test
    public void testSafUpdateMetadataAndEmbargo() throws Exception {
        // First, import an item WITHOUT embargo
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"contributor\" qualifier=\"author\">Original Author</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        // Add bitstream
        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Files.writeString(Files.createFile(Path.of(itemDir.toString() + "/test.txt")), "TEST CONTENT");

        String mapFilePath = tempDir.toString() + "/mapfile.out";
        String[] importArgs = new String[] { "import", "-a", "-e", admin.getEmail(),
                "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", mapFilePath };
        runDSpaceScript(importArgs);

        // Verify initial import
        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);
        String itemHandle = item.getHandle();

        // Verify no embargo policy initially
        List<Bitstream> bitstreams = item.getBundles("ORIGINAL").get(0).getBitstreams();
        Bitstream bitstream = bitstreams.get(0);
        List<ResourcePolicy> initialPolicies = resourcePolicyService.find(context, bitstream, Constants.READ);
        boolean hasInitialEmbargo = initialPolicies.stream()
                .anyMatch(p -> p.getGroup() != null && p.getGroup().equals(anonymousGroup) && p.getStartDate() != null);
        assertFalse("Should not have embargo policy initially", hasInitialEmbargo);

        // Now prepare SAF update directory with updated metadata (add embargo)
        Path updateSafDir = Files.createDirectory(Path.of(tempDir.toString() + "/update"));
        Path updateItemDir = Files.createDirectory(Path.of(updateSafDir.toString() + "/item_000"));

        String updatedDublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + " Updated</dcvalue>\n" +
                "    <dcvalue element=\"contributor\" qualifier=\"author\">Updated Author</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_FUTURE + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(updateItemDir.toString() + "/dublin_core.xml"), updatedDublinCoreContent);

        // Create mapfile for update (item_000 -> handle)
        String updateMapFilePath = tempDir.toString() + "/update_mapfile.out";
        Files.writeString(Path.of(updateMapFilePath), "item_000 " + itemHandle + "\n");

        // Perform SAF update
        String[] updateArgs = new String[] { "import", "-r", "-e", admin.getEmail(),
                "-c", collection.getID().toString(),
                "-s", updateSafDir.toString(), "-m", updateMapFilePath };
        runDSpaceScript(updateArgs);

        // Reload item
        context.uncacheEntity(item);
        Item updatedItem = (Item) handleService.resolveToObject(context, itemHandle);
        assertNotNull("Updated item should exist", updatedItem);

        // Verify metadata was updated
        assertEquals("Title should be updated", ITEM_TITLE + " Updated", updatedItem.getName());

        // Verify embargo policy was applied
        List<Bitstream> updatedBitstreams = updatedItem.getBundles("ORIGINAL").get(0).getBitstreams();
        assertEquals("Should have one bitstream", 1, updatedBitstreams.size());

        Bitstream updatedBitstream = updatedBitstreams.get(0);
        List<ResourcePolicy> updatedPolicies = resourcePolicyService.find(context, updatedBitstream, Constants.READ);

        ResourcePolicy embargoPolicy = updatedPolicies.stream()
                .filter(p -> p.getGroup() != null && p.getGroup().equals(anonymousGroup) && p.getStartDate() != null)
                .findFirst()
                .orElse(null);

        assertNotNull("Should have embargo policy after update", embargoPolicy);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("Embargo policy start date should be embargoend + 1 day",
                EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));
    }
}
