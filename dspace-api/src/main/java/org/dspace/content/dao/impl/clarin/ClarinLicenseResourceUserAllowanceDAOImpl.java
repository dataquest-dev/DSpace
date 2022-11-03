/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl.clarin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.persistence.Query;

import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

public class ClarinLicenseResourceUserAllowanceDAOImpl extends AbstractHibernateDAO<ClarinLicenseResourceUserAllowance>
        implements ClarinLicenseResourceUserAllowanceDAO {
    @Override
    public boolean verifyToken(String resourceID, String token) {
        return false;
    }

    @Override
    public List<ClarinLicenseResourceUserAllowance> findByEPersonId(Context context, UUID userID) throws SQLException {
        Query query = createQuery(context, "SELECT clrua " +
                "FROM ClarinLicenseResourceUserAllowance clrua " +
                "WHERE clrua.userRegistration.id = :userID");

        query.setParameter("userID", userID);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return list(query);
    }
}
