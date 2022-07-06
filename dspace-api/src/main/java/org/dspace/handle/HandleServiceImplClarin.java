package org.dspace.handle;

import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;

public class HandleServiceImplClarin extends HandleServiceImpl {

    public List<Handle> findAll(Context context) throws SQLException {
        return handleDAO.findAll(context, Handle.class);
    }
}
