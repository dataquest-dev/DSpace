package org.dspace.content.dao.clarin;

public interface ClarinLicenseResourceUserAllowanceDAO {
    boolean verifyToken(String resourceID, String token);
}
