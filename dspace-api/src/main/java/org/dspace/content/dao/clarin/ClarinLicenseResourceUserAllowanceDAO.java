package org.dspace.content.dao.clarin;

import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinLicenseResourceUserAllowanceDAO {
    List<ClarinLicenseResourceUserAllowance> findByTokenAndBitstreamId(Context context, String resourceID,
                                                                       String token) throws SQLException;
    public List<ClarinLicenseResourceUserAllowance> findByEPersonId(Context context, UUID userID) throws SQLException;
}
