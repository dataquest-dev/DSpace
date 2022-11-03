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
import org.dspace.content.dao.clarin.ClarinUserRegistrationDAO;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ClarinUserRegistrationServiceImpl implements ClarinUserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ClarinUserRegistrationService.class);

    @Autowired
    AuthorizeService authorizeService;
    @Autowired
    ClarinUserRegistrationDAO clarinUserRegistrationDAO;

    @Override
    public ClarinUserRegistration create(Context context) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create a Clarin user registration");
        }
        // Create a table row
        ClarinUserRegistration clarinUserRegistration = clarinUserRegistrationDAO.create(context,
                new ClarinUserRegistration());

        log.info(LogHelper.getHeader(context, "create_clarin_user_registration",
                "clarin_user_registration_id=" + clarinUserRegistration.getID()));

        return clarinUserRegistration;
    }

    @Override
    public ClarinUserRegistration create(Context context,
           ClarinUserRegistration clarinUserRegistration) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create a Clarin user registration");
        }

        return clarinUserRegistrationDAO.create(context, clarinUserRegistration);
    }

    @Override
    public ClarinUserRegistration find(Context context, int valueId) throws SQLException {
        return clarinUserRegistrationDAO.findByID(context, ClarinUserRegistration.class, valueId);
    }

    @Override
    public void delete(Context context, ClarinUserRegistration clarinUserRegistration)
            throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an Clarin user registration");
        }
        clarinUserRegistrationDAO.delete(context, clarinUserRegistration);
    }
}
