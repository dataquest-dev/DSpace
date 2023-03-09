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
    @Autowired(required = true)
    protected WorkspaceItemDAO workspaceItemDAO;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;
    @Autowired(required = true)
    protected CollectionService collectionService;
    @Autowired(required = true)
    protected ItemService itemService;
    @Autowired(required = true)
    protected WorkflowService workflowService;

    @Override
    public WorkspaceItem create(Context context, Collection collection, boolean multipleTitles, boolean publishedBefore,
                                boolean multipleFiles, Integer stageReached, Integer pageReached,
                                boolean template, UUID uuid) throws AuthorizeException, SQLException {
        // Check the user has permission to ADD to the collection
        authorizeService.authorizeAction(context, collection, Constants.ADD);

        WorkspaceItem workspaceItem = workspaceItemDAO.create(context, new WorkspaceItem());
        workspaceItem.setCollection(collection);


        // Create an item
        Item item;
        if (uuid != null) {
            item = itemService.create(context, workspaceItem, uuid);
        } else {
            item = itemService.create(context, workspaceItem);
        }
        item.setSubmitter(context.getCurrentUser());

        // Now create the policies for the submitter to modify item and contents
        // contents = bitstreams, bundles
        // read permission
        authorizeService.addPolicy(context, item, Constants.READ, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);
        // write permission
        authorizeService.addPolicy(context, item, Constants.WRITE, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);
        // add permission
        authorizeService.addPolicy(context, item, Constants.ADD, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);
        // remove contents permission
        authorizeService
                .addPolicy(context, item, Constants.REMOVE, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);
        // delete permission
        authorizeService
                .addPolicy(context, item, Constants.DELETE, item.getSubmitter(), ResourcePolicy.TYPE_SUBMISSION);

        // Copy template if appropriate
        Item templateItem = collection.getTemplateItem();

        Optional<MetadataValue> colEntityType = getDSpaceEntityType(collection);
        Optional<MetadataValue> templateItemEntityType = getDSpaceEntityType(templateItem);

        if (colEntityType.isPresent() && templateItemEntityType.isPresent() &&
                !StringUtils.equals(colEntityType.get().getValue(), templateItemEntityType.get().getValue())) {
            throw new IllegalStateException("The template item has entity type : (" +
                    templateItemEntityType.get().getValue() + ") different than collection entity type : " +
                    colEntityType.get().getValue());
        }

        if (colEntityType.isPresent() && templateItemEntityType.isEmpty()) {
            MetadataValue original = colEntityType.get();
            MetadataField metadataField = original.getMetadataField();
            MetadataSchema metadataSchema = metadataField.getMetadataSchema();
            // NOTE: dspace.entity.type = <blank> does not make sense
            //       the collection entity type is by default blank when a collection is first created
            if (StringUtils.isNotBlank(original.getValue())) {
                itemService.addMetadata(context, item, metadataSchema.getName(), metadataField.getElement(),
                        metadataField.getQualifier(), original.getLanguage(), original.getValue());
            }
        }

        if (template && (templateItem != null)) {
            List<MetadataValue> md = itemService.getMetadata(templateItem, Item.ANY, Item.ANY, Item.ANY, Item.ANY);

            for (MetadataValue aMd : md) {
                MetadataField metadataField = aMd.getMetadataField();
                MetadataSchema metadataSchema = metadataField.getMetadataSchema();
                itemService.addMetadata(context, item, metadataSchema.getName(), metadataField.getElement(),
                        metadataField.getQualifier(), aMd.getLanguage(),
                        aMd.getValue());
            }
        }

        itemService.update(context, item);
        workspaceItem.setItem(item);

        log.info(LogHelper.getHeader(context, "create_workspace_item",
                "workspace_item_id=" + workspaceItem.getID()
                        + "item_id=" + item.getID() + "collection_id="
                        + collection.getID()));

        context.addEvent(new Event(Event.MODIFY, Constants.ITEM, item.getID(), null,
                itemService.getIdentifiers(context, item)));

        workspaceItem.setPublishedBefore(publishedBefore);
        workspaceItem.setMultipleFiles(multipleFiles);
        workspaceItem.setMultipleTitles(multipleFiles);
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
