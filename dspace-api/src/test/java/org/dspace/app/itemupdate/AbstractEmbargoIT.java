/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.output.TeeOutputStream;
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
import org.dspace.content.service.BundleService;
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

/**
 * Fixture shared by the {@link ItemUpdate} embargo integration tests: a collection whose bitstreams inherit
 * an undated {@code Anonymous}/{@code READ} policy, the metadata fields the embargo actions target, and the
 * plumbing needed to drive a {@code dspace itemupdate} run against a SAF archive built on the fly.
 *
 * <p>Assertions specific to one scenario stay in the test class that makes them; only fixture building and
 * policy inspection live here.</p>
 */
public abstract class AbstractEmbargoIT extends AbstractIntegrationTestWithDatabase {

    /** The single normalised rpName, matching the access condition name in access-conditions.xml. */
    protected static final String EMBARGO_POLICY_NAME = "embargo";

    /** rpName written by earlier versions; fixtures use it so that normalisation is exercised. */
    protected static final String LEGACY_EMBARGO_POLICY_NAME = "Standard Embargo";

    /** Bundle holding the text extracted from a file. */
    protected static final String TEXT_BUNDLE = "TEXT";

    /** Bundle holding the thumbnail rendered from a file. */
    protected static final String THUMBNAIL_BUNDLE = "THUMBNAIL";

    /**
     * Sentinel for {@link #deletePolicies(Bitstream, int)} meaning "every action", picked so it can never
     * collide with a real value of {@link Constants#actionText}.
     */
    protected static final int ALL_ACTIONS = -1;

    protected final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected final BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    protected final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected final ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    protected final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    protected final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected final MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    protected final MetadataFieldService metadataFieldService =
            ContentServiceFactory.getInstance().getMetadataFieldService();

    protected Collection collection;
    protected Group anonymousGroup;
    protected Path tempDir;

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

        // none of these exist in the test metadata registry, the update actions need them as targets
        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");
        ensureMetadataFieldExists("identifier", "thesis");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        // ItemArchive resolves items through this mutable static, it is restored in destroy()
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory(getClass().getSimpleName());
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

    protected void ensureMetadataFieldExists(String element, String qualifier) throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField existingField = metadataFieldService.findByElement(context, dcSchema, element, qualifier);
        if (existingField == null) {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, element, qualifier, null).build();
        }
    }

    /**
     * @param metadataTriples element, qualifier and value of extra {@code dc} metadata, in groups of three
     */
    protected Item createItem(String title, String... metadataTriples) throws Exception {
        context.turnOffAuthorisationSystem();
        ItemBuilder builder = ItemBuilder.createItem(context, collection).withTitle(title);
        for (int i = 0; i + 2 < metadataTriples.length; i += 3) {
            builder.withMetadata("dc", metadataTriples[i], metadataTriples[i + 1], metadataTriples[i + 2]);
        }
        Item item = builder.build();
        context.restoreAuthSystemState();
        return item;
    }

    /**
     * A bitstream in the ORIGINAL bundle. It inherits the collection DEFAULT_BITSTREAM_READ and so carries one
     * undated Anonymous READ policy, the state a freshly imported SAF item is in.
     */
    protected Bitstream createOriginalBitstream(Item item, String name) throws Exception {
        return createBitstreamInBundle(item, name, Constants.CONTENT_BUNDLE_NAME);
    }

    protected Bitstream createBitstreamInBundle(Item item, String name, String bundleName) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)), bundleName)
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    protected void reloadAll(List<Bitstream> bitstreams) throws SQLException {
        for (int i = 0; i < bitstreams.size(); i++) {
            bitstreams.set(i, context.reloadEntity(bitstreams.get(i)));
        }
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream. The authorisation state is a stack
     * the builders push and pop, so it is drained first - otherwise every read looks allowed.
     */
    protected boolean anonymousCanRead(Bitstream bitstream) throws SQLException {
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

    protected List<ResourcePolicy> allPolicies(Bitstream bitstream) throws SQLException {
        return new ArrayList<>(resourcePolicyService.find(context, bitstream));
    }

    protected List<ResourcePolicy> policiesForAction(Bitstream bitstream, int actionId) throws SQLException {
        return new ArrayList<>(resourcePolicyService.find(context, bitstream, actionId));
    }

    protected List<ResourcePolicy> readPolicies(Bitstream bitstream) throws SQLException {
        return policiesForAction(bitstream, Constants.READ);
    }

    protected List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws SQLException {
        return readPolicies(bitstream).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    /**
     * Every resource policy id of the bitstream. Ids rather than counts, so a policy that was deleted and
     * re-created is visible.
     */
    protected Set<Integer> policyIds(Bitstream bitstream) throws SQLException {
        Set<Integer> ids = new TreeSet<>();
        for (ResourcePolicy policy : allPolicies(bitstream)) {
            ids.add(policy.getID());
        }
        return ids;
    }

    /**
     * Deletes policies one by one, because the bulk removal helpers issue an HQL delete and leave the
     * in-memory collection stale.
     *
     * @param actionId action to delete, or {@link #ALL_ACTIONS} for every policy regardless of action
     */
    protected void deletePolicies(Bitstream bitstream, int actionId) throws Exception {
        List<ResourcePolicy> doomed = actionId == ALL_ACTIONS
                ? allPolicies(bitstream)
                : policiesForAction(bitstream, actionId);
        for (ResourcePolicy policy : doomed) {
            resourcePolicyService.delete(context, policy);
        }
    }

    protected ResourcePolicy addAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        return addAnonymousReadPolicy(bitstream, startDate, name, null);
    }

    protected ResourcePolicy addAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name,
                                                    String policyType) throws Exception {
        context.turnOffAuthorisationSystem();
        ResourcePolicyBuilder builder = ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(name);
        if (startDate != null) {
            builder.withStartDate(startDate);
        }
        if (policyType != null) {
            builder.withPolicyType(policyType);
        }
        ResourcePolicy policy = builder.build();
        context.restoreAuthSystemState();
        return policy;
    }

    /**
     * Leaves the bitstream with a single Anonymous READ policy, removing the collection's undated default
     * first; without that an "embargoed" fixture is not embargoed at all.
     */
    protected ResourcePolicy replaceAnonymousReadPolicies(Bitstream bitstream, Date startDate, String name)
            throws Exception {
        return replaceAnonymousReadPolicies(bitstream, startDate, name, null);
    }

    protected ResourcePolicy replaceAnonymousReadPolicies(Bitstream bitstream, Date startDate, String name,
                                                          String policyType) throws Exception {
        context.turnOffAuthorisationSystem();
        deletePolicies(bitstream, Constants.READ);
        context.restoreAuthSystemState();
        return addAnonymousReadPolicy(bitstream, startDate, name, policyType);
    }

    protected List<String> metadataValues(Item item, String element, String qualifier) {
        return itemService.getMetadata(item, "dc", element, qualifier, Item.ANY).stream()
                .map(MetadataValue::getValue)
                .collect(Collectors.toList());
    }

    protected String singleMetadataValue(Item item, String element, String qualifier) {
        List<String> values = metadataValues(item, element, qualifier);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Calendar day of a date. {@code ResourcePolicy.startDate} is mapped as {@code @Temporal(DATE)}, so after
     * a round trip through the database it comes back as a day-granular {@code java.sql.Date}.
     */
    protected LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * The same day rendered for a failure message, {@code null} included.
     */
    protected String day(Date date) {
        return date == null ? "<none>" : toLocalDate(date).toString();
    }

    /**
     * Full identity and content of one policy. Comparing these rather than counts makes both
     * delete-and-recreate and in-place mutation visible.
     */
    protected String fingerprint(ResourcePolicy policy) {
        return String.format("id=%s action=%s group=%s eperson=%s rpType=%s rpName=%s start=%s end=%s",
                policy.getID(),
                Constants.actionText[policy.getAction()],
                policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                policy.getEPerson() == null ? "<none>" : policy.getEPerson().getEmail(),
                policy.getRpType(),
                policy.getRpName(),
                day(policy.getStartDate()),
                day(policy.getEndDate()));
    }

    protected List<String> policyFingerprints(Bitstream bitstream) throws SQLException {
        List<String> fingerprints = new ArrayList<>();
        for (ResourcePolicy policy : allPolicies(bitstream)) {
            fingerprints.add(fingerprint(policy));
        }
        Collections.sort(fingerprints);
        return fingerprints;
    }

    protected Date startOfDayUtc(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    protected String pastDate() {
        return LocalDate.now().minusMonths(1).toString();
    }

    /**
     * A future end date, the branch that closes the file; a past one opens it again.
     */
    protected String futureDate() {
        return LocalDate.now().plusYears(1).toString();
    }

    /**
     * Builds a SAF {@code dublin_core.xml}. {@code ItemArchive.create} resolves the item by
     * {@code dc.identifier.uri == ItemUpdate.HANDLE_PREFIX + handle}.
     *
     * @param rightsAccess   value for {@code dc.rights.access}, or {@code null} to omit the element entirely
     * @param embargoEndDate {@code null} omits {@code dc.date.embargoend} entirely, the empty string writes a
     *                       blank value
     */
    protected String dublinCore(Item item, String rightsAccess, String embargoEndDate) {
        return dublinCoreWithEndDates(item, rightsAccess, embargoEndDate);
    }

    /**
     * The same document with more than one {@code dc.date.embargoend} value, emitted in the given order.
     */
    protected String dublinCoreWithEndDates(Item item, String rightsAccess, String... embargoEndDates) {
        return dublinCoreWithAccessRights(item, rightsAccess == null ? Collections.emptyList()
                : Collections.singletonList(rightsAccess), embargoEndDates);
    }

    /**
     * The same document carrying zero or more {@code dc.rights.access} values, for the contradictory metadata
     * an operator can put in a package.
     */
    protected String dublinCoreWithAccessRights(Item item, List<String> accessRights, String... embargoEndDates) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<dublin_core schema=\"dc\">\n")
                .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
                .append(ItemUpdate.HANDLE_PREFIX).append(item.getHandle())
                .append("</dcvalue>\n");

        for (String accessRight : accessRights) {
            sb.append("    <dcvalue element=\"rights\" qualifier=\"access\">")
                    .append(accessRight)
                    .append("</dcvalue>\n");
        }

        for (String embargoEndDate : embargoEndDates) {
            if (embargoEndDate == null) {
                continue;
            }
            sb.append("    <dcvalue element=\"date\" qualifier=\"embargoend\">")
                    // an empty XML element is dropped by the parser, a single space survives as a blank value
                    .append(embargoEndDate.isEmpty() ? " " : embargoEndDate)
                    .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation. {@code main()} is not used because it ends in {@code System.exit}.
     */
    protected Run runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        return runItemUpdate(new ItemUpdate(), item, dublinCoreContent);
    }

    /**
     * The same run driven by a caller supplied {@link ItemUpdate}, so that a test can make one step of the
     * synchronisation fail where a database error would.
     */
    protected Run runItemUpdate(ItemUpdate itemUpdate, Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        // without this marker processArchive writes an undo archive next to the source directory
        Files.createFile(sourceRoot.resolve(ItemUpdate.SUPPRESS_UNDO_FILENAME));

        Path itemDir = Files.createDirectory(sourceRoot.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);

        DeleteMetadataAction deleteAction =
                (DeleteMetadataAction) itemUpdate.actionMgr.getUpdateAction(DeleteMetadataAction.class);
        deleteAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        AddMetadataAction addAction =
                (AddMetadataAction) itemUpdate.actionMgr.getUpdateAction(AddMetadataAction.class);
        addAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        // ItemUpdate reports to System.out only, so the console has to be captured to assert on it; the
        // stream is teed so that the failsafe -output.txt still holds the full log
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream captureStream =
                new PrintStream(new TeeOutputStream(originalOut, captured), true, StandardCharsets.UTF_8);

        context.turnOffAuthorisationSystem();
        System.setOut(captureStream);
        try {
            itemUpdate.processArchive(context, sourceRoot.toString(), null, null, true, false, true);
        } finally {
            captureStream.flush();
            System.setOut(originalOut);
            context.restoreAuthSystemState();
        }

        context.uncacheEntity(item);
        return new Run(captured.toString(StandardCharsets.UTF_8), itemUpdate.embargoSyncFailures);
    }

    /**
     * A run whose failure count is asserted straight away.
     *
     * @param expectedFailures number of embargo problems the run has to count; anything but 0 makes
     *                         {@code ItemUpdate.main()} exit with 1
     * @return everything the run printed
     */
    protected String runItemUpdateExpecting(Item item, String dublinCoreContent, int expectedFailures)
            throws Exception {
        Run run = runItemUpdate(item, dublinCoreContent);
        assertExitCode("itemupdate run", expectedFailures, run);
        return run.console;
    }

    /**
     * @return the number of embargo problems the run reported, which {@link ItemUpdate#exitStatus(int, int)}
     *         turns into the exit code of {@code dspace itemupdate}
     */
    protected int runItemUpdateFailures(Item item, String dublinCoreContent) throws Exception {
        return runItemUpdate(item, dublinCoreContent).embargoSyncFailures;
    }

    /**
     * Asserts the exit code the run would have produced; without it a skipped item looks synchronised.
     */
    protected void assertExitCode(String scenario, int expectedFailures, Run run) {
        assertEquals("[" + scenario + "] wrong number of reported embargo problems, so ItemUpdate.main() would"
                        + " exit with " + ItemUpdate.exitStatus(0, run.embargoSyncFailures) + " instead of "
                        + ItemUpdate.exitStatus(0, expectedFailures) + ". Console output was:"
                        + System.lineSeparator() + run.console,
                expectedFailures, run.embargoSyncFailures);
    }

    /**
     * What a finished {@code itemupdate} run is judged by: its console output and its failure count.
     */
    protected static final class Run {
        final String console;
        final int embargoSyncFailures;

        private Run(String console, int embargoSyncFailures) {
            this.console = console;
            this.embargoSyncFailures = embargoSyncFailures;
        }
    }
}
