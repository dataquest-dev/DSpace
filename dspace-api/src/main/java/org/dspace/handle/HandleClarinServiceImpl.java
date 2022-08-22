/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataFieldServiceImpl;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.handle.dao.HandleDAO;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.dspace.handle.external.ExternalHandleConstants.MAGIC_BEAN;

/**
 * Additional service implementation for the Handle object in Clarin-DSpace.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
public class HandleClarinServiceImpl implements HandleClarinService {

    static final String PREFIX_DELIMITER = "/";

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
    public List<Handle> findAllExternalHandles(Context context) throws SQLException {
        // fetch all handles which contains `@magicLindat` string from the DB
        return handleDAO.findAll(context, Handle.class)
                .stream()
                .filter(handle -> Objects.nonNull(handle))
                .filter(handle -> Objects.nonNull(handle.getUrl()))
                .filter(handle -> handle.getUrl().contains(MAGIC_BEAN))
                .collect(Collectors.toList());
    }

    @Override
    public Handle findByID(Context context, int id) throws SQLException {
        return handleDAO.findByID(context, Handle.class, id);
    }

    @Override
    public Handle createHandle(Context context, String handleStr, DSpaceObject dSpaceObject, String url)
            throws SQLException {
        String handleId = null;

        //Do we generate the new handleId generated or use entered handleStr by user?
        if (handleStr != null) {
            //we use handleStr entered by use
            handleId = handleStr;
        } else {
            //we generate new handleId
            handleId = createId(context);
        }

        Handle handle = handleDAO.create(context, new Handle());

        //set handle depending on handleId
        handle.setHandle(handleId);
        //only if dspace object exists
        if (dSpaceObject != null) {
            handle.setDSpaceObject(dSpaceObject);
            handle.setResourceTypeId(dSpaceObject.getType());
        }

        if (url != null) {
            handle.setUrl(url);
        }

        handleDAO.save(context, handle);

        log.debug("Created new Handle with handle " + handleId);

        return handle;
    }

    @Override
    public void delete(Context context, Handle handle) throws SQLException {
        //delete handle
        handleDAO.delete(context, handle);

        log.info(LogHelper.getHeader(context, "delete_handle",
                "handle_id=" + handle.getID()));
    }

    @Override
    public void save(Context context, Handle handle) throws SQLException {
        //save handle
        handleDAO.save(context, handle);

        log.info(LogHelper.getHeader(context, "save_handle",
                "handle_id=" + handle.getID()
                        + "handle=" + handle.getHandle()
                        + "resourceTypeID=" + handle.getResourceTypeId()));
    }

    @Override
    public void update(Context context, Handle handleObject, String newHandle,
                       DSpaceObject dso, String newUrl)
            throws SQLException {
        //set all handle attributes
        handleObject.setHandle(newHandle);
        handleObject.setDSpaceObject(dso);
        //if dspace object is null, set resource type id to null
        if (dso != null) {
            //resource type id is type od dspace object
            handleObject.setResourceTypeId(dso.getType());
        } else {
            handleObject.setResourceTypeId(null);
        }
        if (newUrl != null) {
            handleObject.setUrl(newUrl);
        }

        this.save(context, handleObject);

        log.info(LogHelper.getHeader(context, "update_handle",
                "handle_id=" + handleObject.getID()));
    }

    @Override
    public void setPrefix(Context context, String newPrefix, String oldPrefix) throws SQLException {
        //get handle prefix
        String prefix = handleService.getPrefix();
        //set prefix only if not equal to old prefix
        if (prefix.equals(oldPrefix)) {
            //return value says if set prefix was successful
            if (!(configurationService.setProperty("handle.prefix", newPrefix))) {
                //prefix has not changed
                throw new RuntimeException("error while trying to set handle prefix");
            }
        }

        log.info(LogHelper.getHeader(context, "set_handle_prefix",
                "old_prefix=" + oldPrefix + " new_prefix=" + newPrefix));
    }

    /* Created for LINDAT/CLARIAH-CZ (UFAL) */
    @Override
    public boolean isInternalResource(Handle handle) {
        //in internal handle is not entered url
        return (handle.getUrl() == null || handle.getUrl().isEmpty());
    }

    @Override
    public String resolveToURL(Context context, String handleStr) throws SQLException {
        //handle is not entered
        if (handleStr == null) {
            throw new IllegalArgumentException("Handle is null");
        }

        //find handle
        Handle handle = handleDAO.findByHandle(context, handleStr);

        //handle was not find
        if (handle == null) {
            return null;
        }

        String url;
        if (isInternalResource(handle)) {
            //internal handle
            //create url for internal handle
            url = configurationService.getProperty("dspace.ui.url")
                    + "/handle/" + handle;
        } else {
            //external handle
            url = handle.getUrl();
        }

        log.debug("Resolved {} to {}", handle, url);

        return url;
    }

    @Override
    public List<org.dspace.handle.external.Handle> convertHandleWithMagicToExternalHandle(List<Handle> magicHandles) {
        List<org.dspace.handle.external.Handle> externalHandles = new ArrayList<>();
        for (org.dspace.handle.Handle handleWithMagic: magicHandles) {
            externalHandles.add(new org.dspace.handle.external.Handle(handleWithMagic.getHandle(), handleWithMagic.getUrl()));
        }

        return externalHandles;
    }

    /**
     * Returns complete handle made from prefix and suffix
     */
    public String completeHandle(String prefix, String suffix) {
        return prefix + PREFIX_DELIMITER + suffix;
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


}
