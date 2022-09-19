package org.dspace.content.service.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface ClarinLicenseResourceMappingService {

    ClarinLicenseResourceMapping create(Context context) throws SQLException, AuthorizeException;
    ClarinLicenseResourceMapping create(Context context, ClarinLicenseResourceMapping clarinLicenseResourceMapping) throws SQLException, AuthorizeException;
    ClarinLicenseResourceMapping create(Context context, Integer licenseId, UUID bitstreamUuid) throws SQLException, AuthorizeException;

    ClarinLicenseResourceMapping find(Context context, int valueId) throws SQLException;
    List<ClarinLicenseResourceMapping> findAllByLicenseId(Context context, Integer licenseId) throws SQLException;

    void update(Context context, ClarinLicenseResourceMapping newClarinLicenseResourceMapping) throws SQLException;

    void delete(Context context, ClarinLicenseResourceMapping clarinLicenseResourceMapping) throws SQLException;
}
