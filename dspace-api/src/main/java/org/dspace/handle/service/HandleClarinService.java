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

import org.dspace.core.Context;
import org.dspace.handle.Handle;

public interface HandleClarinService {
    public List<Handle> findAll(Context context) throws SQLException;
}
