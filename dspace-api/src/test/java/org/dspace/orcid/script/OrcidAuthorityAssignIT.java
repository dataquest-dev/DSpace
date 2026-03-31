/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.script;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.Choices;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link OrcidAuthorityAssign}.
 */
public class OrcidAuthorityAssignIT extends AbstractIntegrationTestWithDatabase {

    private Collection publicationCollection;

    private ItemService itemService;
    private MetadataFieldService metadataFieldService;
    private MetadataSchemaService metadataSchemaService;

    @Before
    public void setup() throws Exception {
        itemService = ContentServiceFactory.getInstance().getItemService();
        metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        metadataSchemaService = ContentServiceFactory.getInstance().getMetadataSchemaService();

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
            .withName("Parent community")
            .build();

        publicationCollection = CollectionBuilder.createCollection(context, parentCommunity)
            .withName("Publications")
            .build();

        // Ensure dc.identifier.orcid metadata field exists
        ensureOrcidIdentifierFieldExists();

        context.restoreAuthSystemState();
    }

    /**
     * Test basic scenario: one item with one author having an ORCID.
     * The script should assign authority to that author.
     */
    @Test
    public void testBasicAuthorityAssignment() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Test publication")
            .withAuthor("Muroň, Mikuláš")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        // Reload item to check updated metadata
        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertThat(authors, hasSize(1));
        assertEquals("https://orcid.org/0000-0003-1344-1772", authors.get(0).getAuthority());
        assertEquals(Choices.CF_ACCEPTED, authors.get(0).getConfidence());
    }

    /**
     * Test scenario with multiple authors on one item, only one has an ORCID.
     */
    @Test
    public void testMultipleAuthorsOnlyOneWithOrcid() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Multi-author publication")
            .withAuthor("Muroň, Mikuláš")
            .withAuthor("Novák, Jan")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertThat(authors, hasSize(2));

        // Find each author by value and check authority
        for (MetadataValue mv : authors) {
            if ("Muroň, Mikuláš".equals(mv.getValue())) {
                assertEquals("https://orcid.org/0000-0003-1344-1772", mv.getAuthority());
                assertEquals(Choices.CF_ACCEPTED, mv.getConfidence());
            } else if ("Novák, Jan".equals(mv.getValue())) {
                assertNull(mv.getAuthority());
            }
        }
    }

    /**
     * Test that the same author appearing across multiple items gets authority assigned on all.
     */
    @Test
    public void testSameAuthorAcrossMultipleItems() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item1 = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("First publication")
            .withAuthor("Muroň, Mikuláš")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        Item item2 = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Second publication")
            .withAuthor("Muroň, Mikuláš")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        // Both items should have authority on the same author
        context.turnOffAuthorisationSystem();
        item1 = context.reloadEntity(item1);
        item2 = context.reloadEntity(item2);

        List<MetadataValue> authors1 = itemService.getMetadata(item1, "dc", "contributor", "author", Item.ANY);
        List<MetadataValue> authors2 = itemService.getMetadata(item2, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertEquals("https://orcid.org/0000-0003-1344-1772", authors1.get(0).getAuthority());
        assertEquals("https://orcid.org/0000-0003-1344-1772", authors2.get(0).getAuthority());
    }

    /**
     * Test that existing authority is overwritten (always keep data up-to-date).
     */
    @Test
    public void testOverwritesExistingAuthority() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create item with an author that already has some authority
        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Publication with existing authority")
            .withAuthor("Muroň, Mikuláš", "old-authority-value", Choices.CF_UNCERTAIN)
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        context.restoreAuthSystemState();

        // Verify original authority
        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authorsBefore = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        assertEquals("old-authority-value", authorsBefore.get(0).getAuthority());
        assertEquals(Choices.CF_UNCERTAIN, authorsBefore.get(0).getConfidence());
        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        // Authority should be overwritten with the ORCID
        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authorsAfter = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertEquals("https://orcid.org/0000-0003-1344-1772", authorsAfter.get(0).getAuthority());
        assertEquals(Choices.CF_ACCEPTED, authorsAfter.get(0).getConfidence());
    }

    /**
     * Test that running the script with no dc.identifier.orcid entries does nothing.
     */
    @Test
    public void testNoOrcidEntriesDoesNothing() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("No ORCID publication")
            .withAuthor("Unknown, Author")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getInfoMessages(), hasItem("No author-ORCID mappings found. Nothing to do."));

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertThat(authors.get(0).getAuthority(), is(nullValue()));
    }

    /**
     * Test that an ORCID ID with X checksum digit is handled correctly.
     */
    @Test
    public void testOrcidWithXChecksumDigit() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Checksum X publication")
            .withAuthor("Test, Author")
            .withMetadata("dc", "identifier", "orcid", "Test, Author 0000-0001-2345-678X")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertEquals("https://orcid.org/0000-0001-2345-678X", authors.get(0).getAuthority());
        assertEquals(Choices.CF_ACCEPTED, authors.get(0).getConfidence());
    }

    /**
     * Test case-insensitive matching of author names.
     */
    @Test
    public void testCaseInsensitiveMatching() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Case test publication")
            .withAuthor("muroň, mikuláš")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertEquals("https://orcid.org/0000-0003-1344-1772", authors.get(0).getAuthority());
    }

    /**
     * Test that an author not matching any ORCID entry keeps no authority.
     */
    @Test
    public void testNonMatchingAuthorKeepsNoAuthority() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Non-matching publication")
            .withAuthor("Neznámý, Autor")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertThat(authors, hasSize(1));
        assertNull(authors.get(0).getAuthority());
    }

    /**
     * Test with multiple ORCID entries for different authors.
     */
    @Test
    public void testMultipleOrcidEntries() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = ItemBuilder.createItem(context, publicationCollection)
            .withTitle("Multi-ORCID publication")
            .withAuthor("Muroň, Mikuláš")
            .withAuthor("Novák, Jan")
            .withMetadata("dc", "identifier", "orcid", "Muroň, Mikuláš 0000-0003-1344-1772")
            .withMetadata("dc", "identifier", "orcid", "Novák, Jan 0000-0001-2222-3333")
            .build();

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = runScript();

        assertThat(handler.getErrorMessages(), empty());

        context.turnOffAuthorisationSystem();
        item = context.reloadEntity(item);
        List<MetadataValue> authors = itemService.getMetadata(item, "dc", "contributor", "author", Item.ANY);
        context.restoreAuthSystemState();

        assertThat(authors, hasSize(2));

        for (MetadataValue mv : authors) {
            if ("Muroň, Mikuláš".equals(mv.getValue())) {
                assertEquals("https://orcid.org/0000-0003-1344-1772", mv.getAuthority());
                assertEquals(Choices.CF_ACCEPTED, mv.getConfidence());
            } else if ("Novák, Jan".equals(mv.getValue())) {
                assertEquals("https://orcid.org/0000-0001-2222-3333", mv.getAuthority());
                assertEquals(Choices.CF_ACCEPTED, mv.getConfidence());
            }
        }
    }

    /**
     * Ensure the dc.identifier.orcid metadata field exists in the database.
     * This field is Mendelu-specific and may not exist in the default test registry.
     */
    private void ensureOrcidIdentifierFieldExists() throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField field = metadataFieldService.findByElement(context, "dc", "identifier", "orcid");
        if (field == null) {
            metadataFieldService.create(context, dcSchema, "identifier", "orcid",
                    "ORCID identifier of an author in format: AuthorName ORCID-ID");
        }
    }

    private TestDSpaceRunnableHandler runScript() throws Exception {
        String[] args = new String[] { "orcid-authority-assign" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);
        return handler;
    }
}
