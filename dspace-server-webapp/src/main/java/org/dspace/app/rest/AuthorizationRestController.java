/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.authorization.AuthorizationBitstreamUtils;
import org.dspace.app.rest.authorization.AuthorizationRestUtil;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.AuthrnRest;
import org.dspace.app.rest.model.hateoas.AuthrnResource;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
    @Autowired
    private BitstreamService bitstreamService;

    @RequestMapping(method = RequestMethod.GET, value = "/{id}")
    public AuthrnResource authrn(@PathVariable String id, HttpServletResponse response, HttpServletRequest request)
            throws SQLException, AuthorizeException {
        // Validate path variable.
        if (StringUtils.isBlank(id)) {
            log.error("Bitstream's ID is blank");
            throw new BadRequestException("Bitstream's ID cannot be blank.");
        }

        // Load context object.
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot load context object");
        }

        // Load Bitstream by ID.
        Bitstream bitstream = bitstreamService.findByIdOrLegacyId(context, id);
        if (Objects.isNull(bitstream)) {
            throw new NotFoundException("Cannot find bitstream with id: " + id);
        }

        boolean isAuthorized = authorizationBitstreamUtils.authorizeBitstream(context, bitstream);

        // Based on the authorization result create the AuthrnRest object.
        AuthrnRest authrnRest = new AuthrnRest();
        authrnRest.setProjection(utils.obtainProjection());
        return converter.toResource(authrnRest);
    }
}
