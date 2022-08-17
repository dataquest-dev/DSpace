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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.handle.Handle;

/**
 * Additional service interface class of HandleService for the Handle object in Clarin.
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
     * @param context             DSpace context object
     * @param resourceTypeID      Integer
     * @param url                 String
     * @return new Handle
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public Handle createHandle(Context context, DSpaceObject dSpaceObject, String url)
            throws SQLException, AuthorizeException;

    /**
     * Delete the handle.
     *
     * @param context             DSpace context object
     * @param handle              handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    public void delete(Context context, Handle handle) throws SQLException, AuthorizeException;

    /**
     * Save the metadata field in the database.
     *
     * @param context             dspace context
     * @param handle              handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    public void save(Context context, Handle handle)
            throws SQLException, AuthorizeException;

    /**
     * Update handle and url in handle object.
     *
     * @param context             DSpace context object
     * @param handleObject        handle object
     * @param newHandle           handle
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public void update(Context context, Handle handleObject, String newHandle, DSpaceObject dso,
                       Integer resourceTypeId, String newUrl)
            throws SQLException, AuthorizeException;

    /**
     * Set handle prefix.
     *
     * @param context   DSpace context object
     * @param newPrefix new prefix
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public void setPrefix(Context context, String newPrefix, String oldprefix) throws SQLException, AuthorizeException;

    /**
     * Control, if handle is internal resource.
     *
     * @param handle   handle object
     * @return         boolean
     */
    public boolean isInternalResource(Handle handle);

    /**
     * Transforms handle into the canonical form <em>hdl:handle</em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle handle
     * @return       The canonical form
     */
    public String getCanonicalForm(String handle);

    public DSpaceObject resolve(Context context, String identifier);

    public String resolveToURL(Context context, String handle_str)
            throws SQLException;

}
