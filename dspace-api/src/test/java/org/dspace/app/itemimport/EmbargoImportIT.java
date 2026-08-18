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
import java.time.LocalDate;
import java.util.List;
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
     * The single rpName both SAF tools write. Deliberately repeated here instead of referencing
     * {@code SafEmbargoConstants}: the value ends up in the database and must not change silently, and it has
     * to fit the 30 character {@code resourcepolicy.rpname} column.
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

        // Counting is part of the assertion, not a detail: installItem clones the collection's undated
        // DEFAULT_BITSTREAM_READ onto a bitstream that has no Anonymous READ policy yet, and such a second,
        // undated policy would make the file downloadable throughout the embargo. Picking the first matching
        // policy with findFirst() cannot see that.
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
     * An embargo that has already expired must not be turned into a policy - but "no embargo policy" is only
     * half the requirement. The original assertion ("no Anonymous policy carries a start date") is satisfied
     * just as well by a bitstream that has no policy at all and answers HTTP 401, which is the failure mode
     * this branch is fixing. The test therefore also asserts that the file really is publicly readable, which
     * on the import path means the collection default policies installItem applies.
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

        // The point of not writing an expired embargo policy is that the file stays available. Zero policies
        // would satisfy the assertion above and leave every download at HTTP 401.
        assertTrue("An expired embargo end date must leave the bitstream readable, not policy-less",
                anonymousCanRead(bitstream));
    }

    /**
     * The regression this test exists for: {@code dspace import -a -w} puts the item into the workflow, and
     * approving it calls {@code installItem}, which applies the collection's default policies. A bitstream
     * that carries no embargo policy at that moment has no Anonymous READ policy at all, so
     * {@code ItemServiceImpl.addDefaultPoliciesNotInPlace} clones the collection's <em>undated</em>
     * DEFAULT_BITSTREAM_READ onto it - and the file is public from the second it is approved, while its
     * metadata still says {@code embargoedAccess} with a future end date.
     *
     * <p>The embargo policy therefore has to be created on the common path, before the workflow starts. It
     * discloses nothing while the item waits for approval, because {@code AuthorizeServiceImpl} ignores
     * {@code TYPE_CUSTOM} policies on a bitstream that belongs to no installed item (DS-2614) - which the
     * assertion on the workflow item below pins down.</p>
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

        // itemService.findByMetadataField only returns archived items, and this one is deliberately not
        // archived yet. The mapfile is what the operator gets instead: "<package directory> <item uuid>".
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

        // The moment of the leak. Without the embargo policy the bitstream reaches installItem with no
        // Anonymous READ policy, gets the collection default cloned onto it, and is public right here.
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
     * The "special case" branch: {@code dc.date.embargoend} without {@code dc.rights.access=embargoedAccess}.
     *
     * <p>It had no test at all, which is why nobody noticed that it wrote the 48 character rpName
     * "Special Case Embargo - No access rights metadata" into a {@code varchar(30)} column - on PostgreSQL
     * that aborts the whole import, and the SQL error names the column, not the branch that produced it.</p>
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
     * A workflow item that outlives its test keeps a {@code cwf_pooltask} row referencing the collection's
     * workflow group, so {@code AbstractBuilder.cleanupObjects()} cannot delete that group - and every
     * following test in this class then fails in cleanup instead of where the real problem is.
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
     * Every resource policy of the bitstream, for failure messages - "the assertion failed" is not enough to
     * tell an extra undated policy from a missing one.
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
     * What an anonymous visitor gets, with the test's own turnOffAuthorisationSystem calls temporarily unwound.
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
