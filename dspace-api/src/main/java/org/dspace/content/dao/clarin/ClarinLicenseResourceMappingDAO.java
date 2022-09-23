package org.dspace.content.dao.clarin;

import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinLicenseResourceMappingDAO extends GenericDAO<ClarinLicenseResourceMapping> {

    List<ClarinLicenseResourceMapping> findByBitstreamUUID(Context context, UUID bitstreamUUID) throws SQLException;
}
