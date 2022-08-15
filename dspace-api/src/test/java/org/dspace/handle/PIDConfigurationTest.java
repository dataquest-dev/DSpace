/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;

import org.dspace.AbstractUnitTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.junit.Before;
import org.junit.Test;

public class PIDConfigurationTest extends AbstractUnitTest {
    private static final String AUTHOR = "Test author name";

    private Collection col;
    private Community com;
    private Community subCom;
    private Item publicItem;

    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();
    private WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();

    @Before
    public void setup() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        // 1. A community-collection structure with one parent community and one collection
        com = communityService.create(null, context);
        communityService.createSubcommunity(context, com);
        subCom = com.getSubcommunities().get(0);
        col = collectionService.create(context, subCom);
        WorkspaceItem workspaceItem = workspaceItemService.create(context, col, true);
        // 2. Create item and add it to the collection
        publicItem = installItemService.installItem(context, workspaceItem);
        context.restoreAuthSystemState();
    }

    static final String PREFIX_DELIMITER = "/";

    static final String SUBPREFIX_DELIMITER = "-";

    @Test
    public void newHandleIdentifier() {
        String handle = publicItem.getHandle();
        PIDCommunityConfiguration pidCommunityConfiguration = PIDConfiguration
                .getPIDCommunityConfiguration(publicItem.getID());
        String handleId = null;
        if (pidCommunityConfiguration.isEpic()) {
            return;
        } else if (pidCommunityConfiguration.isLocal()) {
            StringBuffer handleIdBuff = new StringBuffer();
            String handlePrefix = pidCommunityConfiguration.getPrefix();
            handleIdBuff.append(handlePrefix);

            if (!handlePrefix.endsWith(PREFIX_DELIMITER)) {
                handleIdBuff.append(PREFIX_DELIMITER);
            }
            StringBuffer suffix = new StringBuffer();
            String handleSubprefix = pidCommunityConfiguration.getSubprefix();
            if (handleSubprefix != null && !handleSubprefix.isEmpty()) {
                suffix.append(handleSubprefix + SUBPREFIX_DELIMITER);
            }
            suffix.append(publicItem.getHandles().get(0).getID());
            String handleSuffix = suffix.toString();
            handleIdBuff.append(handleSuffix);
            handleId = handleIdBuff.toString();
        } else {
            throw new IllegalStateException("Unsupported PID type: "
                    + pidCommunityConfiguration.getType());
        }

        assertEquals("123456789/1-" + publicItem.getHandles().get(0).getID(), handleId);
    }
}
