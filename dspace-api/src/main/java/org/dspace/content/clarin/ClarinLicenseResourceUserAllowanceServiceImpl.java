/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.clarin;

import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.dao.clarin.ClarinLicenseResourceUserAllowanceDAO;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ClarinLicenseResourceUserAllowanceServiceImpl implements ClarinLicenseResourceUserAllowanceService {
    private static final Logger log = LoggerFactory.getLogger(ClarinLicenseResourceUserAllowanceService.class);

    @Autowired
    AuthorizeService authorizeService;
    @Autowired
    ClarinLicenseResourceUserAllowanceDAO clarinLicenseResourceUserAllowanceDAO;

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
}
