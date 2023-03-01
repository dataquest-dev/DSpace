package org.dspace.content.service;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.WorkspaceItemServiceImpl;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.UUID;

public class ClarinWorkspaceItemServiceImpl implements ClarinWorkspaceItemService {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinWorkspaceItemServiceImpl.class);
    @Autowired
    private WorkspaceItemService workspaceItemService;


    @Override
    public WorkspaceItem create(Context context, Collection collection, boolean multipleTitles, boolean publishedBefore,
                                boolean multipleFiles, Integer stageReached, Integer pageReached,
                                boolean template) throws AuthorizeException, SQLException {
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, template);
        workspaceItem.setPublishedBefore(publishedBefore);
        workspaceItem.setMultipleFiles(multipleFiles);
        workspaceItem.setMultipleTitles(multipleFiles);
        workspaceItem.setPageReached(pageReached);
        workspaceItem.setStageReached(stageReached);
        return workspaceItem;
    }
}
