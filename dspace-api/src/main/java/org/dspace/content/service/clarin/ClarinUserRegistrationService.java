/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service.clarin;

import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.core.Context;

public interface ClarinUserRegistrationService {
    ClarinUserRegistration create(Context context) throws SQLException, AuthorizeException;
//    ClarinUserRegistration create(Context context, UUID id) throws SQLException, AuthorizeException;

    ClarinUserRegistration create(Context context,
          ClarinUserRegistration clarinUserRegistration) throws SQLException, AuthorizeException;

    ClarinUserRegistration find(Context context, int valueId) throws SQLException;
    void delete(Context context, ClarinUserRegistration clarinUserRegistration) throws SQLException, AuthorizeException;
    void update(Context context, ClarinUserRegistration clarinUserRegistration) throws SQLException, AuthorizeException;
}
