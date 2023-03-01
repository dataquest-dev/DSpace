package org.dspace.content.service;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.UUID;

public interface ClarinWorkspaceItemService {

    public WorkspaceItem create(Context context, Collection collection,
                                boolean multipleTitles, boolean publishedBefore,
                                boolean multipleFiles, Integer stageReached,
                                Integer pageReached, boolean template)
            throws AuthorizeException, SQLException;
}
