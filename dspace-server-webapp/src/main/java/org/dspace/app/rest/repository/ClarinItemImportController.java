/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;
import static org.dspace.core.Constants.COLLECTION;

import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.WorkspaceItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.content.service.clarin.ClarinWorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.service.EPersonService;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.identifier.IdentifierException;
import org.dspace.identifier.service.IdentifierService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.workflow.WorkflowException;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Specialized controller created for Clarin-Dspace import item, workspace item and workflow item.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
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
    private HandleClarinService handleClarinService;
    @Autowired
    private HandleService handleService;
    @Autowired
    private IdentifierService identifierService;
    @Autowired
    XmlWorkflowService workflowService;
    @Autowired(required = true)
    protected AuthorizeService authorizeService;
    @Autowired
    private EPersonService ePersonService;
    @Autowired
    InstallItemService installItemService;
    @Autowired
    ConfigurationService configurationService;

    /**
     * Endpoint for import workspace item.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/workspaceitem
     * }
     * </pre>
     * @param request request
     * @return workspaceitem converted to rest
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/workspaceitem")
    public WorkspaceItemRest importWorkspaceItem(HttpServletRequest request)
            throws AuthorizeException, SQLException, IdentifierException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }

        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            ServletInputStream input = request.getInputStream();
            itemRest = mapper.readValue(input, ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        //get item attribute values
        String owningCollectionUuidString = request.getParameter("owningCollection");
        String multipleTitlesString = request.getParameter("multipleTitles");
        String publishedBeforeString = request.getParameter("publishedBefore");
        String multipleFilesString = request.getParameter("multipleFiles");
        String stageReachedString = request.getParameter("stageReached");
        String pageReachedString = request.getParameter("pageReached");

        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (Objects.isNull(collection)) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                    + owningCollectionUuid);
        }

        //convert input values to correct formats
        boolean multipleTitles = getBooleanFromString(multipleTitlesString);
        boolean publishedBefore = getBooleanFromString(publishedBeforeString);
        boolean multipleFiles = getBooleanFromString(multipleFilesString);
        Integer stageReached = getIntegerFromString(stageReachedString);
        Integer pageReached = getIntegerFromString(pageReachedString);

        //the submitter of created workspace item is the current user
        //required submitter is different from the current user, so we need to save current user and set it for
        //the time to create workspace item to required submitter
        EPerson currUser = context.getCurrentUser();
        String epersonUUIDString = request.getParameter("epersonUUID");
        UUID epersonUUID = UUIDUtils.fromString(epersonUUIDString);
        EPerson eperson = ePersonService.find(context, epersonUUID);
        context.setCurrentUser(eperson);
        //we have to turn off authorization system, because in service there are authorization controls
        context.turnOffAuthorisationSystem();
        WorkspaceItem workspaceItem = clarinWorkspaceItemService.create(context, collection, multipleTitles,
                publishedBefore, multipleFiles, stageReached, pageReached,false);
        context.restoreAuthSystemState();
        //set current user back to saved current user
        context.setCurrentUser(currUser);

        Item item = workspaceItem.getItem();
        //the method set withdraw to true and isArchived to false
        if (itemRest.getWithdrawn()) {
            //withdraw is working with eperson, not with the current user
            context.setCurrentUser(eperson);
            context.turnOffAuthorisationSystem();
            itemService.withdraw(context, item);
            context.restoreAuthSystemState();
            context.setCurrentUser(currUser);
        }
        //set item attributes to input values
        item.setArchived(itemRest.getInArchive());
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());
        metadataConverter.setMetadata(context, item, itemRest.getMetadata());
        if (!Objects.isNull(itemRest.getHandle())) {
            //create handle
            identifierService.register(context, item, itemRest.getHandle());
        }

        // save changes
        workspaceItemService.update(context, workspaceItem);
        itemService.update(context, item);
        WorkspaceItemRest workspaceItemRest = converter.toRest(workspaceItem, utils.obtainProjection());
        context.complete();

        return workspaceItemRest;
    }

    /**
     * Get item rest based on workspace item id.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/26453b4d-e513-44e8-8d5b-395f62972eff/item
     * }
     * </pre>
     * @param id      workspace item id
     * @param request request
     * @return item of workspace item converted to rest
     * @throws SQLException if database error
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.GET, path = "/{id}/item")
    public ItemRest getWorkspaceitemItem(@PathVariable int id, HttpServletRequest request) throws SQLException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }
        //find workspace item based on id
        WorkspaceItem workspaceItem = workspaceItemService.find(context, id);
        //return item of found workspace item
        return converter.toRest(workspaceItem.getItem(), utils.obtainProjection());
    }

    /**
     * Endpoint for import workflow item.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/workflowitem
     * }
     * </pre>
     * @param request request
     * @return response entity
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     * @throws WorkflowException
     * @throws IOException
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/workflowitem")
    public ResponseEntity importWorkflowItem(HttpServletRequest request) throws SQLException, AuthorizeException,
            WorkflowException, IOException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }

        //workflow item is created from workspace item, so workspace item must be created before
        //id of workspace item
        String workspaceIdString = request.getParameter("id");
        WorkspaceItem wsi = workspaceItemService.find(context, Integer.parseInt(workspaceIdString));
        //create workflow item from workspace item
        XmlWorkflowItem wf = workflowService.start(context, wsi);
        context.commit();
        HttpHeaders headers = new HttpHeaders();
        headers.add("workflowitem_id", wf.getID().toString());
        return new ResponseEntity<>("Import workflowitem was successful", headers, HttpStatus.OK);
    }

    /**
     * Endpoint for import item.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/item
     * }
     * </pre>
     * @param request request
     * @return created item converted to rest
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/item")
    public ItemRest importItem(HttpServletRequest request) throws SQLException, AuthorizeException, IOException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }

        //each item has owning collection
        String owningCollectionUuidString = request.getParameter("owningCollection");
        ObjectMapper mapper = new ObjectMapper();
        ItemRest itemRest = null;
        try {
            ServletInputStream input = request.getInputStream();
            itemRest = mapper.readValue(input, ItemRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        UUID owningCollectionUuid = UUIDUtils.fromString(owningCollectionUuidString);
        //find owning collection of item
        Collection collection = collectionService.find(context, owningCollectionUuid);
        if (collection == null) {
            throw new DSpaceBadRequestException("The given owningCollection parameter is invalid: "
                    + owningCollectionUuid);
        }

        //if we want to create item, we have to firstly create workspace item
        //submitter if workspace item is different from current user
        EPerson currUser = context.getCurrentUser();
        String epersonUUIDString = request.getParameter("epersonUUID");
        UUID epersonUUID = UUIDUtils.fromString(epersonUUIDString);
        EPerson eperson = ePersonService.find(context, epersonUUID);
        context.setCurrentUser(eperson);
        context.turnOffAuthorisationSystem();
        WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, false);
        // created item
        Item item = workspaceItem.getItem();
        // if the created item has pre-registered PID and the importing Item has handle which must be imported, the
        // pre-registered PID must be unbound from the created Item, otherwise the Item will have two handles.
        if (DSpaceServicesFactory.getInstance().getConfigurationService()
                .getBooleanProperty("identifiers.submission.register", false) &&
                StringUtils.isNotEmpty(itemRest.getHandle())) {
            handleService.unbindHandle(context, item);
        }

        context.restoreAuthSystemState();
        context.setCurrentUser(currUser);

        item.setOwningCollection(collection);
        //the method set withdraw to true and isArchived to false
        if (itemRest.getWithdrawn()) {
            //withdraw is working with eperson, not with the current user
            context.setCurrentUser(eperson);
            context.turnOffAuthorisationSystem();
            itemService.withdraw(context, item);
            context.restoreAuthSystemState();
            context.setCurrentUser(currUser);
        }
        item.setDiscoverable(itemRest.getDiscoverable());
        item.setLastModified(itemRest.getLastModified());
        metadataConverter.setMetadata(context, item, itemRest.getMetadata());
        // store metadata values which should not be updated by the import e.g., `dc.description.provenance`,
        // `dc.date.available`, etc..
        // Load these metadata fields from the `clarin-dspace.cfg`
        List<String> notUpdateMetadataNames = Arrays.asList(configurationService
                .getArrayProperty("import.metadata.field.not.update"));

        // Store metadata values into ArrayList
        List<MetadataValue> notUpdateThisMetadata = new ArrayList<>();
        for (String metadataField : notUpdateMetadataNames) {
            notUpdateThisMetadata.addAll(itemService.getMetadataByMetadataString(item, metadataField));
        }

        //remove workspaceitem and create collection2item
        Item itemToReturn = installItemService.installItem(context, workspaceItem, itemRest.getHandle());
        //set isArchived back to false
        itemToReturn.setArchived(itemRest.getInArchive());

        // Clear updated metadata which shouldn't be updated and add the correct ones
        for (String metadataField : notUpdateMetadataNames) {
            itemService.removeMetadataValues(context, item, itemService
                    .getMetadataByMetadataString(item, metadataField));
        }

        // Add stored metadata values into item
        for (MetadataValue metadataValue : notUpdateThisMetadata) {
            String schemaName = metadataValue.getMetadataField().getMetadataSchema().getName();
            String element = metadataValue.getMetadataField().getElement();
            String qualifier = metadataValue.getMetadataField().getQualifier();
            String lang = metadataValue.getLanguage();
            String value = metadataValue.getValue();
            itemService.addMetadata(context, item, schemaName, element, qualifier, lang, value);
        }

        itemService.update(context, itemToReturn);
        itemRest = converter.toRest(itemToReturn, utils.obtainProjection());
        context.complete();
        return itemRest;
    }

    /**
     * Endpoint for importing item's mapped collection.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/item/{ITEM_UUID}/mappedCollections
     * }
     * </pre>
     * @param request request
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method = RequestMethod.POST, value = "/item" + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID +
            "/mappedCollections")
    public void importItemCollections(@PathVariable UUID uuid, HttpServletRequest request) throws SQLException,
            AuthorizeException {
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null - cannot import item's mapped collections.");
        }

        // Load List of collection self links
        List<String> requestAsStringList = new ArrayList<>();
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(new InputStreamReader(request.getInputStream()));

            if (!(obj instanceof JSONArray)) {
                throw new UnprocessableEntityException("The request is not a JSON Array");
            }

            for (Object entity : (JSONArray) obj) {
                String collectionSelfLink = entity.toString();
                requestAsStringList.add(collectionSelfLink);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot import item's mapped collections because parsing of the request JSON" +
                    "throws this error: " + e.getMessage());
        }

        // Find Collections following its self link
        List<DSpaceObject> listDsoFoundInRequest
                = utils.constructDSpaceObjectList(context, requestAsStringList);

        if (CollectionUtils.isEmpty(listDsoFoundInRequest)) {
            throw new UnprocessableEntityException("Not a valid collection uuid.");
        }

        for (DSpaceObject dso : listDsoFoundInRequest) {

            Item item = itemService.find(context, uuid);
            if (dso != null && dso.getType() == COLLECTION && item != null) {
                if (this.checkIfItemIsTemplate(item)) {
                    continue;
                }

                Collection collectionToMapTo = (Collection) dso;
                if (this.checkIfOwningCollection(item, collectionToMapTo.getID())) {
                    continue;
                }

                collectionService.addItem(context, collectionToMapTo, item);
                collectionService.update(context, collectionToMapTo);
                itemService.update(context, item);
            } else {
                throw new UnprocessableEntityException("Not a valid collection or item uuid.");
            }
        }

        context.commit();
    }

    /**
     * Convert String input value to boolean.
     * @param value input value
     * @return converted input value to boolean
     */
    private boolean getBooleanFromString(String value) {
        boolean output = false;
        if (StringUtils.isNotBlank(value)) {
            output = Boolean.parseBoolean(value);
        }
        return output;
    }

    /**
     * Convert String input value to Integer.
     * @param value input value
     * @return converted input value to Integer
     */
    private Integer getIntegerFromString(String value) {
        Integer output = -1;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        }
        return output;
    }

    private boolean checkIfItemIsTemplate(Item item) {
        return item.getTemplateItemOf() != null;
    }

    private boolean checkIfOwningCollection(Item item, UUID collectionID) {
        if (Objects.isNull(item)) {
            return false;
        }
        if (Objects.isNull(item.getOwningCollection())) {
            return false;
        }
        return item.getOwningCollection().getID().equals(collectionID);
    }
}
