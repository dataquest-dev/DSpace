/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.service;

import java.sql.SQLException;
import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.handle.Handle;

/**
 * Additional service interface class of HandleService for the Handle object in Clarin-DSpace.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
public interface HandleClarinService {
    /**
     * Retrieve all handle from the registry
     *
     * @param context       DSpace context object
     * @return              array of handles
     * @throws SQLException if database error
     */
    public List<Handle> findAll(Context context) throws SQLException;

    /**
     * Find the handle corresponding to the given numeric ID.  The ID is
     * a database key internal to DSpace.
     *
     * @param context       DSpace context object
     * @param id            the handle ID
     * @return              the handle object
     * @throws SQLException if database error
     */
    public Handle findByID(Context context, int id) throws SQLException;

    /**
     * Creates a new handle.
     *
     * @param context       DSpace context object
     * @param handleStr     String
     * @param dSpaceObject  DSpaceObject
     * @param url           String
     * @return new Handle
     * @throws SQLException if database error
     */
    public Handle createHandle(Context context, String handleStr, DSpaceObject dSpaceObject, String url)
            throws SQLException;

    /**
     * Delete the handle.
     *
     * @param context       DSpace context object
     * @param handle        handle
     * @throws SQLException if database error
     */
    public void delete(Context context, Handle handle) throws SQLException;

    /**
     * Save the metadata field in the database.
     *
     * @param context       dspace context
     * @param handle        handle
     * @throws SQLException if database error
     */
    public void save(Context context, Handle handle)
            throws SQLException;

    /**
     * Update all attributes in handle object.
     *
     * @param context             DSpace context object
     * @param handleObject        handle object
     * @param newHandle           handle
     * @throws SQLException       if database error
     */
    public void update(Context context, Handle handleObject, String newHandle,
                       DSpaceObject dso, String newUrl)
            throws SQLException;

    /**
     * Set handle prefix.
     *
     * @param context       DSpace context object
     * @param newPrefix     new prefix
     * @throws SQLException if database error
     */
    public void setPrefix(Context context, String newPrefix, String oldPrefix) throws SQLException;

    /* Created for LINDAT/CLARIAH-CZ (UFAL) */
    /**
     * Control, if handle is internal resource.
     *
     * @param handle handle object
     * @return       boolean
     */
    public boolean isInternalResource(Handle handle);

    public String completeHandle(String prefix, String suffix);

    /**
     * Return the local URL for internal handle,
     * saved url for external handle
     * and null if handle cannot be found.
     *
     * @param context       DSpace context
     * @param handleStr     The handle
     * @return The URL
     * @throws SQLException If a database error occurs
     */
    public String resolveToURL(Context context, String handleStr) throws SQLException;
}
