package org.dspace.content.service.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;

public interface ClarinLicenseLabelService {
    ClarinLicenseLabel create(Context context) throws SQLException, AuthorizeException;

    ClarinLicenseLabel create(Context context, ClarinLicenseLabel clarinLicenseLabel) throws SQLException,
            AuthorizeException;

    ClarinLicenseLabel find(Context context, int valueId) throws SQLException;

    List<ClarinLicenseLabel> findAll(Context context) throws SQLException;

    void delete(Context context, ClarinLicenseLabel clarinLicenseLabel) throws SQLException;

    void update(Context context, ClarinLicenseLabel newClarinLicenseLabel) throws SQLException;
}
