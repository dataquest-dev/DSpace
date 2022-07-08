/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.HandleRest;
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

import javax.servlet.http.HttpServletRequest;

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
    protected Handle create(Context context, DSpaceObject dSpaceObject) throws AuthorizeException {
        try {
            return handleClarinService.createHandle(context, dSpaceObject);
        } catch (SQLException e) {
            throw new RuntimeException("error while trying to create new Handle for dspaceobjecs with id: " + dSpaceObject.getID(), e);
        }
    }

    //este bude prerobena
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void changePrefix(Context context, String newPrefix) throws AuthorizeException {
//        try {
//            if (!handleClarinService.changePrefix(context, newPrefix)) {
//                throw new RuntimeException("error while trying to change handle prefix");
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException("error while trying to change handle prefix");
//        }
    }

    //este prerob - posielanie objektu do request
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void patch(Context context, HttpServletRequest request, DSpaceObject dSpaceObject, Integer id) throws AuthorizeException {
//        try {
//            Handle foundHandle = handleClarinService.findByID(context, id);
//            handleClarinService.save(context, );
//        } catch (SQLException e) {
//            throw new RuntimeException("error while trying to edit Handle with id: " + id, e);
//        }
    }

    @Override
    public Class<HandleRest> getDomainClass() {
        return HandleRest.class;
    }
}
