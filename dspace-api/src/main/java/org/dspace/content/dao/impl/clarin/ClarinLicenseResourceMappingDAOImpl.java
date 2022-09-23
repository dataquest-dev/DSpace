package org.dspace.content.dao.impl.clarin;

import org.dspace.content.Bitstream;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.dao.clarin.ClarinLicenseResourceMappingDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

import javax.persistence.Query;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class ClarinLicenseResourceMappingDAOImpl extends AbstractHibernateDAO<ClarinLicenseResourceMapping> implements ClarinLicenseResourceMappingDAO {
    protected ClarinLicenseResourceMappingDAOImpl() {
        super();
    }

    @Override
    public List<ClarinLicenseResourceMapping> findByBitstreamUUID(Context context, UUID bitstreamUUID) throws SQLException {
        Query query = createQuery(context, "SELECT clrm " +
                "FROM ClarinLicenseResourceMapping clrm " +
                "WHERE clrm.bitstream.id = :bitstreamUUID");

        query.setParameter("bitstreamUUID", bitstreamUUID);
        query.setHint("org.hibernate.cacheable", Boolean.TRUE);

        return list(query);
    }
}
