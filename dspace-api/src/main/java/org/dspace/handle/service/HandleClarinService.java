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
     * @param context DSpace context object
     * @return        array of handles
     * @throws SQLException if database error
     */
    public List<Handle> findAll(Context context) throws SQLException;

    /**
     * Find the handle corresponding to the given numeric ID.  The ID is
     * a database key internal to DSpace.
     *
     * @param context DSpace context object
     * @param id      the handle ID
     * @return the handle object
     * @throws SQLException if database error
     */
    public Handle findByID(Context context, int id) throws SQLException;

    /**
     * Creates a new handle.
     *
     * @param context DSpace context object
     * @param dso     dspace object
     * @return new Handle
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public Handle createHandle(Context context, DSpaceObject dso)
            throws SQLException, AuthorizeException;

    /**
     * Delete the handle.
     *
     * @param context DSpace context object
     * @param handle  handle
     * @throws SQLException       if database error
     * @throws AuthorizeException if authorization error
     */
    public void delete(Context context, Handle handle) throws SQLException, AuthorizeException;

    /**
     * Save the handle.
     *
     * @param context DSpace context object
     * @param handle  handle
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public void save(Context context, Handle handle) throws SQLException, AuthorizeException;

    /**
     * Replace handle in handle object by new handle.
     *
     * @param context   DSpace context object
     * @param handleObject handle object
     * @param newHandle handle
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public void replaceHandle(Context context, Handle handleObject, String newHandle)
            throws SQLException, AuthorizeException;

    /**
     * Set handle prefix.
     *
     * @param context   DSpace context object
     * @param newPrefix new prefix
     * @throws AuthorizeException if authorization error
     * @throws SQLException       if database error
     */
    public void setPrefix(Context context, String newPrefix) throws SQLException, AuthorizeException;
}
