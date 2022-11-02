/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.AuthnRest;
import org.dspace.app.rest.model.AuthrnRest;
import org.dspace.app.rest.model.hateoas.AuthnResource;
import org.dspace.app.rest.model.hateoas.AuthrnResource;
import org.dspace.app.rest.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "/api/" + AuthrnRest.CATEGORY)
@RestController
public class AuthorizationRestController {

    @Autowired
    private Utils utils;

    @Autowired
    private ConverterService converter;

    @RequestMapping(method = RequestMethod.GET)
    public AuthrnResource authn() {
        AuthrnRest authrnRest = new AuthrnRest();
        authrnRest.setProjection(utils.obtainProjection());
        return converter.toResource(authrnRest);
    }
}
