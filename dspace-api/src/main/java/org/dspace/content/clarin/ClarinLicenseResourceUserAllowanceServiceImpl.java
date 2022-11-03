package org.dspace.content.clarin;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ClarinLicenseResourceUserAllowanceServiceImpl implements ClarinLicenseResourceUserAllowanceService {
    private static final Logger log = LoggerFactory.getLogger(ClarinLicenseResourceUserAllowanceService.class);

    @Autowired
    AuthorizeService authorizeService;
    @Autowired
    ClarinLicenseResourceUserAllowanceDAO clarinLicenseResourceUserAllowanceDAO;
    @Autowired
    ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    @Override
    public ClarinLicenseResourceUserAllowance create(Context context) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin license resource user allowance");
        }
        // Create a table row
        ClarinLicenseResourceUserAllowance clarinLicenseResourceUserAllowance =
                clarinLicenseResourceUserAllowanceDAO.create(context,
                        new ClarinLicenseResourceUserAllowance());

        log.info(LogHelper.getHeader(context, "create_clarin_license_resource_user_allowance",
                "create_clarin_license_resource_user_allowance_id=" + clarinLicenseResourceUserAllowance.getID()));

        return clarinLicenseResourceUserAllowance;
    }

    @Override
    public ClarinLicenseResourceUserAllowance find(Context context, int valueId) throws SQLException {
        return clarinLicenseResourceUserAllowanceDAO.findByID(context,
                ClarinLicenseResourceUserAllowance.class, valueId);
    }

    @Override
    public void delete(Context context, ClarinLicenseResourceUserAllowance clarinLicenseResourceUserAllowance)
            throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin license resource user allowance");
        }
        clarinLicenseResourceUserAllowanceDAO.delete(context, clarinLicenseResourceUserAllowance);
    }

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
