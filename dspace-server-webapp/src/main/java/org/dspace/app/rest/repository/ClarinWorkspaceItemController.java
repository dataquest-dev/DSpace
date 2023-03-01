package org.dspace.app.rest.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.ClarinWorkspaceItemService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.services.RequestService;
import org.dspace.util.UUIDUtils;
import org.dspace.utils.DSpace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * This will be the entry point for the api/clarin/core/items endpoint with additional paths to it
 */
@RestController
@RequestMapping("/api/clarin/" + WorkspaceItemRest.CATEGORY + "/" + WorkspaceItemRest.NAME)
public class ClarinWorkspaceItemController {

    private RequestService requestService = new DSpace().getRequestService();

    @Autowired
    private CollectionService collectionService;

    @Autowired
    private ClarinWorkspaceItemService clarinWorkspaceItemService;

    @Autowired
    private MetadataConverter metadataConverter;

    @Autowired
    private InstallItemService installItemService;

    @Autowired
    private ConverterService converter;

    @Autowired
    private ItemService itemService;

    @Autowired
    private Utils utils;

    @Autowired
    private HandleClarinService handleService;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = POST)
    public WorkspaceItemRest createAndReturn(@RequestBody(required = false) JsonNode itemRest, HttpServletRequest request,
                                    HttpServletResponse response) throws AuthorizeException, SQLException {

        if (Objects.isNull(itemRest)) {
            throw new BadRequestException("For creating the new workspaceitem has to be itemrest included in json body!");
        }

        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }


        HttpServletRequest req = requestService.getCurrentRequest().getHttpServletRequest();
        String owningCollectionUuidString = req.getParameter("owningCollection");

        ObjectMapper mapper = new ObjectMapper();
        try {
            ItemRest multipleTitlesString = mapper.readValue(itemRest.toString(), ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }


        WorkspaceItemRest workspaceItemRest = null;
        try {
            ServletInputStream input = req.getInputStream();
            workspaceItemRest = mapper.readValue(input, WorkspaceItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }



        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (collection == null) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                    + owningCollectionUuid);
        }

        WorkspaceItem workspaceItem = clarinWorkspaceItemService.create(context, collection, workspaceItemRest..getMultipleTitles,
                publishedBefore, multipleFiles, stageReached, pageReached,false);
        Item item = workspaceItem.getItem();
        //the method set withdraw to true and isArchived to false
        if (itemRest.getWithdrawn()) {
            itemService.withdraw(context, item);
        }
        item.setArchived(itemRest.getInArchive());
        item.setOwningCollection(collection);
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());
        metadataConverter.setMetadata(context, item, itemRest.getMetadata());
        //maybe we don't need to do with handle nothing
        item.addHandle(handleService.findByHandle(context, itemRest.getHandle()));
        Item itemToReturn = installItemService.installItem(context, workspaceItem);

        return converter.toRest(itemToReturn, utils.obtainProjection());
    }

    private boolean getBooleanFromString(String value) {
        boolean output = false;
        if (StringUtils.isNotBlank(value)) {
            output = Boolean.parseBoolean(value);
        } else {
            throw new IllegalArgumentException("The value converted to boolean cannot be blank.");
        }
        return output;
    }

    private Integer getIntegerFromString(String value) {
        Integer output = null;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        } else {
            throw new IllegalArgumentException("The value converted to Integer cannot be blank.");
        }
        return output;
    }
}
