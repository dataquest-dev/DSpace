/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.HandleRest;
import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.Patch;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

/**
 * This is the repository responsible to manage Handle Rest object.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
@Component(HandleRest.CATEGORY + "." + HandleRest.NAME)
public class HandleRestRepository extends  DSpaceRestRepository<HandleRest, Integer> {

    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(HandleRestRepository.class);
    @Autowired
    HandleClarinService handleClarinService;

    @Autowired
    ItemService itemService;

    @Override
    @PreAuthorize("permitAll()")
    public HandleRest findOne(Context context, Integer id) {
        Handle handle = null;
        try {
            handle = handleClarinService.findByID(context, id);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (handle == null) {
            return null;
        }
        return converter.toRest(handle, utils.obtainProjection());
    }

    @Override
    public Page<HandleRest> findAll(Context context, Pageable pageable) {
        try {
            List<Handle> handles = handleClarinService.findAll(context);
            return converter.toRestPage(handles, pageable, utils.obtainProjection());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void delete(Context context, Integer id) throws AuthorizeException {
        try {
            Handle handle = handleClarinService.findByID(context, id);
            handleClarinService.delete(context, handle);
        } catch (SQLException e) {
            throw new RuntimeException("error while trying to delete " + HandleRest.NAME + " with id: " + id, e);
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    protected HandleRest createAndReturn(Context context) throws AuthorizeException {
        HandleRest handleRest;

        try {
            handleRest = new ObjectMapper().readValue(
                    getRequestService().getCurrentRequest().getHttpServletRequest().getInputStream(),
                    HandleRest.class
            );
        } catch (IOException excIO) {
            throw new DSpaceBadRequestException("error parsing request body", excIO);
        }

        Handle handle = null;
        try {
            handle = handleClarinService.createHandle(context, handleRest.getResourceTypeID(), handleRest.getUrl());
            handleClarinService.update(context, handle);
        } catch (SQLException e) {
            throw new RuntimeException
            ("error while trying to create new Handle and update it", e);
        }

        if (ObjectUtils.isEmpty(handle)) {
            throw new RuntimeException("Handle is empty");
        }

        return converter.toRest(handle, utils.obtainProjection());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    protected void patch(Context context, HttpServletRequest request, String apiCategory, String model, Integer id,
                         Patch patch) throws AuthorizeException {
        try {
            for (Operation operation : patch.getOperations()) {
                if (operation.getOp() == "replace") {
                    switch (operation.getPath()) {
                        case "/replaceHandle":
                            //replace handle in handle object by new handle
                            Handle handleObject = handleClarinService.findByID(context, id);

                            if (operation.getValue() != null && handleObject != null) {
                                JsonNode jsonNodeUrl = null;
                                JsonNode jsonNodeHandle = null;
                                JsonNode jsonNodeArchive = null;
                                JsonValueEvaluator jsonValEvaluator = (JsonValueEvaluator) operation.getValue();
                                JsonNode jsonNodes = jsonValEvaluator.getValueNode();

                                if (jsonNodes.get("handle") != null) {
                                    jsonNodeHandle = jsonNodes.get("handle");
                                }
                                if (jsonNodes.get("url") != null) {
                                    jsonNodeUrl = jsonNodes.get("url");
                                }
                                if (jsonNodes.get("archive") != null) {
                                    jsonNodeArchive = jsonNodes.get("archive");
                                }
                                if (ObjectUtils.isEmpty(jsonNodeHandle.asText()) ||
                                        StringUtils.isBlank(jsonNodeHandle.asText()) ||
                                        ObjectUtils.isEmpty(jsonNodeUrl.asText()) ||
                                        StringUtils.isBlank(jsonNodeUrl.asText()) ||
                                        ObjectUtils.isEmpty(jsonNodeArchive.asText()) ||
                                        StringUtils.isBlank(jsonNodeArchive.asText())) {
                                    throw new UnprocessableEntityException
                                    ("Cannot load JsonNode value from the operation: " + operation.getPath());
                                }

                                updateHandle(context, handleObject, jsonNodeHandle.asText(), jsonNodeUrl.asText(),
                                        jsonNodeArchive.asBoolean());
                            }
                            break;

                        case "/setPrefix":
                            if (operation.getValue() != null) {
                                //set handle prefix
                                JsonNode jsonNodeNewPrefix = null;
                                JsonNode jsonNodeOldPrefix = null;
                                JsonNode jsonNodeArchive = null;
                                JsonValueEvaluator jsonValEvaluator = (JsonValueEvaluator) operation.getValue();
                                JsonNode jsonNodes = jsonValEvaluator.getValueNode();

                                if (jsonNodes.get("newPrefix") != null) {
                                    jsonNodeNewPrefix = jsonNodes.get("newPrefix");
                                }
                                if (jsonNodes.get("oldPrefix") != null) {
                                    jsonNodeOldPrefix = jsonNodes.get("oldPrefix");
                                }
                                if (jsonNodes.get("archive") != null) {
                                    jsonNodeArchive = jsonNodes.get("archive");
                                }

                                if (ObjectUtils.isEmpty(jsonNodeNewPrefix) ||
                                        StringUtils.isBlank(jsonNodeNewPrefix.asText()) ||
                                        ObjectUtils.isEmpty(jsonNodeOldPrefix) ||
                                        StringUtils.isBlank(jsonNodeOldPrefix.asText()) ||
                                        ObjectUtils.isEmpty(jsonNodeArchive) ||
                                        StringUtils.isBlank(jsonNodeArchive.asText())) {
                                    throw new UnprocessableEntityException
                                    ("Cannot load JsonNode value from the operation: " + operation.getPath());
                                }

                                //changing prefix in existing handles with old prefix
                                //archiving if it is required
                                if(!jsonNodeOldPrefix.asText().equals( jsonNodeNewPrefix.asText())) {
                                    this.changePrefixInExistingHandles(context, jsonNodeOldPrefix.asText(),
                                            jsonNodeNewPrefix.asText(), jsonNodeArchive.asBoolean());
                                    // get the value from the old operation as a string
                                    handleClarinService.setPrefix(context, jsonNodeNewPrefix.asText(),
                                            jsonNodeOldPrefix.asText());
                                }
                            }
                            break;

                        default:
                            throw new UnprocessableEntityException("Provided operation:"
                                    + operation.getOp() + " is not supported");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("error while trying to patch handle");
        }
    }

    @Override
    public Class<HandleRest> getDomainClass() {
        return HandleRest.class;
    }

    private void updateHandle(Context context, Handle handleObject, String newHandle, String url, boolean archive)
            throws AuthorizeException {
        //archive handle
        Item item = null;
        String oldHandle = handleObject.getHandle();
        try {
            if (handleClarinService.isInternalResource(handleObject)) {
                // Try resolving handle to Item
                try {
                    DSpaceObject dso = handleObject.getDSpaceObject();
                    if (dso != null && dso.getType() == Constants.ITEM) {
                        item = (Item) dso;
                    }
                } catch (IllegalStateException e) {
                    item = null;
                }

                // Update Item's metadata
                if (null != item) {

                    // Handle resolved to Item
                    if (archive) {
                        // Archive metadata
                        List<MetadataValue> dcUri = itemService.getMetadata(item, "dc", "identifier", "uri", Item.ANY);
                        List<String> values = new ArrayList<>();
                        for (MetadataValue aDcUri : dcUri) {
                            values.add(aDcUri.getValue());
                        }
                        itemService.addMetadata(context, item, "dc", "identifier", "other", Item.ANY, values);
                    }

                    // Clear dc.identifier.uri
                    itemService.clearMetadata(context, item, "dc", "identifier", "uri", Item.ANY);

                    // Update dc.identifier.uri
                    if (oldHandle != null && !handleObject.getHandle().isEmpty()) {
                        String newUri = handleClarinService.getCanonicalForm(oldHandle);
                        itemService.addMetadata(context, item, "dc", "identifier", "uri", Item.ANY, newUri);
                    }

                    // Update the metadata
                    itemService.update(context, item);
                }
            }

            if (!newHandle.equals(handleObject.getHandle())) {
                Handle createdHandle = handleClarinService.createHandle(context, handleObject.getResourceTypeId(),
                        handleObject.getUrl());
                handleClarinService.update(context, createdHandle);
            }

            handleClarinService.replaceHandle(context, handleObject,
                    newHandle, url);

        } catch (SQLException e) {
            throw new RuntimeException("error while trying to update handle");
        }
    }

    public void changePrefixInExistingHandles( Context context, String oldPrefix,
                                     String newPrefix, boolean archive) throws AuthorizeException {
        try {
            List<Handle> handles = handleClarinService.findAll(context);
            for (Iterator<Handle> it = handles.iterator(); it.hasNext(); ) {
                Handle handleObject = it.next();
                String[] handleParts = (handleObject.getHandle()).split("/");
                if ((handleParts[0]).equals(oldPrefix)) {
                    updateHandle(context, handleObject, newPrefix + "/" + handleParts[1], handleObject.getUrl(), archive);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("error while trying to change prefix in existing handles");
        }
    }
}
