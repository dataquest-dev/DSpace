package org.dspace.content.dao.impl.clarin;

import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;

public class ClarinLicenseResourceUserAllowanceDAOImpl implements ClarinLicenseResourceUserAllowanceDAO {
    @Override
    public boolean verifyToken(String resourceID, String token) {
        return false;
    }
}
