package org.dspace.content.clarin;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

public class ClarinLicenseResourceUserAllowanceServiceImpl implements ClarinLicenseResourceUserAllowanceService {

    @Autowired
    ClarinLicenseResourceUserAllowanceDAO clarinLicenseResourceUserAllowanceDAO;
    @Autowired
    ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    @Override
    public boolean verifyToken(String resourceID, String token) {
        return clarinLicenseResourceUserAllowanceDAO.verifyToken(resourceID, token);
    }

    @Override
    public boolean isUserAllowedToAccessTheResource(UUID userId, String resourceId) {
        List<ClarinLicense> clarinLicenseList =
                clarinLicenseResourceMappingService.getLicensesToAgree(userId, resourceId);

        // If the list is empty there are none licenses to agree -> the user is authorized.
        return CollectionUtils.isEmpty(clarinLicenseList);
    }
}
