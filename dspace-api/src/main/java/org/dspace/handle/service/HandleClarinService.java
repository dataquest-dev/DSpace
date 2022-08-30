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
import org.dspace.core.Context;
import org.dspace.handle.Handle;

/**
 * Additional service interface class of HandleService for the Handle object in Clarin-DSpace.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
public interface HandleClarinService {


    /**
     * Find all Handles following the sorting options
     * @param context DSpace context object
     * @param sortingColumn sorting option in the specific format e.g. `handle:123456789/111`
     * @param maxResult the max count of the handles
     * @param offset page
     * @return List of Handles
     */
    List<Handle> findAll(Context context, String sortingColumn, int maxResult, int offset) throws SQLException;

    /**
     * Retrieve all handles from the database
     *
     * @param context       DSpace context object
     * @return              list of handles
     * @throws SQLException if database error
     */
    List<Handle> findAll(Context context) throws SQLException;

    /**
     * Find the handle corresponding to the given numeric ID.  The ID is
     * a database key internal to DSpace.
     *
     * @param context       DSpace context object
     * @param id            the handle ID
     * @return              the handle object
     * @throws SQLException if database error
     */
    Handle findByID(Context context, int id) throws SQLException;

    /**
     * Find the handle corresponding to the given string handle.
     *
     * @param context       DSpace context object
     * @param handle        string handle
     * @return              the handle object
     * @throws SQLException if database error
     */
    Handle findByHandle(Context context, String handle) throws SQLException;

    /**
     * Creates a new external handle.
     * External handle has to have entered URL.
     *
     * @param context             DSpace context object
     * @param handleStr           String
     * @param url                 String
     * @return new Handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    Handle createExternalHandle(Context context, String handleStr, String url)
            throws SQLException, AuthorizeException;

    /**
     * Delete the handle.
     *
     * @param context             DSpace context object
     * @param handle              handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    void delete(Context context, Handle handle) throws SQLException, AuthorizeException;

    /**
     * Save the metadata field in the database.
     *
     * @param context             dspace context
     * @param handle              handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    void save(Context context, Handle handle)
            throws SQLException, AuthorizeException;

    /**
     * Update handle and url in handle object.
     * It is not possible to update external handle to internal handle or
     * external handle to internal handle.
     *
     * @param context             DSpace context object
     * @param newHandle           new handle
     * @param newUrl              new url
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    void update(Context context, Handle handleObject, String newHandle,
                      String newUrl)
            throws SQLException, AuthorizeException;

    /**
     * Set handle prefix.
     *
     * @param context             DSpace context object
     * @param newPrefix           new prefix
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    void setPrefix(Context context, String newPrefix, String oldPrefix) throws SQLException, AuthorizeException;

    /* Created for LINDAT/CLARIAH-CZ (UFAL) */
    /**
     * Control, if handle is internal resource.
     *
     * @param handle handle object
     * @return       boolean
     */
    boolean isInternalResource(Handle handle);

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
    String resolveToURL(Context context, String handleStr) throws SQLException;
}
