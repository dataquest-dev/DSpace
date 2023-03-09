package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RESTAuthorizationException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.WorkflowItemRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.submit.SubmissionService;
import org.dspace.app.rest.utils.SolrOAIReindexer;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.content.service.clarin.ClarinWorkspaceItemService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.discovery.IndexingService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.services.ConfigurationService;
import org.dspace.util.UUIDUtils;
import org.dspace.workflow.WorkflowException;
import org.dspace.workflow.WorkflowItem;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

/**
 * This will be the entry point for the api/clarin/core/items endpoint with additional paths to it
 */
@RestController
@RequestMapping("/api/clarin/import")
public class ClarinItemImportController {

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ClarinWorkspaceItemService clarinWorkspaceItemService;

    @Autowired
    private WorkspaceItemService workspaceItemService;
    @Autowired
    private MetadataConverter metadataConverter;

    @Autowired
    private ConverterService converter;

    @Autowired
    private ItemService itemService;

    @Autowired
    private Utils utils;

    @Autowired
    private HandleClarinService handleService;

    @Autowired
    XmlWorkflowService workflowService;

    @Autowired
    SubmissionService submissionService;

    @Autowired
    private SolrOAIReindexer solrOAIReindexer;
    @Autowired
    private ConfigurationService configurationService;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;

    @Autowired
    private EPersonService ePersonService;
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/workspaceitem")
    public WorkspaceItemRest importWorkspaceItemAndItem(HttpServletRequest request)
            throws AuthorizeException, SQLException {

        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }

        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            ServletInputStream input = request.getInputStream();
            itemRest = mapper.readValue(input, ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        String owningCollectionUuidString = request.getParameter("owningCollection");
        String multipleTitlesString = request.getParameter("multipleTitles");
        String publishedBeforeString = request.getParameter("publishedBefore");
        String multipleFilesString = request.getParameter("multipleFiles");
        String stageReachedString = request.getParameter("stageReached");
        String pageReachedString = request.getParameter("pageReached");

        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (collection == null) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                    + owningCollectionUuid);
        }
        boolean multipleTitles = getBooleanFromString(multipleTitlesString);
        boolean publishedBefore = getBooleanFromString(publishedBeforeString);
        boolean multipleFiles = getBooleanFromString(multipleFilesString);
        Integer stageReached = getIntegerFromString(stageReachedString);
        Integer pageReached = getIntegerFromString(pageReachedString);

        EPerson currUser = context.getCurrentUser();
        String epersonUUIDString = request.getParameter("epersonUUID");
        UUID epersonUUID = UUIDUtils.fromString(epersonUUIDString);
        EPerson eperson = ePersonService.find(context, epersonUUID);
        context.setCurrentUser(eperson);

        WorkspaceItem workspaceItem = clarinWorkspaceItemService.create(context, collection, multipleTitles,
                publishedBefore, multipleFiles, stageReached, pageReached,false);

        context.setCurrentUser(currUser);
        Item item = workspaceItem.getItem();
        //the method set withdraw to true and isArchived to false
        if (itemRest.getWithdrawn()) {
            itemService.withdraw(context, item);
        }
        //maybe update item in database...
        item.setArchived(itemRest.getInArchive());
        item.setOwningCollection(collection);
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());

        metadataConverter.setMetadata(context, item, itemRest.getMetadata());
        //maybe we don't need to do with handle nothing
        if (!Objects.isNull(itemRest.getHandle())) {
            item.addHandle(handleService.findByHandle(context, itemRest.getHandle()));
        }
        // save changes
        workspaceItemService.update(context, workspaceItem);
        itemService.update(context, item);
        WorkspaceItemRest workspaceItemRest = converter.toRest(workspaceItem, utils.obtainProjection());
        context.complete();

        return workspaceItemRest;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}/item")
    public ItemRest getWorkspaceitemItem(@PathVariable int id, HttpServletRequest request) throws SQLException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }
        WorkspaceItem workspaceItem = workspaceItemService.find(context, id);
        return converter.toRest(workspaceItem.getItem(), utils.obtainProjection());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/workflowitem")
    public WorkflowItemRest importWorkflowItem(HttpServletRequest request) throws SQLException, AuthorizeException, WorkflowException, IOException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }

        String workspaceIdString = request.getParameter("id");
        WorkspaceItem wsi = workspaceItemService.find(context, Integer.parseInt(workspaceIdString));
        XmlWorkflowItem wf = workflowService.start(context, wsi);
        return converter.toRest(wf, utils.obtainProjection());
    }

    private boolean getBooleanFromString(String value) {
        boolean output = false;
        if (StringUtils.isNotBlank(value)) {
            output = Boolean.parseBoolean(value);
        }
        return output;
    }

    private Integer getIntegerFromString(String value) {
        Integer output = -1;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        }
        return output;
    }
}
