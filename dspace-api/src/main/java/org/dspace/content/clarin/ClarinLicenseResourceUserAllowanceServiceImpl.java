package org.dspace.content.clarin;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ClarinLicenseResourceUserAllowanceServiceImpl implements ClarinLicenseResourceUserAllowanceService {

    @Autowired
    ClarinLicenseResourceUserAllowanceDAO clarinLicenseResourceUserAllowanceDAO;
    @Autowired
    ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    @Override
    public boolean verifyToken(Context context, String resourceID, String token) throws SQLException {
        List<ClarinLicenseResourceUserAllowance> clarinLicenseResourceUserAllowances =
                clarinLicenseResourceUserAllowanceDAO.findByTokenAndBitstreamId(context, resourceID, token);

        return CollectionUtils.isNotEmpty(clarinLicenseResourceUserAllowances);
    }

    @Override
    public boolean isUserAllowedToAccessTheResource(Context context, UUID userId, UUID resourceId) throws SQLException {
        ClarinLicense clarinLicenseToAgree =
                clarinLicenseResourceMappingService.getLicenseToAgree(context, userId, resourceId);

        // If the list is empty there are none licenses to agree -> the user is authorized.
        return Objects.isNull(clarinLicenseToAgree);
    }

    @Override
    public List<ClarinLicenseResourceUserAllowance> findByEPersonId(Context context, UUID userID) throws SQLException {
        return clarinLicenseResourceUserAllowanceDAO.findByEPersonId(context, userID);
    }
}
