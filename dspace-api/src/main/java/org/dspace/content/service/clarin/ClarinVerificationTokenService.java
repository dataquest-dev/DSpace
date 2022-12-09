package org.dspace.content.service.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.clarin.ClarinVerificationToken;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinVerificationTokenService {

    ClarinVerificationToken create(Context context) throws SQLException, AuthorizeException;
    ClarinVerificationToken create(Context context, ClarinVerificationToken clarinVerificationToken)
            throws SQLException, AuthorizeException;

    ClarinVerificationToken find(Context context, int valueId) throws SQLException;
    List<ClarinVerificationToken> findAll(Context context) throws SQLException, AuthorizeException;
    ClarinVerificationToken findByToken(Context context, String token) throws SQLException;
    ClarinVerificationToken findByNetID(Context context, String netID) throws SQLException;
    void delete(Context context, ClarinVerificationToken clarinUserRegistration)
            throws SQLException;
    void update(Context context, ClarinVerificationToken newClarinVerificationToken) throws SQLException;
}
