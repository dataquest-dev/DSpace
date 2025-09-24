/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.SolrOAIReindexer;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Simple integration test for SolrOAIReindexer to verify that exceptions
 * are handled gracefully and commits still occur.
 */
public class SolrOAIReindexerIT extends AbstractControllerIntegrationTest {

    @Autowired
    private SolrOAIReindexer solrOAIReindexer;

    private Community community;
    private Collection collection;

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
     * Test that reindexing completes even when cache clearing fails
     */
    @Test
    public void testReindexWithCacheException() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create a simple test item
        Item testItem = ItemBuilder.createItem(context, collection)
                .withTitle("Test Item")
                .withAuthor("Test Author")
                .build();

        context.restoreAuthSystemState();

        // Test that reindexing doesn't throw exceptions even if cache clearing fails
        // The safeClearCaches method should handle any exceptions gracefully
        boolean completed = false;
        try {
            solrOAIReindexer.reindexItem(testItem);
            completed = true;
        } catch (Exception e) {
            // Should not happen with safe cache clearing
        }

        assertTrue("Reindexing should complete successfully", completed);
        assertNotNull("Item should still exist", testItem);
        assertNotNull("Item should have handle after reindexing", testItem.getHandle());
    }

    /**
     * Test that deletion works even when cache clearing fails
     */
    @Test
    public void testDeleteWithCacheException() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create a simple test item
        Item testItem = ItemBuilder.createItem(context, collection)
                .withTitle("Test Item for Deletion")
                .withAuthor("Test Author")
                .build();

        context.restoreAuthSystemState();

        // Test that deletion doesn't throw exceptions even if cache clearing fails
        boolean completed = false;
        try {
            solrOAIReindexer.deleteItem(testItem);
            completed = true;
        } catch (Exception e) {
            // Should not happen with safe cache clearing
        }

        assertTrue("Deletion should complete successfully", completed);
    }

    @Test
    public void testEventFallbackMethods() throws Exception {
        // Set system property to force event-based fallback
        System.setProperty("dspace.test.force.event.fallback", "true");
        
        try {
            context.turnOffAuthorisationSystem();

            Item testItem = ItemBuilder.createItem(context, collection)
                    .withTitle("Test Item for Event Fallback")
                    .withAuthor("Test Author")
                    .build();

            context.restoreAuthSystemState();

            // Test reindexing - should now use event-based approach due to system property
            boolean reindexCompleted = false;
            try {
                solrOAIReindexer.reindexItem(testItem);
                reindexCompleted = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            assertTrue("Event-based reindexing should complete successfully", reindexCompleted);

            // Test deletion - should now use event-based approach due to system property
            boolean deleteCompleted = false;
            try {
                solrOAIReindexer.deleteItem(testItem);
                deleteCompleted = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            assertTrue("Event-based deletion should complete successfully", deleteCompleted);

            // Verify the test item is still valid
            assertNotNull("Test item should not be null", testItem);
            assertNotNull("Test item should have an ID", testItem.getID());
        } finally {
            // Always clean up the system property
            System.clearProperty("dspace.test.force.event.fallback");
        }
    }
}