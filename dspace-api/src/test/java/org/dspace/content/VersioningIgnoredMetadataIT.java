/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.VersionBuilder;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.versioning.Version;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for verifying that certain metadata fields are properly excluded
 * when creating a new item version via the versioning service.
 * <p>
 * The {@code ignoredMetadataFields} configured in {@code versioning-service.xml} should
 * prevent the following fields from being copied to a new version:
 * <ul>
 *   <li>{@code dc.date.accessioned} — system-assigned accession date, must be unique per version</li>
 *   <li>{@code dc.date.available} — system-assigned availability date, must be unique per version</li>
 *   <li>{@code dc.description.provenance} — provenance log, should start fresh for each version</li>
 *   <li>{@code dc.identifier.uri} — Handle URI, each version gets its own identifier</li>
 *   <li>{@code dc.identifier.doi} — DOI, each version should receive a new DOI</li>
 * </ul>
 *
 * @author dataquest-dev
 */
public class VersioningIgnoredMetadataIT extends AbstractIntegrationTestWithDatabase {

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private final WorkspaceItemService workspaceItemService =
        ContentServiceFactory.getInstance().getWorkspaceItemService();
    private final InstallItemService installItemService =
        ContentServiceFactory.getInstance().getInstallItemService();

    protected Community community;
    protected Collection collection;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        community = CommunityBuilder.createCommunity(context)
            .withName("Test Community")
            .build();

        collection = CollectionBuilder.createCollection(context, community)
            .withName("Test Collection")
            .build();

        context.restoreAuthSystemState();
    }

    /**
     * Test that {@code dc.date.available} is NOT copied to the new version.
     * <p>
     * When a new version is created, the availability date should be reset
     * so the new version receives its own date upon installation.
     */
    @Test
    public void testDateAvailableNotCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Test Item")
            .withMetadata("dc", "date", "available", "2024-01-15")
            .build();

        // Verify original has dc.date.available
        String originalDateAvailable = itemService.getMetadataFirstValue(
            originalItem, "dc", "date", "available", Item.ANY);
        assertNotNull("Original item should have dc.date.available", originalDateAvailable);

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // The new version (still a workspace item) should NOT have dc.date.available copied
        String newDateAvailable = itemService.getMetadataFirstValue(
            newItem, "dc", "date", "available", Item.ANY);
        assertNull("dc.date.available should NOT be copied to new version", newDateAvailable);

        context.restoreAuthSystemState();
    }

    /**
     * Test that {@code dc.identifier.uri} is NOT copied to the new version.
     * <p>
     * Each item version should receive its own Handle URI upon installation.
     * Copying the old URI would cause identifier conflicts.
     */
    @Test
    public void testIdentifierUriNotCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Test Item with URI")
            .build();

        // After installation, original should have a handle-based dc.identifier.uri
        String originalUri = itemService.getMetadataFirstValue(
            originalItem, "dc", "identifier", "uri", Item.ANY);
        assertNotNull("Original item should have dc.identifier.uri", originalUri);

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // The new version should NOT have the original's dc.identifier.uri
        String newUri = itemService.getMetadataFirstValue(
            newItem, "dc", "identifier", "uri", Item.ANY);
        // The new item should either have no URI or a different one (reserved by identifier service)
        if (newUri != null) {
            // If some URI was set (e.g. by identifier reservation), it must differ from the original
            assertNotNull("If new item has a URI, it should be newly assigned", newUri);
            // The URI should not be the same as the original item's URI
            assertEquals("New version URI should not match original URI", false,
                originalUri.equals(newUri));
        }
        // If null, that's correct — the URI was properly ignored and will be assigned on install

        context.restoreAuthSystemState();
    }

    /**
     * Test that {@code dc.identifier.doi} is NOT copied to the new version.
     * <p>
     * Each version of an item should get its own DOI. Copying the old DOI
     * to a new version would lead to DOI conflicts and violate DOI uniqueness.
     */
    @Test
    public void testIdentifierDoiNotCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Test Item with DOI")
            .withDoiIdentifier("10.5555/test-doi-12345")
            .build();

        // Verify original has dc.identifier.doi
        String originalDoi = itemService.getMetadataFirstValue(
            originalItem, "dc", "identifier", "doi", Item.ANY);
        assertNotNull("Original item should have dc.identifier.doi", originalDoi);
        assertEquals("10.5555/test-doi-12345", originalDoi);

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // The new version should NOT have dc.identifier.doi copied
        String newDoi = itemService.getMetadataFirstValue(
            newItem, "dc", "identifier", "doi", Item.ANY);
        assertNull("dc.identifier.doi should NOT be copied to new version", newDoi);

        context.restoreAuthSystemState();
    }

    /**
     * Test that {@code dc.date.accessioned} is NOT copied to the new version.
     * <p>
     * The accession date marks when an item was first archived. Each version
     * should get its own accession date upon installation.
     */
    @Test
    public void testDateAccessionedNotCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Test Item Accessioned")
            .build();

        // After installation, original should have dc.date.accessioned
        String originalAccessioned = itemService.getMetadataFirstValue(
            originalItem, "dc", "date", "accessioned", Item.ANY);
        assertNotNull("Original item should have dc.date.accessioned", originalAccessioned);

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // The new version should NOT have dc.date.accessioned copied
        String newAccessioned = itemService.getMetadataFirstValue(
            newItem, "dc", "date", "accessioned", Item.ANY);
        assertNull("dc.date.accessioned should NOT be copied to new version", newAccessioned);

        context.restoreAuthSystemState();
    }

    /**
     * Test that {@code dc.description.provenance} is NOT copied to the new version.
     * <p>
     * Provenance records are version-specific audit trails and should not carry
     * over to a new version.
     */
    @Test
    public void testDescriptionProvenanceNotCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Test Item Provenance")
            .withProvenanceData("Submitted by admin on 2024-01-01")
            .build();

        // Verify original has dc.description.provenance
        String originalProvenance = itemService.getMetadataFirstValue(
            originalItem, "dc", "description", "provenance", Item.ANY);
        assertNotNull("Original item should have dc.description.provenance", originalProvenance);

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // The new version should NOT have dc.description.provenance copied
        String newProvenance = itemService.getMetadataFirstValue(
            newItem, "dc", "description", "provenance", Item.ANY);
        assertNull("dc.description.provenance should NOT be copied to new version", newProvenance);

        context.restoreAuthSystemState();
    }

    /**
     * Test that regular metadata (e.g. dc.title, dc.subject) IS still copied to the new version.
     * <p>
     * This ensures the ignored fields configuration doesn't accidentally prevent
     * all metadata from being copied.
     */
    @Test
    public void testRegularMetadataIsCopiedToNewVersion() throws Exception {
        context.turnOffAuthorisationSystem();

        Item originalItem = ItemBuilder.createItem(context, collection)
            .withTitle("Important Research Paper")
            .withAuthor("Smith, John")
            .withSubject("Computer Science")
            .build();

        // Create a new version
        Version newVersion = VersionBuilder.createVersion(context, originalItem, "test version").build();
        Item newItem = newVersion.getItem();

        // Regular metadata should be copied
        String newTitle = itemService.getMetadataFirstValue(
            newItem, "dc", "title", null, Item.ANY);
        assertEquals("dc.title should be copied to new version",
            "Important Research Paper", newTitle);

        String newAuthor = itemService.getMetadataFirstValue(
            newItem, "dc", "contributor", "author", Item.ANY);
        assertEquals("dc.contributor.author should be copied to new version",
            "Smith, John", newAuthor);

        String newSubject = itemService.getMetadataFirstValue(
            newItem, "dc", "subject", null, Item.ANY);
        assertEquals("dc.subject should be copied to new version",
            "Computer Science", newSubject);

        context.restoreAuthSystemState();
    }
}
