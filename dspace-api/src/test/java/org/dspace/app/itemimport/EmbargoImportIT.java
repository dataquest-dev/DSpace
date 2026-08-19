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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.dspace.xmlworkflow.state.Workflow;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Integration tests for embargo functionality in SAF Import feature.
 * Tests the automatic detection and application of embargo policies based on
 * metadata schema fields (dc.rights.access and dc.date.embargoend).
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class EmbargoImportIT extends AbstractIntegrationTestWithDatabase {

    // Embargo end date must be in the future at test run time, so compute it relative to "now".
    private static final LocalDate EMBARGO_END_FUTURE = LocalDate.now().plusYears(1);
    private static final String EMBARGOEND_DATE_FUTURE = EMBARGO_END_FUTURE.toString();
    // The resource policy start date should be embargoend + 1 day
    private static final String EXPECTED_POLICY_START_DATE = EMBARGO_END_FUTURE.plusDays(1).toString();
    private static final String EMBARGOEND_DATE_PAST = "2020-01-01";
    private static final String ITEM_TITLE = "Test Embargo Item";
    /**
     * The single rpName both SAF tools write. Repeated here rather than referenced, because the value ends up
     * in the database and has to fit the 30 character {@code resourcepolicy.rpname} column.
     */
    private static final String EMBARGO_POLICY_NAME = "embargo";

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();
    private MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    private XmlWorkflowService xmlWorkflowService =
            XmlWorkflowServiceFactory.getInstance().getXmlWorkflowService();
    private XmlWorkflowItemService xmlWorkflowItemService =
            XmlWorkflowServiceFactory.getInstance().getXmlWorkflowItemService();

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
        deleteRemainingWorkflowItems();
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

        // installItem clones the collection's undated DEFAULT_BITSTREAM_READ onto a bitstream without an
        // Anonymous READ policy, and such a second policy would open the file throughout the embargo.
        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals("exactly one Anonymous READ policy may remain: " + describe(bitstream),
                1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertNotNull("Embargo policy should have start date", embargoPolicy.getStartDate());
        assertEquals("the embargo policy has to be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertEquals("the embargo policy has to carry the access condition name",
                EMBARGO_POLICY_NAME, embargoPolicy.getRpName());

        // Verify start date is embargoend + 1 day (file becomes accessible day after embargo ends)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("Embargo policy start date should be embargoend + 1 day",
                EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));

        assertFalse("an embargoed file must not be downloadable by an anonymous visitor: " + describe(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an embargo which has already expired produces no policy and leaves the file readable,
     * which on the import path means the collection default policies applied by installItem.
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

        // The assertion above is also satisfied by a bitstream with no policy at all, which is unreadable.
        assertTrue("An expired embargo end date must leave the bitstream readable, not policy-less",
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an embargo imported with {@code -w} survives approval. Approval calls {@code installItem},
     * which clones the collection's undated default onto any bitstream without an Anonymous READ policy, so
     * the embargo policy has to exist before the workflow starts.
     */
    @Test
    public void testWorkflowEmbargoSurvivesApproval() throws Exception {
        context.turnOffAuthorisationSystem();
        Collection workflowCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Workflow Collection")
                .withWorkflowGroup(1, admin)
                .build();
        context.restoreAuthSystemState();

        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"rights\" qualifier=\"access\">embargoedAccess</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_FUTURE + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT FOR WORKFLOW EMBARGO");

        // -w: the submission goes through the workflow instead of straight into the archive
        String[] args = new String[] { "import", "-a", "-w", "-e", admin.getEmail(),
                "-c", workflowCollection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        // findByMetadataField only returns archived items and this one is still in the workflow, so the
        // mapfile is what identifies it: "<package directory> <item uuid>".
        Item item = itemFromMapfile(tempDir.toString() + "/mapfile.out");
        assertFalse("fixture precondition: -w must leave the item in the workflow, not in the archive",
                item.isArchived());

        Bitstream bitstream = item.getBundles("ORIGINAL").get(0).getBitstreams().get(0);
        assertFalse("a submission waiting for approval must not be downloadable: " + describe(bitstream),
                anonymousCanRead(bitstream));

        approveWorkflowItem(workflowCollection, item);

        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        assertTrue("fixture precondition: approving the workflow item must archive it", item.isArchived());

        // Without the embargo policy the bitstream would reach installItem with no Anonymous READ policy and
        // get the collection default cloned onto it.
        assertFalse("approving an embargoed submission published its files. dc.date.embargoend is "
                        + EMBARGOEND_DATE_FUTURE + ", so the file has to stay closed: " + describe(bitstream),
                anonymousCanRead(bitstream));

        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals("exactly one Anonymous READ policy may remain after approval - a second, undated one is"
                        + " the collection default and defeats the embargo: " + describe(bitstream),
                1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertNotNull("the surviving Anonymous READ policy has to be dated", embargoPolicy.getStartDate());
        assertEquals("the embargo policy has to carry the access condition name",
                EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals("the embargo policy has to be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("the embargo has to start the day after dc.date.embargoend",
                EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));
    }

    /**
     * Verifies the branch where {@code dc.date.embargoend} arrives without
     * {@code dc.rights.access=embargoedAccess}: it embargoes the files under the same, short enough rpName.
     */
    @Test
    public void testEmbargoEndWithoutAccessRightsStillEmbargoes() throws Exception {
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        // no dc.rights.access at all - this is the branch under test
        String dublinCoreContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<dublin_core schema=\"dc\">\n" +
                "    <dcvalue element=\"title\" qualifier=\"none\">" + ITEM_TITLE + "</dcvalue>\n" +
                "    <dcvalue element=\"date\" qualifier=\"embargoend\">" + EMBARGOEND_DATE_FUTURE + "</dcvalue>\n" +
                "</dublin_core>";
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCoreContent);

        Path contentsFile = Files.createFile(Path.of(itemDir.toString() + "/contents"));
        Files.writeString(contentsFile, "test.txt");
        Path bitstreamFile = Files.createFile(Path.of(itemDir.toString() + "/test.txt"));
        Files.writeString(bitstreamFile, "TEST CONTENT FOR SPECIAL CASE EMBARGO");

        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        runDSpaceScript(args);

        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);
        assertTrue("the item has to be archived, i.e. the import must not have been aborted by an SQL error",
                item.isArchived());

        Bitstream bitstream = item.getBundles("ORIGINAL").get(0).getBitstreams().get(0);

        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals("exactly one Anonymous READ policy may remain: " + describe(bitstream),
                1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertNotNull("the special case branch has to write a dated policy too", embargoPolicy.getStartDate());
        assertEquals("both branches write the same rpName", EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertTrue("rpname is a varchar(30) column, so the value has to fit into it",
                embargoPolicy.getRpName().length() <= 30);
        assertEquals("the embargo policy has to be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("the embargo has to start the day after dc.date.embargoend",
                EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));

        assertFalse("a dc.date.embargoend in the future closes the file even without dc.rights.access: "
                + describe(bitstream), anonymousCanRead(bitstream));
    }

    /**
     * The single item the import reported in its mapfile, looked up by id. Works for workflow imports too,
     * where the item is not in the archive yet and therefore invisible to {@code findByMetadataField}.
     */
    private Item itemFromMapfile(String mapfilePath) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(mapfilePath));
        assertEquals("the import has to report exactly one item in its mapfile, got " + lines,
                1, lines.size());
        String[] columns = lines.get(0).trim().split("\\s+");
        assertEquals("a mapfile line is '<package directory> <item id>', got '" + lines.get(0) + "'",
                2, columns.length);

        Item item = itemService.find(context, UUID.fromString(columns[1]));
        assertNotNull("the item named in the mapfile has to exist", item);
        return item;
    }

    /**
     * A workflow item that outlives its test keeps a {@code cwf_pooltask} row referencing the workflow group,
     * which then blocks {@code AbstractBuilder.cleanupObjects()} from deleting that group.
     */
    private void deleteRemainingWorkflowItems() throws Exception {
        if (context == null || !context.isValid()) {
            return;
        }
        context.turnOffAuthorisationSystem();
        try {
            for (XmlWorkflowItem workflowItem : xmlWorkflowItemService.findAll(context)) {
                xmlWorkflowItemService.delete(context, workflowItem);
            }
            context.commit();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Claims and approves the single review task of a workflow item, which is what finally calls
     * {@code installItem}.
     */
    private void approveWorkflowItem(Collection workflowCollection, Item item) throws Exception {
        XmlWorkflowItem workflowItem = xmlWorkflowItemService.findByItem(context, item);
        assertNotNull("fixture precondition: the imported item has to be a workflow item", workflowItem);

        Workflow workflow = XmlWorkflowServiceFactory.getInstance().getWorkflowFactory()
                .getWorkflow(workflowCollection);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("submit_approve", "submit_approve");
        HttpServletRequest servletRequest = request;

        EPerson previousUser = context.getCurrentUser();
        context.setCurrentUser(admin);
        try {
            Integer workflowItemId = workflowItem.getID();
            xmlWorkflowService.doState(context, admin, servletRequest, workflowItemId, workflow,
                    workflow.getStep("reviewstep").getActionConfig("claimaction"));
            xmlWorkflowService.doState(context, admin, servletRequest, workflowItemId, workflow,
                    workflow.getStep("reviewstep").getActionConfig("reviewaction"));
        } finally {
            context.setCurrentUser(previousUser);
        }
        context.commit();
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws Exception {
        return resourcePolicyService.find(context, bitstream, Constants.READ).stream()
                .filter(policy -> policy.getGroup() != null && policy.getGroup().equals(anonymousGroup))
                .collect(Collectors.toList());
    }

    /**
     * Every resource policy of the bitstream, for failure messages: an extra undated policy and a missing one
     * are otherwise indistinguishable.
     */
    private String describe(Bitstream bitstream) throws Exception {
        StringBuilder sb = new StringBuilder(System.lineSeparator());
        sb.append("      bitstream=").append(bitstream.getID()).append(System.lineSeparator())
                .append("      anonymousCanRead=").append(anonymousCanRead(bitstream))
                .append(System.lineSeparator());
        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);
        if (policies.isEmpty()) {
            sb.append("      <NO READ POLICIES AT ALL>").append(System.lineSeparator());
        }
        for (ResourcePolicy policy : policies) {
            sb.append(String.format("      id=%s group=%s rpType=%s rpName=%s start=%s end=%s",
                            policy.getID(),
                            policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                            policy.getRpType(),
                            policy.getRpName(),
                            policy.getStartDate(),
                            policy.getEndDate()))
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream, with the test's own
     * turnOffAuthorisationSystem calls temporarily unwound.
     */
    private boolean anonymousCanRead(Bitstream bitstream) throws Exception {
        EPerson savedUser = context.getCurrentUser();
        int popped = 0;
        while (context.ignoreAuthorization()) {
            context.restoreAuthSystemState();
            popped++;
        }
        context.setCurrentUser(null);
        try {
            return authorizeService.authorizeActionBoolean(context, bitstream, Constants.READ);
        } finally {
            context.setCurrentUser(savedUser);
            for (int i = 0; i < popped; i++) {
                context.turnOffAuthorisationSystem();
            }
        }
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
     * Verifies that a {@code dc.date.embargoend} no date parser accepts is reported and leaves no readable
     * file behind. The shapes {@code DCDate} did accept are still imported, see the format tests below.
     */
    @Test
    public void testInvalidEmbargoDateFormat() throws Exception {
        assertBrokenEmbargoPackageIsRefused("invalid-date-format", "an unparseable dc.date.embargoend");
    }

    /**
     * Verifies that a date only a lenient parser would accept is refused; reading 30 February as 2 March turns
     * a typo into a real embargo date.
     */
    @Test
    public void testLenientRollOverEmbargoDateIsRefused() throws Exception {
        int year = LocalDate.now(ZoneOffset.UTC).getYear() + 1;
        assertBrokenEmbargoPackageIsRefused(year + "-02-30", "a dc.date.embargoend that does not exist");
    }

    /**
     * Verifies that {@code embargoedAccess} without an end date is refused: archiving it would leave an item
     * whose metadata says closed while the collection default policies make the files public.
     */
    @Test
    public void testEmbargoedAccessWithoutEndDateIsRefused() throws Exception {
        Path itemDir = safPackage("embargoedAccess", null, "TEST CONTENT NO END DATE");

        Exception reported = runImport(itemDir.getParent());
        assertNotNull("dc.rights.access=embargoedAccess without dc.date.embargoend has to be reported to the"
                + " operator instead of being archived as a public item: " + describeArchived(ITEM_TITLE),
                reported);
        assertNoFileOfTheItemIsPublic("embargoedAccess without an end date");
    }

    /**
     * Verifies that a present but empty {@code dc.date.embargoend} is refused rather than read as "no
     * embargo"; it is a broken export.
     */
    @Test
    public void testBlankEmbargoEndIsRefused() throws Exception {
        assertBrokenEmbargoPackageIsRefused("", "an empty dc.date.embargoend");
    }

    /**
     * Verifies that a bare year keeps the day {@code DCDate} mapped it to, 1 January; reading it as
     * 31 December would extend embargoes that repositories already live with.
     */
    @Test
    public void testYearOnlyEmbargoEndIsFirstOfJanuary() throws Exception {
        int year = LocalDate.now(ZoneOffset.UTC).getYear() + 1;
        assertEmbargoIsAppliedFrom(String.valueOf(year), LocalDate.of(year, 1, 1).plusDays(1));
    }

    /**
     * Verifies that {@code yyyy-MM} keeps the day {@code DCDate} mapped it to, the first of that month.
     */
    @Test
    public void testYearMonthEmbargoEndIsFirstOfMonth() throws Exception {
        YearMonth yearMonth = YearMonth.from(LocalDate.now(ZoneOffset.UTC).plusYears(1));
        assertEmbargoIsAppliedFrom(yearMonth.toString(), yearMonth.atDay(1).plusDays(1));
    }

    /**
     * Verifies that a full ISO timestamp is truncated to its UTC day, which is then the last closed day.
     */
    @Test
    public void testIsoTimestampEmbargoEndIsTruncatedToUtcDay() throws Exception {
        LocalDate embargoEndDay = LocalDate.now(ZoneOffset.UTC).plusYears(1);
        assertEmbargoIsAppliedFrom(embargoEndDay + "T00:00:00Z", embargoEndDay.plusDays(1));
    }

    /**
     * Verifies that a policy which cannot be written stops the import instead of being logged and forgotten.
     * The failure is injected because breaking the resourcepolicy table would take the rest of the suite down.
     */
    @Test
    public void testFailureToWriteThePolicyIsNotSwallowed() throws Exception {
        Item item = importEmbargoedItem();

        ItemImportServiceImpl service = serviceWithTestDependencies();
        ResourcePolicyService failingPolicies = mock(ResourcePolicyService.class);
        when(failingPolicies.create(any(), any(), any()))
                .thenThrow(new SQLException("resource policy store is down"));
        service.resourcePolicyService = failingPolicies;

        try {
            service.processEmbargoMetadata(context, item);
            fail("an embargo policy that could not be written has to stop the import. The item is archived a"
                    + " few lines later in addItem and installItem then hands its bitstreams the collection"
                    + " undated default READ policy, so a swallowed failure here publishes the files.");
        } catch (Exception expected) {
            // fail closed: ItemImport rolls the import back
        }
    }

    /**
     * Same for the other collaborator: without the {@code Anonymous} group no embargo policy can be created,
     * and an item archived without one is public by collection default.
     */
    @Test
    public void testMissingAnonymousGroupIsNotSwallowed() throws Exception {
        Item item = importEmbargoedItem();

        ItemImportServiceImpl service = serviceWithTestDependencies();
        // a GroupService whose findByName answers null, which is the branch under test
        service.groupService = mock(GroupService.class);

        try {
            service.processEmbargoMetadata(context, item);
            fail("without the Anonymous group the embargo policy cannot be created, and archiving the item"
                    + " anyway leaves it public under the collection default policies");
        } catch (Exception expected) {
            // fail closed
        }
    }

    /**
     * Writes a SAF package with the given access right and embargo end date into a fresh source directory.
     *
     * @param accessRight value of dc.rights.access, {@code null} to leave the field out
     * @param embargoEnd  value of dc.date.embargoend, {@code null} to leave the field out
     * @param content     payload of the single ORIGINAL bitstream
     * @return the item directory; its parent is the source directory to hand to the import
     */
    private Path safPackage(String accessRight, String embargoEnd, String content) throws Exception {
        Path safDir = Files.createDirectory(Path.of(tempDir.toString() + "/test"));
        Path itemDir = Files.createDirectory(Path.of(safDir.toString() + "/item_000"));

        StringBuilder dublinCore = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<dublin_core schema=\"dc\">\n")
                .append("    <dcvalue element=\"title\" qualifier=\"none\">").append(ITEM_TITLE)
                .append("</dcvalue>\n");
        if (accessRight != null) {
            dublinCore.append("    <dcvalue element=\"rights\" qualifier=\"access\">").append(accessRight)
                    .append("</dcvalue>\n");
        }
        if (embargoEnd != null) {
            dublinCore.append("    <dcvalue element=\"date\" qualifier=\"embargoend\">").append(embargoEnd)
                    .append("</dcvalue>\n");
        }
        dublinCore.append("</dublin_core>");
        Files.writeString(Path.of(itemDir.toString() + "/dublin_core.xml"), dublinCore.toString());

        Files.writeString(Files.createFile(Path.of(itemDir.toString() + "/contents")), "test.txt");
        Files.writeString(Files.createFile(Path.of(itemDir.toString() + "/test.txt")), content);
        return itemDir;
    }

    /**
     * Runs {@code dspace import -a} on the source directory.
     *
     * @param safDir source directory holding the item directories
     * @return the exception the script reported, or {@code null} when it ran through
     */
    private Exception runImport(Path safDir) throws Exception {
        String[] args = new String[] { "import", "-a", "-e", admin.getEmail(), "-c", collection.getID().toString(),
                "-s", safDir.toString(), "-m", tempDir.toString() + "/mapfile.out" };
        try {
            runDSpaceScript(args);
            return null;
        } catch (Exception reported) {
            return reported;
        }
    }

    /**
     * Both halves of "fail closed" for a package whose {@code dc.date.embargoend} cannot be used: the failure
     * is reported, and no file of that package ends up readable.
     */
    private void assertBrokenEmbargoPackageIsRefused(String embargoEnd, String what) throws Exception {
        Path itemDir = safPackage("embargoedAccess", embargoEnd, "TEST CONTENT " + what);

        Exception reported = runImport(itemDir.getParent());
        assertNotNull(what + " has to be reported to the operator instead of being archived as a public item: "
                + describeArchived(ITEM_TITLE), reported);
        assertNoFileOfTheItemIsPublic(what);
    }

    /**
     * No ORIGINAL bitstream of an archived item with this test title may be readable by an anonymous visitor.
     * A refused import leaves no item at all, which is why the loop may legitimately find nothing.
     */
    private void assertNoFileOfTheItemIsPublic(String what) throws Exception {
        Iterator<Item> items = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE);
        while (items.hasNext()) {
            Item item = items.next();
            for (Bundle bundle : item.getBundles("ORIGINAL")) {
                for (Bitstream bitstream : bundle.getBitstreams()) {
                    assertFalse("the package says dc.rights.access=embargoedAccess and " + what + ", so this"
                            + " file must not be readable by an anonymous visitor: " + describe(bitstream),
                            anonymousCanRead(bitstream));
                }
            }
        }
    }

    /**
     * A {@code dc.date.embargoend} in one of the shapes {@code DCDate} accepted still produces an embargo, on
     * the day {@code DCDate} mapped it to.
     *
     * @param embargoEnd       value written into dc.date.embargoend
     * @param expectedStartDay UTC day the files are expected to open, i.e. embargo end day + 1
     */
    private void assertEmbargoIsAppliedFrom(String embargoEnd, LocalDate expectedStartDay) throws Exception {
        Path itemDir = safPackage("embargoedAccess", embargoEnd, "TEST CONTENT " + embargoEnd);

        assertNull("dc.date.embargoend=" + embargoEnd + " was accepted by DCDate, so the packages of this"
                + " repository use it and the import must not fail on it", runImport(itemDir.getParent()));

        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("Item should be created", item);
        Bitstream bitstream = item.getBundles("ORIGINAL").get(0).getBitstreams().get(0);

        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals("exactly one Anonymous READ policy may remain: " + describe(bitstream),
                1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertNotNull("the embargo policy has to be dated", embargoPolicy.getStartDate());
        assertEquals("dc.date.embargoend=" + embargoEnd + " has to mean the same day it meant with DCDate,"
                        + " and the files open the day after it",
                expectedStartDay.toString(), utcDay(embargoPolicy.getStartDate()));
        assertEquals("the embargo policy has to carry the access condition name",
                EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals("the embargo policy has to be TYPE_CUSTOM",
                ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertFalse("an embargoed file must not be downloadable by an anonymous visitor: " + describe(bitstream),
                anonymousCanRead(bitstream));
    }

    /**
     * Imports one valid, still running embargo and returns the archived item.
     */
    private Item importEmbargoedItem() throws Exception {
        Path itemDir = safPackage("embargoedAccess", EMBARGOEND_DATE_FUTURE, "TEST CONTENT FOR INJECTION");
        assertNull("fixture precondition: the valid package has to import", runImport(itemDir.getParent()));

        Item item = itemService.findByMetadataField(context, "dc", "title", null, ITEM_TITLE).next();
        assertNotNull("fixture precondition: the item has to exist", item);
        return item;
    }

    /**
     * An {@code ItemImportServiceImpl} whose collaborators the test can replace one by one. Only the three the
     * embargo code uses are wired; the service is never asked to import anything through this instance.
     */
    private ItemImportServiceImpl serviceWithTestDependencies() {
        ItemImportServiceImpl service = new ItemImportServiceImpl();
        service.itemService = itemService;
        service.groupService = groupService;
        service.resourcePolicyService = resourcePolicyService;
        return service;
    }

    /**
     * The policy start date as the UTC calendar day it is stored as. {@code SimpleDateFormat} would otherwise
     * render midnight UTC in the time zone of the build machine and report the previous day west of Greenwich.
     */
    private String utcDay(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    /**
     * Every archived item with this title and the policies of its ORIGINAL bitstreams, for failure messages:
     * a refused package and a published one are otherwise indistinguishable.
     */
    private String describeArchived(String title) throws Exception {
        StringBuilder sb = new StringBuilder(System.lineSeparator());
        Iterator<Item> items = itemService.findByMetadataField(context, "dc", "title", null, title);
        if (!items.hasNext()) {
            sb.append("      <no archived item with this title>").append(System.lineSeparator());
        }
        while (items.hasNext()) {
            Item item = items.next();
            sb.append("      item=").append(item.getID()).append(" archived=").append(item.isArchived())
                    .append(System.lineSeparator());
            for (Bundle bundle : item.getBundles("ORIGINAL")) {
                for (Bitstream bitstream : bundle.getBitstreams()) {
                    sb.append(describe(bitstream));
                }
            }
        }
        return sb.toString();
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
            List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
            assertEquals("exactly one Anonymous READ policy may remain on each bitstream: " + describe(bitstream),
                    1, anonymousRead.size());

            ResourcePolicy embargoPolicy = anonymousRead.get(0);
            assertNotNull("Each embargo policy should have start date", embargoPolicy.getStartDate());
            assertEquals("the embargo policy has to be TYPE_CUSTOM",
                    ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
            assertEquals("the embargo policy has to carry the access condition name",
                    EMBARGO_POLICY_NAME, embargoPolicy.getRpName());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            assertEquals("Each embargo start date should be embargoend + 1 day",
                    EXPECTED_POLICY_START_DATE, sdf.format(embargoPolicy.getStartDate()));

            assertFalse("an embargoed file must not be downloadable by an anonymous visitor: "
                    + describe(bitstream), anonymousCanRead(bitstream));
        }
    }
}
