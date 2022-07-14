/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataFieldServiceImpl;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.handle.dao.HandleDAO;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class HandleClarinServiceImpl implements HandleClarinService {

    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataFieldServiceImpl.class);

    @Autowired(required = true)
    protected HandleDAO handleDAO;

    @Autowired(required = true)
    protected HandleService handleService;

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;



    protected HandleClarinServiceImpl() {
    }

    @Override
    public List<Handle> findAll(Context context) throws SQLException {
        return handleDAO.findAll(context, Handle.class);
    }
    @Override
    public Handle findByID(Context context, int id) throws SQLException {
        return handleDAO.findByID(context, Handle.class, id);
    }

    @Override
    public void delete(Context context, Handle handle) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        handleDAO.delete(context, handle);

        log.info(LogHelper.getHeader(context, "delete_metadata_field",
                "handle_id=" + handle.getID()));
    }

    @Override
    public Handle createHandle(Context context, DSpaceObject dso) throws SQLException, AuthorizeException  {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        Handle handle = handleDAO.create(context, new Handle());
        String handleId = createId(context);

        handle.setHandle(handleId);
        handle.setDSpaceObject(dso);
        dso.addHandle(handle);
        handle.setResourceTypeId(dso.getType());
        handleDAO.save(context, handle);

        log.debug("Created new Handle for {} (ID={}) {}",
            () -> Constants.typeText[dso.getType()],
            () -> dso.getID(),
            () -> handleId);

        return handle;
    }

    @Override
    public void save(Context context, Handle handle) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        handleDAO.save(context,handle);
    }

    @Override
    public void editHandle(Context context, Handle oldHandle, String newHandle)
            throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        oldHandle.setHandle(newHandle);
        handleDAO.save(context, oldHandle);
    }

    @Override
    public void setPrefix(Context context, String newPrefix) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        if (!(configurationService.setProperty("handle.prefix",newPrefix))) {
            throw new RuntimeException("error while trying to change handle prefix");
        }
    }

    protected String createId(Context context) throws SQLException {
        // Get configured prefix
        String handlePrefix = handleService.getPrefix();

        // Get next available suffix (as a Long, since DSpace uses an incrementing sequence)
        Long handleSuffix = handleDAO.getNextHandleSuffix(context);

        return handlePrefix + (handlePrefix.endsWith("/") ? "" : "/") + handleSuffix.toString();
    }
}
