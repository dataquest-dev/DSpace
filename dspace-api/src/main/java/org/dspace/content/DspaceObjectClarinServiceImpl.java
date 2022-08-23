/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.sql.SQLException;
import java.util.List;

import org.dspace.content.service.DspaceObjectClarinService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;

/**
 * Additional service implementation for the DspaceObject in Clarin-DSpace.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
public class DspaceObjectClarinServiceImpl<T extends DSpaceObject> implements DspaceObjectClarinService<T> {
    private WorkspaceItemService workspaceItemService;
    @Override
    public Community getPrincipalCommunity(Context context, DSpaceObject dso) throws SQLException {
        Community principalCommunity = null;
        int type = dso.getType();
        if (type == Constants.COMMUNITY) {
            principalCommunity = (Community) dso;
        } else {
            Collection collection = null;
            if (type == Constants.COLLECTION) {
                collection = (Collection) dso;
            } else if (type == Constants.ITEM) {
                collection = ((Item) dso).getOwningCollection();
                if (collection == null) {
                    WorkspaceItem wi = workspaceItemService.findByItem(context, (Item)dso);
                    if (wi != null) {
                        collection = wi.getCollection();
                    }
                }
            }

            if (collection != null) {
                List<Community> communities = collection.getCommunities();
                if (communities.size() > 0) {
                    principalCommunity = communities.get(0);
                }
            }
        }
        return principalCommunity;
    }
}
