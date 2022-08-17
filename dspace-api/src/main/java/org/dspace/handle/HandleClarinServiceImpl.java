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

    static final String PART_IDENTIFIER_DELIMITER = "@";

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
    public Handle createHandle(Context context, DSpaceObject dSpaceObject, String url)
            throws SQLException, AuthorizeException  {
        // Check authorisation: Only admins may create DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        Handle handle = handleDAO.create(context, new Handle());
        String handleId = createId(context);
        handle.setHandle(handleId);
        handle.setDSpaceObject(dSpaceObject);
        if (dSpaceObject != null) {
            handle.setResourceTypeId(dSpaceObject.getType());
        }
        handle.setUrl(url);
        handleDAO.save(context, handle);

        if (dSpaceObject != null) {
            log.debug("Created new Handle for {} (ID={}) {}",
                () -> Constants.typeText[dSpaceObject.getType()],
                () -> dSpaceObject.getType(),
                () -> handleId);
        } else {
            log.debug("Created new Handle without dspace object");
        }

        return handle;
    }

    @Override
    public void delete(Context context, Handle handle) throws SQLException, AuthorizeException {

        // Check authorisation: Only admins may delete DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        handleDAO.delete(context, handle);

        log.info(LogHelper.getHeader(context, "delete_handle",
                "handle_id=" + handle.getID()));
    }

    @Override
    public void save(Context context, Handle handle) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may update the metadata registry
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        handleDAO.save(context, handle);

        log.info(LogHelper.getHeader(context, "save_handle",
                "handle_id=" + handle.getID()
                        + "handle=" + handle.getHandle()
                        + "resourceTypeID=" + handle.getResourceTypeId()));
    }

    @Override
    public void update(Context context, Handle handleObject, String newHandle,
                       DSpaceObject dso, Integer resourceTypeId, String newUrl)
            throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may update DC types
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the handle registry");
        }

        handleObject.setHandle(newHandle);
        handleObject.setDSpaceObject(dso);
        handleObject.setResourceTypeId(resourceTypeId);
        handleObject.setUrl(newUrl);
        this.save(context, handleObject);

        log.info(LogHelper.getHeader(context, "update_handle",
                "handle_id=" + handleObject.getID()));
    }

    @Override
    public void setPrefix(Context context, String newPrefix, String oldPrefix) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may set handle prefix
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

        log.info(LogHelper.getHeader(context, "set_handle_prefix",
                "old_prefix=" + oldPrefix + " new_prefix=" + newPrefix));
    }

    @Override
    public boolean isInternalResource(Handle handle) {
        return (handle.getUrl() == null || handle.getUrl().isEmpty());
    }

    @Override
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

    @Override
    public DSpaceObject resolve(Context context, String identifier) {
        // We can do nothing with this, return null
        try {
            Handle handle = handleDAO.findByHandle(context, identifier);

            if (handle == null) {
                // Check for an url
                identifier = retrieveHandleOutOfUrl(identifier);
                if (identifier != null) {
                    handle = handleDAO.findByHandle(context, identifier);
                }

                if (handle == null) {
                    return null;
                }
            }

            return handle.getDSpaceObject();

        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to resolve handle");
        }
    }

    @Override
    public String resolveToURL(Context context, String handle_str) throws SQLException {
        // <UFAL>
        String baseHandle = stripPartIdentifier(handle_str);

        log.debug(String.format("Base handle [%s]", baseHandle));

        Handle handle = handleDAO.findByHandle(context, baseHandle);

        if (handle == null) {
            return null;
        }

        String url = null;

        if (handle.getUrl() != null) {
            url = handle.getUrl();
        } else {
            url = configurationService.getProperty("dspace.url") + "/handle/"
                    + baseHandle;
        }

        String partIdentifier = extractPartIdentifier(handle_str);
        url = appendPartIdentifierToUrl(url, partIdentifier);
        // </UFAL>

        if (log.isDebugEnabled()) {
            log.debug("Resolved " + handle + " to " + url);
        }

        return url;
    }

    /**
     * Create id for handle object.
     *
     * @param context       DSpace context object
     * @return              handle id
     * @throws SQLException if database error
     */
    private String createId(Context context) throws SQLException {
        // Get configured prefix
        String handlePrefix = handleService.getPrefix();

        // Get next available suffix (as a Long, since DSpace uses an incrementing sequence)
        Long handleSuffix = handleDAO.getNextHandleSuffix(context);

        return handlePrefix + (handlePrefix.endsWith("/") ? "" : "/") + handleSuffix.toString();
    }

    private static String retrieveHandleOutOfUrl(String url)
            throws SQLException {
        // We can do nothing with this, return null
        if (!url.contains("/")) {
            return null;
        }

        String[] splitUrl = url.split("/");

        return splitUrl[splitUrl.length - 2] + "/" + splitUrl[splitUrl.length - 1];
    }


    /**
     * Strips the part identifier from the handle
     *
     * @param handle The handle with optional part identifier
     * @return The handle without the part identifier
     */
    private static String stripPartIdentifier(String handle) {
        String baseHandle = null;
        if (handle != null) {
            int pos = handle.indexOf(PART_IDENTIFIER_DELIMITER);
            if (pos >= 0) {
                baseHandle = handle.substring(0, pos);
            } else {
                baseHandle = handle;
            }
        }
        return baseHandle;
    }

    /**
     * Extracts the part identifier from the handle
     *
     * @param handle The handle with optional part identifier
     * @return part identifier or null
     */
    private static String extractPartIdentifier(String handle) {
        String partIdentifier = null;
        if (handle != null) {
            int pos = handle.indexOf(PART_IDENTIFIER_DELIMITER);
            if (pos >= 0) {
                partIdentifier = handle.substring(pos + 1);
            }
        }
        return partIdentifier;
    }

    /**
     * Appends the partIdentifier as parameters to the given URL
     *
     * @param url The URL
     * @param partIdentifier  Part identifier (can be null or empty)
     * @return Final URL with part identifier appended as parameters to the given URL
     */
    private static String appendPartIdentifierToUrl(String url, String partIdentifier) {
        String finalUrl = url;
        if (finalUrl != null && partIdentifier != null && !partIdentifier.isEmpty()) {
            if (finalUrl.contains("?")) {
                finalUrl += '&' + partIdentifier;
            } else {
                finalUrl += '?' + partIdentifier;
            }
        }
        return finalUrl;
    }
}
