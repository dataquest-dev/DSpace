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
import java.util.List;
import javax.servlet.http.HttpServletRequest;

//import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.HandleRest;
//import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.MetadataFieldRest;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.Patch;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
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
        Handle handle = null;

        try {
            handle = new ObjectMapper().readValue(
                    getRequestService().getCurrentRequest().getHttpServletRequest().getInputStream(),
                    Handle.class
            );
        } catch (IOException excIO) {
            throw new DSpaceBadRequestException("error parsing request body", excIO);
        }

        try {
            handle = handleClarinService.createHandle(context, handle.getDSpaceObject());
            // @TODO create update method
//            handleClarinService.update(context, handle);
        } catch (SQLException e) {
            throw new RuntimeException
            ("error while trying to create new Handle for dspaceobjecs with id: " + handle.getID(), e);
        }

        if (ObjectUtils.isEmpty(handle)) {
            // @TODO throw exception
            return null;
        }
        return converter.toRest(handle, utils.obtainProjection());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    protected void patch(Context context, HttpServletRequest request, String apiCategory, String model, Integer id,
                         Patch patch) throws SQLException, AuthorizeException {
        List<Operation> operations = patch.getOperations();
        try {
            for (Operation operation : patch.getOperations()) {
                if (operation.getOp() == "replace") {
                    //
                    switch (operation.getPath()) {
                        case "/replaceHandle":
                            //replace handle in handle object by new handle
                            Handle handleObject = handleClarinService.findByID(context, id);
                            if (operation.getValue() != null && handleObject != null) {
                                //if value is Hashmap
//                                JsonNode valueNode = ((JsonValueEvaluator) operation.getValue())
//                                        .getValueNode().get("value");
//                                String newHandle = valueNode.textValue();
//                                handleClarinService.editHandle(context, oldHandle, newHandle);
                                handleClarinService.replaceHandle(context, handleObject,
                                        operation.getValue().toString());
                            }
                            break;
                        case "/setPrefix":
                            if (operation.getValue() != null) {
                                //set handle prefix
                                //if value is Hashmap
//                                JsonNode valueNode = ((JsonValueEvaluator) operation.getValue())
//                                        .getValueNode().get("value");
//                                String newPrefix = valueNode.textValue();
//                                handleClarinService.setPrefix(context, newPrefix);
                                handleClarinService.setPrefix(context, operation.getValue().toString());
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
}
