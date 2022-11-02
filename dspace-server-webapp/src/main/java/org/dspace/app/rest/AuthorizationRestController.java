/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.dspace.app.rest.authorization.AuthorizationBitstreamUtils;
import org.dspace.app.rest.authorization.AuthorizationRestUtil;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.AuthnRest;
import org.dspace.app.rest.model.AuthrnRest;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.hateoas.AuthnResource;
import org.dspace.app.rest.model.hateoas.AuthrnResource;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;

@RequestMapping(value = "/api/" + AuthrnRest.CATEGORY)
@RestController
public class AuthorizationRestController {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRestController.class);

    @Autowired
    private Utils utils;

    @Autowired
    private ConverterService converter;

    @Autowired
    AuthorizationBitstreamUtils authorizationBitstreamUtils;

    @Autowired
    private AuthorizationRestUtil authorizationRestUtil;


    @RequestMapping(method = RequestMethod.GET)
    public AuthrnResource authn(HttpServletResponse response, HttpServletRequest request)
            throws SQLException, AuthorizeException {
        AuthrnRest authrnRest = new AuthrnRest();
        authrnRest.setProjection(utils.obtainProjection());

        Context context = ContextUtil.obtainContext(request);
        BaseObjectRest object = null;
        try {
            object = authorizationRestUtil.getObject(context, id);
        } catch (IllegalArgumentException e) {
            log.warn("Object informations not found in the specified id " + id, e);
            return null;
        }

        authorizationBitstreamUtils.authorizeBitstream(context, object);
        return converter.toResource(authrnRest);
    }
}
