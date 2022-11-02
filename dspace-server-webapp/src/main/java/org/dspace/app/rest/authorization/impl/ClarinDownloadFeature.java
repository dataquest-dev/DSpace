package org.dspace.app.rest.authorization.impl;

import org.checkerframework.checker.units.qual.A;
import org.dspace.app.rest.authorization.AuthorizationBitstreamUtils;
import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.AuthorizationFeatureDocumentation;
import org.dspace.app.rest.authorization.AuthorizeServiceRestUtil;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.security.DSpaceRestPermission;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * The download bitstream feature. It can be used to verify if a bitstream can be downloaded.
 *
 * Authorization is granted if the current user has READ permissions on the given bitstream.
 * Inspired by DownloadFeature
 */
@Component
@AuthorizationFeatureDocumentation(name = ClarinDownloadFeature.NAME,
        description = "It can be used to verify if the user can download a bitstream")
public class ClarinDownloadFeature implements AuthorizationFeature {
    public final static String NAME = "canDownloadClarin";

    @Autowired
    private AuthorizeServiceRestUtil authorizeServiceRestUtil;

    @Autowired
    private ClarinLicenseService clarinLicenseService;

    @Autowired
    private AuthorizationBitstreamUtils authorizationBitstreamUtils;

    @Override
    public boolean isAuthorized(Context context, BaseObjectRest object) throws SQLException, AuthorizeException {
        if (!(object instanceof BitstreamRest)) {
            return false;
        }

        boolean defaultAuth =
                authorizeServiceRestUtil.authorizeActionBoolean(context, object, DSpaceRestPermission.READ);
        if (!defaultAuth) {
            return false;
        }


        // This function throws exception if the authorization fails - if it is not reported,
        // the license restrictions are OK
        authorizationBitstreamUtils.authorizeBitstream(context, object);
        return false;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
                BitstreamRest.CATEGORY + "." + BitstreamRest.NAME,
        };
    }
}
