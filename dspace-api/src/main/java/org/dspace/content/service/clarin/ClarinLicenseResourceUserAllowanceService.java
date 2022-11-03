package org.dspace.content.service.clarin;

import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinLicenseResourceUserAllowanceService {
    boolean verifyToken(String resourceID, String token);
    boolean isUserAllowedToAccessTheResource(Context context, UUID userId, UUID resourceId) throws SQLException;
    List<ClarinLicenseResourceUserAllowance> findByEPersonId(Context context, UUID userID) throws SQLException;
}
