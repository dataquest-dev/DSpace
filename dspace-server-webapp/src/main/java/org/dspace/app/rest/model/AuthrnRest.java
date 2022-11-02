/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import org.dspace.app.rest.AuthorizationRestController;

public class AuthrnRest extends BaseObjectRest<Integer> {

    public static final String NAME = "authrn";
    public static final String CATEGORY = "authrns";

    public String getCategory() {
        return CATEGORY;
    }

    public String getType() {
        return NAME;
    }

    public Class getController() {
        return AuthorizationRestController.class;
    }
}
