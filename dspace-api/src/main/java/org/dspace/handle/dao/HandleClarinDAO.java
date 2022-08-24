/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.dao;

import org.dspace.core.Context;
import org.dspace.core.GenericDAO;
import org.dspace.handle.Handle;

import java.sql.SQLException;

public interface HandleClarinDAO extends GenericDAO<Handle> {
    public Handle findByHandle(Context context, String handle) throws SQLException;
}
