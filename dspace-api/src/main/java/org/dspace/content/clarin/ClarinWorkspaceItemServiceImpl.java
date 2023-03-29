package org.dspace.content.clarin;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.util.DCInputsReaderException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.dao.WorkspaceItemDAO;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.content.service.clarin.ClarinWorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.event.Event;
import org.dspace.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ClarinWorkspaceItemServiceImpl implements ClarinWorkspaceItemService {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinWorkspaceItemServiceImpl.class);
    @Autowired
    private WorkspaceItemService workspaceItemService;

    @Autowired
    private WorkspaceItemDAO workspaceItemDAO;

    @Override
    public WorkspaceItem create(Context context, Collection collection, boolean multipleTitles, boolean publishedBefore,
                                boolean multipleFiles, Integer stageReached, Integer pageReached,
                                boolean template) throws AuthorizeException, SQLException {

        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, false);
        workspaceItem.setPublishedBefore(publishedBefore);
        workspaceItem.setMultipleFiles(multipleFiles);
        workspaceItem.setMultipleTitles(multipleTitles);
        workspaceItem.setPageReached(pageReached);
        workspaceItem.setStageReached(stageReached);
        return workspaceItem;
    }

    private Optional<MetadataValue> getDSpaceEntityType(DSpaceObject dSpaceObject) {
        return Objects.nonNull(dSpaceObject) ? dSpaceObject.getMetadata()
                .stream()
                .filter(x -> x.getMetadataField().toString('.')
                        .equalsIgnoreCase("dspace.entity.type"))
                .findFirst()
                : Optional.empty();
    }


    @Override
    public WorkspaceItem find(Context context, UUID uuid) throws SQLException {
        WorkspaceItem workspaceItem = workspaceItemDAO.findByID(context, WorkspaceItem.class, uuid);

        if (workspaceItem == null) {
            if (log.isDebugEnabled()) {
                log.debug(LogHelper.getHeader(context, "find_workspace_item",
                        "not_found,workspace_item_uuid=" + uuid));
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug(LogHelper.getHeader(context, "find_workspace_item",
                        "workspace_item_uuid=" + uuid));
            }
        }
        return workspaceItem;
    }
}