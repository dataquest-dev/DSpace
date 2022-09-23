/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao.impl.clarin;

import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.dao.clarin.ClarinLicenseDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

import javax.persistence.Query;
import java.sql.SQLException;

/**
 * Hibernate implementation of the Database Access Object interface class for the Clarin License object.
 * This class is responsible for all database calls for the Clarin License object and is autowired by spring
 * This class should never be accessed directly.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinLicenseDAOImpl extends AbstractHibernateDAO<ClarinLicense> implements ClarinLicenseDAO {
    protected ClarinLicenseDAOImpl() {
        super();
    }

    @Override
    public ClarinLicense findByDefinition(Context context, String definition) throws SQLException {
        Query query = createQuery(context, "SELECT cl " +
                "FROM ClarinLicense cl " +
                "WHERE cl.definition = :definition");

        query.setParameter("definition", definition);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return singleResult(query);
    }
}
