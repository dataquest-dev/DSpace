package org.dspace.handle.dao;

import org.dspace.core.Context;
import org.dspace.handle.Handle;

import java.sql.SQLException;
import java.util.List;

public interface HandleClarinDAO {

    public List<Handle> findAll(Context context, String sortingColumn, boolean direction, int maxResult, int offset) throws SQLException;
}
