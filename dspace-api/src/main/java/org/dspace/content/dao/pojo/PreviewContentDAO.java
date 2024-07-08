package org.dspace.content.dao.pojo;

import org.dspace.content.PreviewContent;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

import java.sql.SQLException;
import java.util.UUID;

public interface PreviewContentDAO extends GenericDAO<PreviewContent> {
    PreviewContent find(Context context, UUID bitstream_id, String path, long size_bytes) throws SQLException;

}
