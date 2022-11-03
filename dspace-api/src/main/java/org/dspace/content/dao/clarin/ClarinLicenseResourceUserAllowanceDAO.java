package org.dspace.content.dao.clarin;

import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinLicenseResourceUserAllowanceDAO {
    boolean verifyToken(String resourceID, String token);
    public List<ClarinLicenseResourceUserAllowance> findByEPersonId(Context context, UUID userID) throws SQLException;
}
