/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl;

import java.sql.SQLException;
import java.util.List;
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
    public List<PreviewContent> findByBitstream(Context context, UUID bitstream_id) throws SQLException {
        Query query = createQuery(context, "SELECT pc FROM " + PreviewContent.class.getSimpleName() +
                        " as pc join pc.bitstream as b WHERE b.id = :bitstream_id");
        query.setParameter("bitstream_id", bitstream_id);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return findMany(context, query);
    }

    @Override
    public List<PreviewContent> findRootByBitstream(Context context, UUID bitstream_id) throws SQLException {
        Query query = createQuery(context,
                "SELECT pc FROM " + PreviewContent.class.getSimpleName() + " pc " +
                        "JOIN pc.bitstream b " +
                        "WHERE b.id = :bitstream_id " +
                        "AND pc.id NOT IN (SELECT child.id FROM " + PreviewContent.class.getSimpleName() + " parent " +
                        "JOIN parent.sub child)"
        );
        query.setParameter("bitstream_id", bitstream_id);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return findMany(context, query);
    }
}
