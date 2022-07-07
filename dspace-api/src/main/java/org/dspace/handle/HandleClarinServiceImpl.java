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

import org.dspace.core.Context;
import org.dspace.handle.dao.HandleDAO;
import org.dspace.handle.service.HandleClarinService;
import org.springframework.beans.factory.annotation.Autowired;

public class HandleClarinServiceImpl implements HandleClarinService {

    @Autowired(required = true)
    protected HandleDAO handleDAO;

    /**
     * Public Constructor
     */
    protected HandleClarinServiceImpl() {
       }

    @Override
    public List<Handle> findAll(Context context) throws SQLException {
        return handleDAO.findAll(context, Handle.class);
    }
}
