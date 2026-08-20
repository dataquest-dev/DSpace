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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.junit.Test;

/**
 * Integration tests for {@link ItemUpdate} and {@link ItemArchive}.
 */
public class ItemUpdateIT extends AbstractEmbargoIT {

    /**
     * Verifies the step that turns {@code embargoSyncFailures} into the process exit code, which is otherwise
     * only reachable through {@code main()} and its {@code System.exit}.
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
        Bitstream bitstream = createOriginalBitstream(item, "standard.txt");

        addAnonymousReadPolicy(bitstream, null, "Immediate Read");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);
        assertEquals("setting a future embargo is not a failure", 0, itemUpdate.embargoSyncFailures);

        // A second, undated policy would defeat the embargo, so the count is part of the assertion.
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
        Bitstream bitstream = createOriginalBitstream(item, "special.txt");

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);
        assertEquals("setting a future embargo is not a failure", 0, itemUpdate.embargoSyncFailures);

        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(bitstream);
        assertEquals(1, anonymousRead.size());

        // Both cases share one access condition name, which also fits the 30 character
        // resourcepolicy.rpname column.
        ResourcePolicy embargoPolicy = anonymousRead.get(0);
        assertEquals(EMBARGO_POLICY_NAME, embargoPolicy.getRpName());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, embargoPolicy.getRpType());
        assertNotNull(embargoPolicy.getStartDate());
        assertEquals(LocalDate.parse(futureDate).plusDays(1), toLocalDate(embargoPolicy.getStartDate()));
        assertFalse(anonymousCanRead(bitstream));
    }

    /**
     * Verifies that a blank {@code dc.date.embargoend} leaves every resource policy untouched and fails the
     * run; it is a broken export, not an instruction to change anything.
     */
    @Test
    public void syncEmbargoPoliciesLeavesPoliciesUntouchedWhenEmbargoDateInvalid() throws Exception {
        Item item = createItem("Invalid Date Item", "date", "embargoend", "");
        Bitstream bitstream = createOriginalBitstream(item, "invalid.txt");
        ResourcePolicy legacyPolicy = replaceAnonymousReadPolicies(bitstream,
                new Date(System.currentTimeMillis() + 86_400_000L), LEGACY_EMBARGO_POLICY_NAME);
        bitstream = context.reloadEntity(bitstream);

        Set<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable, otherwise the"
                        + " 'nothing changed' assertions below say nothing about a leak",
                anonymousCanRead(bitstream));

        ItemUpdate itemUpdate = new ItemUpdate();
        itemUpdate.syncEmbargoPolicies(context, item);

        // A blank end date is broken input, so the run has to exit non-zero.
        assertEquals("a blank dc.date.embargoend has to fail the run", 1, itemUpdate.embargoSyncFailures);
        assertEquals(1, ItemUpdate.exitStatus(0, itemUpdate.embargoSyncFailures));
        assertEquals(idsBefore, policyIds(bitstream));
        assertFalse("an unparseable dc.date.embargoend published an embargoed file",
                anonymousCanRead(bitstream));

        // The dated policy the run could not validate is still there, unchanged, under its legacy name.
        ResourcePolicy reloadedLegacy = resourcePolicyService.find(context, legacyPolicy.getID());
        assertNotNull(reloadedLegacy);
        assertEquals(LEGACY_EMBARGO_POLICY_NAME, reloadedLegacy.getRpName());
    }

    @Test
    public void processArchiveUpdatesEmbargoMetadataAndResyncsEmbargoPolicy() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(5).toString();
        String newEmbargoDate = LocalDate.now().plusDays(35).toString();

        Item item = createItem("Embargo Update Item",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createOriginalBitstream(item, "update-embargo.txt");

        LocalDate oldPolicyDate = LocalDate.parse(oldEmbargoDate).plusDays(1);
        Date oldPolicyStart = Date.from(oldPolicyDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy = addAnonymousReadPolicy(bitstream, oldPolicyStart, LEGACY_EMBARGO_POLICY_NAME);
        Integer legacyPolicyId = legacyPolicy.getID();

        assertEquals("re-dating an embargo is not a failure", 0,
                runItemUpdateFailures(item, dublinCore(item, "embargoedAccess", newEmbargoDate)));

        Item reloadedItem = context.reloadEntity(item);
        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        List<MetadataValue> embargoDates = itemService.getMetadata(reloadedItem, "dc", "date", "embargoend", Item.ANY);
        assertEquals(1, embargoDates.size());
        assertEquals(newEmbargoDate, embargoDates.get(0).getValue());

        LocalDate expectedPolicyDate = LocalDate.parse(newEmbargoDate).plusDays(1);
        List<ResourcePolicy> anonymousRead = anonymousReadPolicies(reloadedBitstream);
        assertEquals(1, anonymousRead.size());

        // The pre-existing policy is re-dated in place, so the file is covered by a policy at every moment.
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
     * Same as {@link #syncEmbargoPoliciesLeavesPoliciesUntouchedWhenEmbargoDateInvalid()} driven through a SAF
     * archive whose {@code dc.date.embargoend} is blank.
     */
    @Test
    public void processArchiveUpdateWithBlankEmbargoDateLeavesPoliciesUntouched() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(12).toString();

        Item item = createItem("Blank Embargo Date Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createOriginalBitstream(item, "blank-embargo-date.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy =
                replaceAnonymousReadPolicies(bitstream, oldPolicyStart, LEGACY_EMBARGO_POLICY_NAME);
        bitstream = context.reloadEntity(bitstream);

        Set<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable, otherwise the"
                        + " 'nothing changed' assertions below say nothing about a leak",
                anonymousCanRead(bitstream));

        assertEquals("a blank dc.date.embargoend has to fail the run", 1,
                runItemUpdateFailures(item, dublinCore(item, "embargoedAccess", "")));

        Bitstream reloadedBitstream = context.reloadEntity(bitstream);

        assertEquals(idsBefore, policyIds(reloadedBitstream));
        assertFalse("a blank dc.date.embargoend published an embargoed file",
                anonymousCanRead(reloadedBitstream));

        ResourcePolicy reloadedLegacy = resourcePolicyService.find(context, legacyPolicy.getID());
        assertNotNull(reloadedLegacy);
        assertEquals(LEGACY_EMBARGO_POLICY_NAME, reloadedLegacy.getRpName());
    }

    /**
     * Verifies that a SAF package without {@code dc.date.embargoend} leaves every policy untouched. The field
     * carries no instruction about the embargo; a file is opened by an end date that lies in the past.
     */
    @Test
    public void processArchiveUpdateRemovingEmbargoMetadataLeavesPoliciesUntouched() throws Exception {
        String oldEmbargoDate = LocalDate.now().plusDays(10).toString();

        Item item = createItem("Remove Embargo Metadata Update",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", oldEmbargoDate);
        Bitstream bitstream = createOriginalBitstream(item, "remove-embargo.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        // The undated Anonymous READ policy from the collection default has to go, otherwise the file is
        // readable throughout and the assertions below prove nothing.
        ResourcePolicy legacyPolicy =
                replaceAnonymousReadPolicies(bitstream, oldPolicyStart, LEGACY_EMBARGO_POLICY_NAME);
        Integer legacyPolicyId = legacyPolicy.getID();
        bitstream = context.reloadEntity(bitstream);

        Set<Integer> idsBefore = policyIds(bitstream);
        assertFalse("fixture precondition: the embargoed file must not be publicly readable",
                anonymousCanRead(bitstream));

        int failures = runItemUpdateFailures(item, dublinCore(item));

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
        assertEquals(LEGACY_EMBARGO_POLICY_NAME, untouchedPolicy.getRpName());
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
        Bitstream bitstream = createOriginalBitstream(item, "special-case-update.txt");

        Date oldPolicyStart = Date.from(LocalDate.parse(oldEmbargoDate).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        ResourcePolicy legacyPolicy = addAnonymousReadPolicy(bitstream, oldPolicyStart, LEGACY_EMBARGO_POLICY_NAME);
        Integer legacyPolicyId = legacyPolicy.getID();

        assertEquals("an embargo end date without dc.rights.access is not a failure", 0,
                runItemUpdateFailures(item, dublinCore(item, null, newEmbargoDate)));

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

    private String dublinCore(Item item) {
        String identifierUri = ItemUpdate.HANDLE_PREFIX + item.getHandle();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<dublin_core schema=\"dc\">\n"
                + "    <dcvalue element=\"identifier\" qualifier=\"uri\">" + identifierUri + "</dcvalue>\n"
                + "</dublin_core>";
    }

}