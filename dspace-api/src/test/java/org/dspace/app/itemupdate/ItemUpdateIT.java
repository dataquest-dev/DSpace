/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link ItemUpdate} and {@link ItemArchive}.
 */
public class ItemUpdateIT extends AbstractIntegrationTestWithDatabase {

    /** rpName written by the shipped implementation; the fix has to adopt and normalise it. */
    private static final String STANDARD_EMBARGO = "Standard Embargo";

    /** The single normalised rpName, matching the access condition name in access-conditions.xml. */
    private static final String EMBARGO_POLICY_NAME = "embargo";

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    private MetadataFieldService metadataFieldService =
            ContentServiceFactory.getInstance().getMetadataFieldService();

    private Collection collection;
    private Group anonymousGroup;
    private Path tempDir;
    private String previousHandlePrefix;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();

        ensureMetadataFieldExists("identifier", "thesis");
        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("itemUpdateIT");
    }

    @After
    @Override
    public void destroy() throws Exception {
        ItemUpdate.HANDLE_PREFIX = previousHandlePrefix;
        if (tempDir != null) {
            PathUtils.deleteDirectory(tempDir);
        }
        super.destroy();
    }

    /**
     * The embargo refusals are counted per item and never abort the run, so the exit code is the only place
     * an operator's script can see them. Every test below asserts {@code embargoSyncFailures}; this one
     * asserts the step that turns that counter into the process exit code, which is otherwise only reachable
     * through {@code main()} and its {@code System.exit}.
     */
    @Test
    public void embargoSyncFailuresDecideTheExitCode() {
        assertEquals("a clean run has to exit 0", 0, ItemUpdate.exitStatus(0, 0));
        assertEquals("a single unsynchronised bitstream has to fail the run", 1, ItemUpdate.exitStatus(0, 1));
        assertEquals("several problems still fail the run once", 1, ItemUpdate.exitStatus(0, 7));
        assertEquals("an already failed run stays failed", 1, ItemUpdate.exitStatus(1, 0));
    }

    @Test
    public void containsEmbargoFieldHandlesNullsAndWhitespace() {
        assertFalse(ItemUpdate.containsEmbargoField(null));
        assertFalse(ItemUpdate.containsEmbargoField(new String[] { "dc.title", null }));
        assertTrue(ItemUpdate.containsEmbargoField(new String[] { " dc.rights.access " }));
        assertTrue(ItemUpdate.containsEmbargoField(new String[] { "dc.date.embargoend" }));
    }

    @Test
    public void itemArchiveCreateResolvesByCanonicalHandleUri() throws Exception {
        Item item = createItem("Canonical Handle Item");
        String canonicalUri = ItemUpdate.HANDLE_PREFIX + item.getHandle();

        Path itemDir = createSafItemDirectory(dublinCore(canonicalUri, null));

        ItemArchive archive = ItemArchive.create(context, itemDir.toFile(), null);

        assertEquals(item.getID(), archive.getItem().getID());
    }

    @Test
    public void itemArchiveCreateResolvesByIdentifierUriFallback() throws Exception {
        String customUri = "https://example.org/custom-uri-1001";
        Item item = createItem("URI Fallback Item", "identifier", "uri", customUri);

        Path itemDir = createSafItemDirectory(dublinCore(customUri, null));

        ItemArchive archive = ItemArchive.create(context, itemDir.toFile(), null);

        assertEquals(item.getID(), archive.getItem().getID());
    }

    @Test
    public void itemArchiveCreateResolvesByThesisFallback() throws Exception {
        String thesisId = "THESIS-2026-0001";
        Item item = createItem("Thesis Fallback Item", "identifier", "thesis", thesisId);

        Path itemDir = createSafItemDirectory(dublinCore("https://example.org/unresolvable", thesisId));

        ItemArchive archive = ItemArchive.create(context, itemDir.toFile(), null);

        assertEquals(item.getID(), archive.getItem().getID());
    }

    @Test
    public void itemArchiveCreateFailsForAmbiguousIdentifierUri() throws Exception {
        String duplicateUri = "https://example.org/duplicate-uri";
        createItem("Ambiguous Item 1", "identifier", "uri", duplicateUri);
        createItem("Ambiguous Item 2", "identifier", "uri", duplicateUri);

        Path itemDir = createSafItemDirectory(dublinCore(duplicateUri, null));

        try {
            ItemArchive.create(context, itemDir.toFile(), null);
            fail("Expected IllegalArgumentException for ambiguous item resolution");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unable to resolve item"));
        }
    }

    @Test
    public void syncEmbargoPoliciesDatesTheAnonymousReadPolicyAndBlocksAccess() throws Exception {
        String futureDate = LocalDate.now().plusDays(14).toString();
        Item item = createItem("Standard Embargo Item",
                               "rights", "access", "embargoedAccess",
                               "date", "embargoend", futureDate);
        Bitstream bitstream = createBitstream(item, "standard.txt");

        createAnonymousReadPolicy(bitstream, null, "Immediate Read");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);
        assertEquals("setting a future embargo is not a failure", 0, itemUpdate.embargoSyncFailures);

        // Exactly one Anonymous READ policy has to be left behind. A second, undated one would silently
        // defeat the embargo, so counting is part of the assertion, not a detail.
        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals(1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertEquals(EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertNotNull(embargoPolicy.getStartDate());
        assertEquals(LocalDate.parse(futureDate).plusDays(1), toLocalDate(embargoPolicy.getStartDate()));
        assertFalse(anonymousCanRead(bitstream));
    }

    @Test
    public void syncEmbargoPoliciesAppliesEmbargoWithoutAccessRightMetadata() throws Exception {
        String futureDate = LocalDate.now().plusDays(21).toString();
        Item item = createItem("Special Case Embargo Item", "date", "embargoend", futureDate);
        Bitstream bitstream = createBitstream(item, "special.txt");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);
        assertEquals("setting a future embargo is not a failure", 0, itemUpdate.embargoSyncFailures);

        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals(1, anonymousRead.size());

        // The distinction between "standard" and "special case" embargo only ever existed in the rpName.
        // Both are now written as the single access condition name from access-conditions.xml, which also
        // keeps the value inside the 30 character resourcepolicy.rpname column.
        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertEquals(EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertNotNull(embargoPolicy.getStartDate());
        assertEquals(LocalDate.parse(futureDate).plusDays(1), toLocalDate(embargoPolicy.getStartDate()));
        assertFalse(anonymousCanRead(bitstream));
    }

    /**
     * A blank {@code dc.date.embargoend} is a broken export, not an instruction to change anything.
     *
     * <p>This test used to assert only {@code assertFalse(hasSafEmbargoPolicy)}, which an empty policy table
     * satisfies just as well as a correct one - and an empty policy table is exactly the customer bug (HTTP 401
     * on every download). It now asserts what the operator actually cares about: not a single resource policy
     * was touched.</p>
     */
    @Test
    public void syncEmbargoPoliciesLeavesPoliciesUntouchedWhenEmbargoDateInvalid() throws Exception {
        Item item = createItem("Invalid Date Item", "date", "embargoend", "");
        Bitstream bitstream = createBitstream(item, "invalid.txt");
        ResourcePolicy legacyPolicy = replaceAnonymousReadPolicies(bitstream,
                new Date(System.currentTimeMillis() + 86_400_000L), STANDARD_EMBARGO);
        bitstream = context.reloadEntity(bitstream);

        List<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable, otherwise the"
                        + " 'nothing changed' assertions below say nothing about a leak",
                anonymousCanRead(bitstream));

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);

        // Spec row 7: a blank end date is broken input, and the run has to exit non-zero because of it.
        assertEquals("a blank dc.date.embargoend has to fail the run", 1, itemUpdate.embargoSyncFailures);
        assertEquals(1, ItemUpdate.exitStatus(0, itemUpdate.embargoSyncFailures));
        assertEquals(idsBefore, policyIds(bitstream));
        assertFalse("an unparseable dc.date.embargoend published an embargoed file",
                anonymousCanRead(bitstream));

        // The dated policy the run could not validate is still there, unchanged, under its legacy name.
        ResourcePolicy reloadedLegacy = resourcePolicyService.find(context, legacyPolicy.getID());
        assertNotNull(reloadedLegacy);
        assertEquals(STANDARD_EMBARGO, reloadedLegacy.getRpName());
    }

    @Test
    public void processArchiveUpdatesEmbargoMetadataAndResyncsEmbargoPolicy() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(5).toString();
        String newEmbargoDate = LocalDate.now().plusDays(35).toString();

        Item item = createItem("Embargo Update Item",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "update-embargo.txt");

        LocalDate oldPolicyDate = LocalDate.parse(oldEmbargoDate).plusDays(1);
        Date oldPolicyStart = Date.from(oldPolicyDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy = createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);
        Integer legacyPolicyId = legacyPolicy.getID();

        assertEquals("re-dating an embargo is not a failure", 0,
                runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, "embargoedAccess", newEmbargoDate)));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> embargoDates = itemService.getMetadata(reloadedItem, "dc", "date", "embargoend", Item.ANY);
        assertEquals(1, embargoDates.size());
        assertEquals(newEmbargoDate, embargoDates.get(0).getValue());

        LocalDate expectedPolicyDate = LocalDate.parse(newEmbargoDate).plusDays(1);
        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(reloadedBitstream);
        assertEquals(1, anonymousRead.size());

        // The pre-existing policy is re-dated in place instead of being deleted and re-created. Between a
        // delete and a create the file has no policy at all, which is the state the customer report was about.
        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertEquals(legacyPolicyId, embargoPolicy.getID());
        assertEquals(EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertNotNull(embargoPolicy.getStartDate());
        assertFalse(toLocalDate(embargoPolicy.getStartDate()).equals(oldPolicyDate));
        assertEquals(expectedPolicyDate, toLocalDate(embargoPolicy.getStartDate()));
        assertFalse(anonymousCanRead(reloadedBitstream));
    }

    /**
     * Blanking {@code dc.date.embargoend} in the SAF archive is a broken export. Same reasoning as
     * {@link #syncEmbargoPoliciesLeavesPoliciesUntouchedWhenEmbargoDateInvalid()}: the old
     * {@code assertFalse(hasSafEmbargoPolicy)} was also satisfied by a bitstream stripped of every policy.
     */
    @Test
    public void processArchiveUpdateWithBlankEmbargoDateLeavesPoliciesUntouched() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(12).toString();

        Item item = createItem("Blank Embargo Date Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "blank-embargo-date.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy = replaceAnonymousReadPolicies(bitstream, oldPolicyStart, STANDARD_EMBARGO);
        bitstream = context.reloadEntity(bitstream);

        List<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable, otherwise the"
                        + " 'nothing changed' assertions below say nothing about a leak",
                anonymousCanRead(bitstream));

        assertEquals("a blank dc.date.embargoend has to fail the run", 1,
                runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, "embargoedAccess", "")));

        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        assertEquals(idsBefore, policyIds(reloadedBitstream));
        assertFalse("a blank dc.date.embargoend published an embargoed file",
                anonymousCanRead(reloadedBitstream));

        ResourcePolicy reloadedLegacy = resourcePolicyService.find(context, legacyPolicy.getID());
        assertNotNull(reloadedLegacy);
        assertEquals(STANDARD_EMBARGO, reloadedLegacy.getRpName());
    }

    /**
     * A SAF package that does not carry {@code dc.date.embargoend} carries no instruction about the embargo,
     * and an absent field must never open a file. {@code syncEmbargoPolicies} runs for every item of a batch
     * whose target fields mention an embargo field, so reading "field missing" as "lift the embargo" would
     * publish every embargoed item of a batch whose packages happen not to carry it. A file is opened by
     * writing a {@code dc.date.embargoend} that lies in the past.
     */
    @Test
    public void processArchiveUpdateRemovingEmbargoMetadataLeavesPoliciesUntouched() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(10).toString();

        Item item = createItem("Remove Embargo Metadata Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "remove-embargo.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        // The collection default leaves an undated Anonymous READ policy on a new bitstream. It has to go,
        // otherwise the file is readable throughout and the assertions below would prove nothing.
        ResourcePolicy legacyPolicy = replaceAnonymousReadPolicies(bitstream, oldPolicyStart, STANDARD_EMBARGO);
        Integer legacyPolicyId = legacyPolicy.getID();
        bitstream = context.reloadEntity(bitstream);

        List<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable",
                anonymousCanRead(bitstream));

        int failures = runEmbargoMetadataUpdate(item, dublinCore(item));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> rightsAccess = itemService.getMetadata(reloadedItem, "dc", "rights", "access", Item.ANY);
        List<MetadataValue> embargoDates = itemService.getMetadata(reloadedItem, "dc", "date", "embargoend", Item.ANY);
        assertTrue(rightsAccess.isEmpty());
        assertTrue("fixture precondition: dc.date.embargoend has to be gone from the item", embargoDates.isEmpty());

        // Nothing happened: same policy rows, same name, same start date, same answer to "can anyone read it".
        assertEquals("removing dc.date.embargoend must not add or remove a single resource policy",
                idsBefore, policyIds(reloadedBitstream));
        assertEquals(1, anonymousReadPolicies(reloadedBitstream).size());

        ResourcePolicy untouchedPolicy = anonymousReadPolicies(reloadedBitstream).get(0);
        assertEquals(legacyPolicyId, untouchedPolicy.getID());
        assertNotNull("removing dc.date.embargoend must not clear the embargo start date",
                untouchedPolicy.getStartDate());
        assertEquals(STANDARD_EMBARGO, untouchedPolicy.getRpName());
        assertFalse("removing dc.date.embargoend published an embargoed file", anonymousCanRead(reloadedBitstream));

        // "No instruction" is not a failure - the batch has to keep its exit code 0.
        assertEquals("a SAF package without dc.date.embargoend is not an error", 0, failures);
    }

    @Test
    public void processArchiveUpdateWithEmbargoDateAndNoRightsAppliesEmbargo() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(8).toString();
        String newEmbargoDate = LocalDate.now().plusDays(25).toString();

        Item item = createItem("Special Case Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "special-case-update.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy = createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);
        Integer legacyPolicyId = legacyPolicy.getID();

        assertEquals("an embargo end date without dc.rights.access is not a failure", 0,
                runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, null, newEmbargoDate)));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> rightsAccess = itemService.getMetadata(reloadedItem, "dc", "rights", "access", Item.ANY);
        assertTrue(rightsAccess.isEmpty());

        LocalDate expectedPolicyDate = LocalDate.parse(newEmbargoDate).plusDays(1);
        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(reloadedBitstream);
        assertEquals(1, anonymousRead.size());

        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertEquals(legacyPolicyId, embargoPolicy.getID());
        assertEquals(EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertNotNull(embargoPolicy.getStartDate());
        assertEquals(expectedPolicyDate, toLocalDate(embargoPolicy.getStartDate()));
        assertFalse(anonymousCanRead(reloadedBitstream));
    }

    private void ensureMetadataFieldExists(String element, String qualifier) throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField existingField = metadataFieldService.findByElement(context, dcSchema, element, qualifier);
        if (existingField == null) {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, element, qualifier, null).build();
        }
    }

    private Item createItem(String title, String... metadataTriples) throws Exception {
        context.turnOffAuthorisationSystem();

        ItemBuilder builder = ItemBuilder.createItem(context, collection)
                .withTitle(title);

        for (int i = 0; i + 2 < metadataTriples.length; i += 3) {
            builder.withMetadata("dc", metadataTriples[i], metadataTriples[i + 1], metadataTriples[i + 2]);
        }

        Item item = builder.build();
        context.restoreAuthSystemState();
        return item;
    }

    private Bitstream createBitstream(Item item, String name) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)))
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    /**
     * Leaves the bitstream with exactly one Anonymous READ policy: the collection's undated default is
     * removed first. Without that step a "the file is embargoed" fixture is not embargoed at all.
     */
    private ResourcePolicy replaceAnonymousReadPolicies(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        context.turnOffAuthorisationSystem();
        authorizeService.removePoliciesActionFilter(context, bitstream, Constants.READ);
        context.restoreAuthSystemState();
        return createAnonymousReadPolicy(bitstream, startDate, name);
    }

    private ResourcePolicy createAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        context.turnOffAuthorisationSystem();
        ResourcePolicyBuilder builder = ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(name);

        if (startDate != null) {
            builder.withStartDate(startDate);
        }
        ResourcePolicy policy = builder.build();
        context.restoreAuthSystemState();
        return policy;
    }

    private boolean isAnonymousPolicy(ResourcePolicy policy) {
        return policy.getGroup() != null && policy.getGroup().equals(anonymousGroup);
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws Exception {
        return resourcePolicyService.find(context, bitstream, Constants.READ).stream()
                .filter(this::isAnonymousPolicy)
                .collect(Collectors.toList());
    }

    /**
     * Identity of every resource policy on the bitstream. A policy deleted and immediately re-created keeps
     * the count but loses its id, so ids are what "untouched" has to be measured with.
     */
    private List<Integer> policyIds(Bitstream bitstream) throws Exception {
        return authorizeService.getPolicies(context, bitstream).stream()
                .map(ResourcePolicy::getID)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * What an anonymous visitor of the REST API gets: the authorisation system asked as nobody, with the
     * test's own turnOffAuthorisationSystem calls temporarily unwound.
     */
    private boolean anonymousCanRead(Bitstream bitstream) throws SQLException {
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

    private Path createSafItemDirectory(String dublinCoreContent) throws IOException {
        Path safDir = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        Path itemDir = Files.createDirectory(safDir.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);
        return itemDir;
    }

    private String dublinCore(String identifierUri, String thesisIdentifier) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<dublin_core schema=\"dc\">\n")
          .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
          .append(identifierUri)
          .append("</dcvalue>\n");

        if (thesisIdentifier != null) {
            sb.append("    <dcvalue element=\"identifier\" qualifier=\"thesis\">")
              .append(thesisIdentifier)
              .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }

    /**
     * @return the number of embargo problems reported by the run; {@link ItemUpdate#exitStatus(int, int)} is
     *         what turns it into the exit code of {@code dspace itemupdate}
     */
    private int runEmbargoMetadataUpdate(Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("update-source-" + System.nanoTime()));
        Files.createFile(sourceRoot.resolve(ItemUpdate.SUPPRESS_UNDO_FILENAME));

        Path itemDir = Files.createDirectory(sourceRoot.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);

        ItemUpdate itemUpdate = new ItemUpdate();
        DeleteMetadataAction deleteAction =
            (DeleteMetadataAction) itemUpdate.actionMgr.getUpdateAction(DeleteMetadataAction.class);
        deleteAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        AddMetadataAction addAction =
            (AddMetadataAction) itemUpdate.actionMgr.getUpdateAction(AddMetadataAction.class);
        addAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        context.turnOffAuthorisationSystem();
        itemUpdate.processArchive(context, sourceRoot.toString(), null, null, true, false, true);
        context.restoreAuthSystemState();

        // Force entity reload in caller assertions after update transaction.
        context.uncacheEntity(item);
        return itemUpdate.embargoSyncFailures;
    }

    private String dublinCore(Item item) {
        String identifierUri = ItemUpdate.HANDLE_PREFIX + item.getHandle();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<dublin_core schema=\"dc\">\n"
                + "    <dcvalue element=\"identifier\" qualifier=\"uri\">" + identifierUri + "</dcvalue>\n"
                + "</dublin_core>";
    }

    private String dublinCoreWithEmbargo(Item item, String rightsAccess, String embargoEndDate) {
        String identifierUri = ItemUpdate.HANDLE_PREFIX + item.getHandle();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<dublin_core schema=\"dc\">\n")
                .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
                .append(identifierUri)
                .append("</dcvalue>\n");

        if (rightsAccess != null) {
            sb.append("    <dcvalue element=\"rights\" qualifier=\"access\">")
                    .append(rightsAccess)
                    .append("</dcvalue>\n");
        }

        if (embargoEndDate != null) {
            sb.append("    <dcvalue element=\"date\" qualifier=\"embargoend\">")
                    .append(embargoEndDate.isEmpty() ? " " : embargoEndDate)
                    .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }

    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}