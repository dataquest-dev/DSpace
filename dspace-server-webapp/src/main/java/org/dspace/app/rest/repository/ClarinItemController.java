package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.GroupRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ClarinWorkspaceItemService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.services.RequestService;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * This will be the entry point for the api/clarin/core/items endpoint with additional paths to it
 */
@RestController
@RequestMapping("/api/clarin/" + ItemRest.CATEGORY + "/" + ItemRest.NAME)
public class ClarinItemController {

    private RequestService requestService = new DSpace().getRequestService();

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ClarinWorkspaceItemService workspaceItemService;

    @Autowired
    private MetadataConverter metadataConverter;

    @Autowired
    private InstallItemService installItemService;

    @Autowired
    private ConverterService converter;

    @Autowired
    private Utils utils;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = POST)
    public ItemRest createAndReturn(Context context) throws AuthorizeException, SQLException {
        HttpServletRequest req = requestService.getCurrentRequest().getHttpServletRequest();
        String owningCollectionUuidString = req.getParameter("owningCollection");
        String multipleTitlesString = req.getParameter("multipleTitles");
        String publishedBeforeString = req.getParameter("publishedBefore");
        String multipleFilesString = req.getParameter("multipleFiles");
        String stageReachedString = req.getParameter("stageReached");
        String pageReachedString = req.getParameter("pageReached");
        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            ServletInputStream input = req.getInputStream();
            itemRest = mapper.readValue(input, ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (collection == null) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                    + owningCollectionUuid);
        }
        
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, false);
        Item item = workspaceItem.getItem();
        item.setArchived(true);
        item.setOwningCollection(collection);
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());
        metadataConverter.setMetadata(context, item, itemRest.getMetadata());

        Item itemToReturn = installItemService.installItem(context, workspaceItem);

        return converter.toRest(itemToReturn, utils.obtainProjection());
    }

}
