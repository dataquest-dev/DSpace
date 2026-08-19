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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Covers embargo synchronisation in {@link ItemUpdate} when the same archive is re-imported with a
 * {@code dc.date.embargoend} that has already passed: the ORIGINAL bitstreams have to stay readable
 * for anonymous users instead of losing their last {@code READ} policy.
 */
public class EmbargoPastDateIT extends AbstractIntegrationTestWithDatabase {

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private final ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private final MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    private final MetadataFieldService metadataFieldService =
            ContentServiceFactory.getInstance().getMetadataFieldService();

    private final StringBuilder diagnostics = new StringBuilder();

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

        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("embargoPastDateIT");
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
     * Verifies that a future embargo followed by an expired one leaves the ORIGINAL bitstream publicly
     * readable.
     */
    @Test
    public void pastEmbargoEndMustKeepFilesPublic() throws Exception {
        String futureEmbargoEnd = LocalDate.now().plusYears(1).toString();
        String pastEmbargoEnd = LocalDate.now().minusMonths(1).toString();

        List<Group> defaultBitstreamReadGroups =
                authorizeService.getAuthorizedGroups(context, collection, Constants.DEFAULT_BITSTREAM_READ);
        assertTrue("fixture precondition: collection must grant DEFAULT_BITSTREAM_READ to Anonymous",
                defaultBitstreamReadGroups.contains(anonymousGroup));

        Item item = createItem("VSB-TUO thesis");
        Bitstream bitstream = createOriginalBitstream(item, "thesis.pdf");

        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFalse("fixture precondition: imported bitstream must carry an Anonymous READ policy",
                anonymousReadPolicies(bitstream).isEmpty());
        assertTrue("fixture precondition: imported bitstream must be publicly readable",
                anonymousCanRead(bitstream));

        // first run: embargo end date in the future
        runItemUpdate(item, dublinCore(item, "embargoedAccess", futureEmbargoEnd));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + futureEmbargoEnd, bitstream);

        assertEquals("itemupdate did not store the future embargo end date",
                futureEmbargoEnd, singleMetadataValue(item, "date", "embargoend"));
        List<ResourcePolicy> embargoed = anonymousReadPolicies(bitstream);
        assertEquals("future embargo must leave exactly one Anonymous READ policy", 1, embargoed.size());
        assertNotNull("the surviving Anonymous READ policy must be dated", embargoed.get(0).getStartDate());
        assertFalse("while embargoed the file must not be publicly readable", anonymousCanRead(bitstream));

        // second run: embargo end date in the past, item declared openAccess
        runItemUpdate(item, dublinCore(item, "openAccess", pastEmbargoEnd));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C - after itemupdate with PAST dc.date.embargoend=" + pastEmbargoEnd
                + " and dc.rights.access=openAccess", bitstream);

        List<ResourcePolicy> afterExpiry = anonymousReadPolicies(bitstream);
        assertFalse("Expired embargo wiped every Anonymous READ policy from the ORIGINAL bitstream."
                        + " The file is now unreachable (HTTP 401) although dc.rights.access=openAccess."
                        + diagnostics,
                afterExpiry.isEmpty());
        assertTrue("Expired embargo left the ORIGINAL bitstream unreadable for anonymous users."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream.
     */
    private boolean anonymousCanRead(Bitstream bs) throws Exception {
        EPerson saved = context.getCurrentUser();
        int popped = 0;
        while (context.ignoreAuthorization()) {
            context.restoreAuthSystemState();
            popped++;
        }
        context.setCurrentUser(null);
        try {
            return authorizeService.authorizeActionBoolean(context, bs, Constants.READ);
        } finally {
            context.setCurrentUser(saved);
            for (int i = 0; i < popped; i++) {
                context.turnOffAuthorisationSystem();
            }
        }
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws Exception {
        return resourcePolicyService.find(context, bitstream, Constants.READ).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    private void dump(String label, Bitstream bitstream) throws Exception {
        List<String> lines = new ArrayList<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream, Constants.READ)) {
            lines.add(String.format("      id=%s group=%s action=%s rpType=%s rpName=%s start=%s end=%s valid=%s",
                    policy.getID(),
                    policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                    Constants.actionText[policy.getAction()],
                    policy.getRpType(),
                    policy.getRpName(),
                    policy.getStartDate(),
                    policy.getEndDate(),
                    resourcePolicyService.isDateValid(policy)));
        }
        if (lines.isEmpty()) {
            lines.add("      <NO READ POLICIES AT ALL>");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator())
          .append("  === ").append(label).append(" ===").append(System.lineSeparator())
          .append("      bitstream=").append(bitstream.getID()).append(System.lineSeparator())
          .append("      anonymousCanRead=").append(anonymousCanRead(bitstream)).append(System.lineSeparator());
        for (String line : lines) {
            sb.append(line).append(System.lineSeparator());
        }
        diagnostics.append(sb);
        System.out.print(sb);
    }

    private void ensureMetadataFieldExists(String element, String qualifier) throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField existingField = metadataFieldService.findByElement(context, dcSchema, element, qualifier);
        if (existingField == null) {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, element, qualifier, null).build();
        }
    }

    private Item createItem(String title) throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = ItemBuilder.createItem(context, collection)
                .withTitle(title)
                .build();
        context.restoreAuthSystemState();
        return item;
    }

    private Bitstream createOriginalBitstream(Item item, String name) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)))
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    private String singleMetadataValue(Item item, String element, String qualifier) {
        List<MetadataValue> values = itemService.getMetadata(item, "dc", element, qualifier, Item.ANY);
        return values.isEmpty() ? null : values.get(0).getValue();
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation.
     */
    private void runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
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

        context.uncacheEntity(item);

        // embargoSyncFailures drives the exit code of ItemUpdate.main(), so a refusal that left it at
        // zero would be invisible to the calling script.
        assertEquals("itemupdate reported an embargo synchronisation problem, so ItemUpdate.main() would exit"
                        + " with " + ItemUpdate.exitStatus(0, itemUpdate.embargoSyncFailures),
                0, itemUpdate.embargoSyncFailures);
    }

    private String dublinCore(Item item, String rightsAccess, String embargoEndDate) {
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
                    .append(embargoEndDate)
                    .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }
}
