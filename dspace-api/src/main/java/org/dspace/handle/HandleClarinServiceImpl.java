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
import org.dspace.content.MetadataFieldServiceImpl;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.handle.dao.HandleDAO;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Additional service implementation for the Handle object in Clarin.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
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
    protected ItemService itemService;

    @Autowired(required = true)
    protected ConfigurationService configurationService;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;


    /**
     * Public Constructor
     */
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
    public Handle createHandle(Context context, Integer resourceTypeID, String url)
            throws SQLException, AuthorizeException  {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        Handle handle = handleDAO.create(context, new Handle());
        String handleId = createId(context);
        handle.setHandle(handleId);
        handle.setResourceTypeId(resourceTypeID);
        handle.setUrl(url);
        handleDAO.save(context, handle);

        log.debug("Created new Handle for {} (ID={}) {}",
            () -> Constants.typeText[resourceTypeID],
            () -> resourceTypeID,
            () -> handleId);

        return handle;
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
    public void update(Context context, Handle handle) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may update the metadata registry
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modiffy the handle registry");
        }

        handleDAO.save(context, handle);

        log.info(LogHelper.getHeader(context, "update_handleregistry",
                "handle_id=" + handle.getID()
                        + "handle=" + handle.getHandle()
                        + "resourceTypeID=" + handle.getResourceTypeId()));
    }

    @Override
    public void replaceHandle(Context context, Handle handleObject, String newHandle, String newUrl)
            throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        handleObject.setUrl(newUrl);
        handleObject.setHandle(newHandle);
        handleDAO.save(context, handleObject);
    }

    @Override
    public void setPrefix(Context context, String newPrefix, String oldPrefix) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }
        String handle = handleService.getPrefix();
        if (handle.equals(oldPrefix)) {
            if (!(configurationService.setProperty("handle.prefix", newPrefix))) {
                throw new RuntimeException("error while trying to set handle prefix");
            }
        }
    }

    @Override
    public boolean isInternalResource(Handle handle) {
        return (handle.getUrl() == null || handle.getUrl().isEmpty());
    }

    /**
     * Transforms handle into the canonical form <em>hdl:handle</em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle
     *            The handle
     * @return The canonical form
     */
    public String getCanonicalForm(String handle) {
        // Let the admin define a new prefix, if not then we'll use the
        // CNRI default. This allows the admin to use "hdl:" if they want to or
        // use a locally branded prefix handle.myuni.edu.
        String handlePrefix = configurationService.getProperty("handle.canonical.prefix");
        if (handlePrefix == null || handlePrefix.length() == 0) {
            handlePrefix = "http://hdl.handle.net/";
        }

        return handlePrefix + handle;
    }

    private String createId(Context context) throws SQLException {
        // Get configured prefix
        String handlePrefix = handleService.getPrefix();

        // Get next available suffix (as a Long, since DSpace uses an incrementing sequence)
        Long handleSuffix = handleDAO.getNextHandleSuffix(context);

        return handlePrefix + (handlePrefix.endsWith("/") ? "" : "/") + handleSuffix.toString();
    }
}
