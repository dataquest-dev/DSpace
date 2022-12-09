package org.dspace.content.dao.clarin;

import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.clarin.ClarinVerificationToken;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

import java.sql.SQLException;

public interface ClarinVerificationTokenDAO extends GenericDAO<ClarinVerificationToken> {

    ClarinVerificationToken findByToken(Context context, String token) throws SQLException;
    ClarinVerificationToken findByNetID(Context context, String netID) throws SQLException;
}
