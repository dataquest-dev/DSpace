package org.dspace.content.dao.impl.clarin;

import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinVerificationToken;
import org.dspace.content.dao.clarin.ClarinLicenseDAO;
import org.dspace.content.dao.clarin.ClarinVerificationTokenDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

import javax.persistence.Query;
import java.sql.SQLException;

public class ClarinVerificationTokenDAOImpl extends AbstractHibernateDAO<ClarinVerificationToken>
        implements ClarinVerificationTokenDAO {

    @Override
    public ClarinVerificationToken findByToken(Context context, String token) throws SQLException {
        Query query = createQuery(context, "SELECT cvt " +
                "FROM ClarinVerificationToken cvt " +
                "WHERE cvt.token = :token");

        query.setParameter("token", token);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return singleResult(query);
    }

    @Override
    public ClarinVerificationToken findByNetID(Context context, String netID) throws SQLException {
        Query query = createQuery(context, "SELECT cvt " +
                "FROM ClarinVerificationToken cvt " +
                "WHERE cvt.ePersonNetID = :netID");

        query.setParameter("netID", netID);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return singleResult(query);
    }
}
