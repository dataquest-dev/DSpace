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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.file.PathUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
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

    private static final String STANDARD_EMBARGO = "Standard Embargo";
    private static final String SPECIAL_CASE_EMBARGO = "Special Case Embargo";

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
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
    public void syncEmbargoPoliciesCreatesStandardEmbargoAndRemovesImmediateAnonymousRead() throws Exception {
        String futureDate = LocalDate.now().plusDays(14).toString();
        Item item = createItem("Standard Embargo Item",
                               "rights", "access", "embargoedAccess",
                               "date", "embargoend", futureDate);
        Bitstream bitstream = createBitstream(item, "standard.txt");

        createAnonymousReadPolicy(bitstream, null, "Immediate Read");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);

        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, bitstream, Constants.READ);

        boolean hasImmediateAnonymousRead = readPolicies.stream()
                .anyMatch(policy -> isAnonymousPolicy(policy) && policy.getStartDate() == null);
        assertFalse(hasImmediateAnonymousRead);

        ResourcePolicy embargoPolicy = readPolicies.stream()
                .filter(policy -> isAnonymousPolicy(policy)
                        && STANDARD_EMBARGO.equals(policy.getRpName())
                        && policy.getStartDate() != null)
                .findFirst()
                .orElse(null);

        assertNotNull(embargoPolicy);
    }

    @Test
    public void syncEmbargoPoliciesCreatesSpecialCasePolicyWithoutAccessRightMetadata() throws Exception {
        String futureDate = LocalDate.now().plusDays(21).toString();
        Item item = createItem("Special Case Embargo Item", "date", "embargoend", futureDate);
        Bitstream bitstream = createBitstream(item, "special.txt");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);

        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, bitstream, Constants.READ);

        ResourcePolicy embargoPolicy = readPolicies.stream()
                .filter(policy -> isAnonymousPolicy(policy)
                        && SPECIAL_CASE_EMBARGO.equals(policy.getRpName())
                        && policy.getStartDate() != null)
                .findFirst()
                .orElse(null);

        assertNotNull(embargoPolicy);
    }

    @Test
    public void syncEmbargoPoliciesClearsSafEmbargoPoliciesWhenEmbargoDateInvalid() throws Exception {
        Item item = createItem("Invalid Date Item", "date", "embargoend", "");
        Bitstream bitstream = createBitstream(item, "invalid.txt");
        createAnonymousReadPolicy(bitstream, new Date(System.currentTimeMillis() + 86_400_000L), STANDARD_EMBARGO);

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);

        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, bitstream, Constants.READ);

        boolean hasSafEmbargoPolicy = readPolicies.stream()
                .anyMatch(policy -> isAnonymousPolicy(policy)
                        && (STANDARD_EMBARGO.equals(policy.getRpName())
                        || SPECIAL_CASE_EMBARGO.equals(policy.getRpName())));

        assertFalse(hasSafEmbargoPolicy);
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
        createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);

        runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, "embargoedAccess", newEmbargoDate));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> embargoDates = itemService.getMetadata(reloadedItem, "dc", "date", "embargoend", Item.ANY);
        assertEquals(1, embargoDates.size());
        assertEquals(newEmbargoDate, embargoDates.get(0).getValue());

        LocalDate expectedPolicyDate = LocalDate.parse(newEmbargoDate).plusDays(1);
        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, reloadedBitstream, Constants.READ);

        boolean hasOldEmbargoPolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && STANDARD_EMBARGO.equals(policy.getRpName())
                && policy.getStartDate() != null
                && toLocalDate(policy.getStartDate()).equals(oldPolicyDate));
        assertFalse(hasOldEmbargoPolicy);

        boolean hasNewEmbargoPolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && STANDARD_EMBARGO.equals(policy.getRpName())
                && policy.getStartDate() != null
                && toLocalDate(policy.getStartDate()).equals(expectedPolicyDate));
        assertTrue(hasNewEmbargoPolicy);
    }

    @Test
    public void processArchiveUpdateWithBlankEmbargoDateClearsSafEmbargoPolicies() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(12).toString();

        Item item = createItem("Blank Embargo Date Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "blank-embargo-date.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);

        runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, "embargoedAccess", ""));

        Bitstream reloadedBitstream = context.reloadEntity(bitstream);
        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, reloadedBitstream, Constants.READ);

        boolean hasSafEmbargoPolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && (STANDARD_EMBARGO.equals(policy.getRpName())
                || SPECIAL_CASE_EMBARGO.equals(policy.getRpName())));
        assertFalse(hasSafEmbargoPolicy);
    }

    @Test
    public void processArchiveUpdateRemovingEmbargoMetadataClearsPoliciesAndMetadata() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(10).toString();

        Item item = createItem("Remove Embargo Metadata Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "remove-embargo.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);

        runEmbargoMetadataUpdate(item, dublinCore(item));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> rightsAccess = itemService.getMetadata(reloadedItem, "dc", "rights", "access", Item.ANY);
        List<MetadataValue> embargoDates = itemService.getMetadata(reloadedItem, "dc", "date", "embargoend", Item.ANY);
        assertTrue(rightsAccess.isEmpty());
        assertTrue(embargoDates.isEmpty());

        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, reloadedBitstream, Constants.READ);
        boolean hasSafEmbargoPolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && (STANDARD_EMBARGO.equals(policy.getRpName())
                || SPECIAL_CASE_EMBARGO.equals(policy.getRpName())));
        assertFalse(hasSafEmbargoPolicy);
    }

    @Test
    public void processArchiveUpdateWithEmbargoDateAndNoRightsCreatesSpecialCasePolicy() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(8).toString();
        String newEmbargoDate = LocalDate.now().plusDays(25).toString();

        Item item = createItem("Special Case Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createBitstream(item, "special-case-update.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        createAnonymousReadPolicy(bitstream, oldPolicyStart, STANDARD_EMBARGO);

        runEmbargoMetadataUpdate(item, dublinCoreWithEmbargo(item, null, newEmbargoDate));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> rightsAccess = itemService.getMetadata(reloadedItem, "dc", "rights", "access", Item.ANY);
        assertTrue(rightsAccess.isEmpty());

        LocalDate expectedPolicyDate = LocalDate.parse(newEmbargoDate).plusDays(1);
        List<ResourcePolicy> readPolicies = resourcePolicyService.find(context, reloadedBitstream, Constants.READ);

        boolean hasSpecialCasePolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && SPECIAL_CASE_EMBARGO.equals(policy.getRpName())
                && policy.getStartDate() != null
                && toLocalDate(policy.getStartDate()).equals(expectedPolicyDate));
        assertTrue(hasSpecialCasePolicy);

        boolean hasStandardPolicy = readPolicies.stream().anyMatch(policy -> isAnonymousPolicy(policy)
                && STANDARD_EMBARGO.equals(policy.getRpName()));
        assertFalse(hasStandardPolicy);
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

    private void createAnonymousReadPolicy(Bitstream bitstream, Date startDate, String name) throws Exception {
        context.turnOffAuthorisationSystem();
        ResourcePolicyBuilder builder = ResourcePolicyBuilder.createResourcePolicy(context, null, anonymousGroup)
                .withAction(Constants.READ)
                .withDspaceObject(bitstream)
                .withName(name);

        if (startDate != null) {
            builder.withStartDate(startDate);
        }
        builder.build();
        context.restoreAuthSystemState();
    }

    private boolean isAnonymousPolicy(ResourcePolicy policy) {
        return policy.getGroup() != null && policy.getGroup().equals(anonymousGroup);
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

    private void runEmbargoMetadataUpdate(Item item, String dublinCoreContent) throws Exception {
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