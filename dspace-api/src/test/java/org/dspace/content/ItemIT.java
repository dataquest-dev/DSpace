/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.apache.logging.log4j.Logger;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.junit.Test;

/**
 * Integration Tests for class Item
 *
 * @author milanmajchrak
 */
public class ItemIT extends AbstractIntegrationTestWithDatabase {

    /**
     * log4j category
     */
    protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ItemTest.class);

    /**
     * Item instance for the tests
     */
    protected Item it;

    protected ItemService itemService = ContentServiceFactory.getInstance()
            .getItemService();
    protected CommunityService communityService = ContentServiceFactory.getInstance()
            .getCommunityService();
    protected CollectionService collectionService = ContentServiceFactory.getInstance()
            .getCollectionService();
    protected WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance()
            .getWorkspaceItemService();
    protected InstallItemService installItemService = ContentServiceFactory.getInstance()
            .getInstallItemService();


    protected Collection collection;
    protected Community owningCommunity;

    /**
     * Spy of AuthorizeService to use for tests
     * (initialized / setup in @Before method)
     */
    private AuthorizeService authorizeServiceSpy;

    /**
     * Test of update item and find method.
     */
    @Test
    public void dtqExampleTest() throws Exception {
        // create item
        //we have to create a new community in the database
        context.turnOffAuthorisationSystem();
        this.owningCommunity = communityService.create(null, context);
        this.collection = collectionService.create(context, owningCommunity);
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, true);
        this.it = installItemService.installItem(context, workspaceItem);
        context.restoreAuthSystemState();

        // Find by id
        // Get ID of item created in init()
        UUID id = it.getID();
        // Make sure we can find it via its ID
        Item found = itemService.find(context, id);
        assertThat("dtqExampleTest 0", found.getID(), equalTo(id));

        // default discoverable should be true
        assertThat("dtqExampleTest 1", found.isDiscoverable(), equalTo(true));

        context.turnOffAuthorisationSystem();
        // set discoverable and update
        found.setDiscoverable(false);
        itemService.update(context, found);
        context.restoreAuthSystemState();


        // find by id
        Item foundAfterUpdate = itemService.find(context, id);
        assertThat("dtqExampleTest 2", foundAfterUpdate.getID(), equalTo(id));

        // check if discoverable changed
        assertThat("dtqExampleTest 1", foundAfterUpdate.isDiscoverable(), equalTo(false));
    }
}
