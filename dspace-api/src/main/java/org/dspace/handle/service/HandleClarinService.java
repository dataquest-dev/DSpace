package org.dspace.handle.service;

import java.sql.SQLException;
import java.util.List;

import org.dspace.core.Context;
import org.dspace.handle.Handle;

public interface HandleClarinService {
    public List<Handle> findAll(Context context) throws SQLException;
}
