package org.dspace.content.dao.impl;

import org.dspace.content.PreviewContent;
import org.dspace.content.dao.pojo.PreviewContentDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

import javax.persistence.Query;
import java.sql.SQLException;
import java.util.UUID;

public class PreviewContentDAOImpl extends AbstractHibernateDAO<PreviewContent> implements PreviewContentDAO {

    protected PreviewContentDAOImpl() {
        super();
    }

    @Override
    public PreviewContent find(Context context, UUID bitstream_id, String path, long size_bytes) throws SQLException {
        Query query = createQuery(context, "SELECT cp " +
                "FROM ClarinContentPreview cp " +
                "WHERE cp.bitstream_id = :bitstream_id AND " +
                "cp.path = :path AND cp.size_bytes = :size_bytes");

        query.setParameter("bitstream_id", bitstream_id);
        query.setParameter("path", path);
        query.setParameter("size_bytes", size_bytes);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return singleResult(query);
    }
}
