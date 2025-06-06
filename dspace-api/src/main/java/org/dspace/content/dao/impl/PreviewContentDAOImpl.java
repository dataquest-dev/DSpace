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

import org.dspace.content.Bitstream;
import org.dspace.content.PreviewContent;
import org.dspace.content.dao.PreviewContentDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * Hibernate implementation of the Database Access Object interface class for the PreviewContent object.
 * This class should never be accessed directly.
 *
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
public class PreviewContentDAOImpl extends AbstractHibernateDAO<PreviewContent> implements PreviewContentDAO {

    protected PreviewContentDAOImpl() {
        super();
    }

    @Override
    public List<PreviewContent> findByBitstream(Context context, UUID bitstreamId) throws SQLException {
        Query query = createQuery(context, "SELECT pc FROM " + PreviewContent.class.getSimpleName() +
                        " as pc join pc.bitstream as b WHERE b.id = :bitstream_id");
        query.setParameter("bitstream_id", bitstreamId);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return findMany(context, query);
    }

    @Override
    public List<PreviewContent> hasPreview(Context context, Bitstream bitstream) throws SQLException {
        String sql =
                "SELECT 1 FROM previewcontent pc\n" +
                        "WHERE pc.bitstream_id = :bitstream_id\n" +
                        "LIMIT 1";
        Query query = getHibernateSession(context).createNativeQuery(sql, PreviewContent.class);
        query.setParameter("bitstream_id", bitstream.getID());
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);
        return findMany(context, query);
    }
}
