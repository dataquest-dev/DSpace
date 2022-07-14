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

public interface HandleClarinService {
    public List<Handle> findAll(Context context) throws SQLException;

    public Handle findByID(Context context, int id) throws SQLException;

    public void delete(Context context, Handle handle) throws SQLException, AuthorizeException;

    public Handle createHandle(Context context, DSpaceObject dso)
            throws SQLException, AuthorizeException;

    public void save(Context context, Handle handle) throws SQLException, AuthorizeException;

    public void editHandle(Context context, Handle oldHandle, String newHandle) throws SQLException, AuthorizeException;

    public void setPrefix(Context context, String newPrefix) throws SQLException, AuthorizeException;


}
