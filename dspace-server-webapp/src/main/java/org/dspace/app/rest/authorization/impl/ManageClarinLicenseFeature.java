/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.authorization.impl;

import java.sql.SQLException;

import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.AuthorizationFeatureDocumentation;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The Manage Clarin License Feature. It can be used to verify if the current user can create/delete or update
 * the clarin license of an Item.
 *
 * Authorization is granted if the current user is admin/community admin or collection admin.
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
@Component
@AuthorizationFeatureDocumentation(name = ManageClarinLicenseFeature.NAME,
        description = "It can be used to verify if the user can create/delete or update the clarin license of an Item")
public class ManageClarinLicenseFeature implements AuthorizationFeature {

    public final static String NAME = "canManageClarinLicenses";

    @Autowired
    private AuthorizeService authorizeService;

    @Override
    public boolean isAuthorized(Context context, BaseObjectRest object) throws SQLException {
        if (!(object instanceof ItemRest)) {
            return false;
        }
        try {
            if (authorizeService.isAdmin(context) || authorizeService.isCommunityAdmin(context) ||
            authorizeService.isCollectionAdmin(context)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
                ItemRest.CATEGORY + "." + ItemRest.NAME
        };
    }
}
