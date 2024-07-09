/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl;

import java.sql.SQLException;
import java.util.UUID;
import javax.persistence.Query;

import org.dspace.content.PreviewContent;
import org.dspace.content.dao.PreviewContentDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

public class PreviewContentDAOImpl extends AbstractHibernateDAO<PreviewContent> implements PreviewContentDAO {

    protected PreviewContentDAOImpl() {
        super();
    }

    @Override
    public PreviewContent find(Context context, UUID bitstream_id, String path, long size_bytes) throws SQLException {
        Query query = createQuery(context, "SELECT cp " +
                "FROM PreviewContent cp " +
                "WHERE cp.bitstream_id = :bitstream_id AND " +
                "cp.path = :path AND cp.size_bytes = :size_bytes");

        query.setParameter("bitstream_id", bitstream_id);
        query.setParameter("path", path);
        query.setParameter("size_bytes", size_bytes);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return singleResult(query);
    }
}
