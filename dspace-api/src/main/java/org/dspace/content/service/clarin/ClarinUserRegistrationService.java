package org.dspace.content.service.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.core.Context;

import java.sql.SQLException;

public interface ClarinUserRegistrationService {
    ClarinUserRegistration create(Context context) throws SQLException, AuthorizeException;

    ClarinUserRegistration find(Context context, int valueId) throws SQLException;
    void delete(Context context, ClarinUserRegistration clarinUserRegistration) throws SQLException, AuthorizeException;
}
