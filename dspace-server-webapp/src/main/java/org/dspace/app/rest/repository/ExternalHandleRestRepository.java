/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;
import static org.dspace.core.Constants.COLLECTION;


@RestController
@RequestMapping("/api/services")
public class ExternalHandleRestRepository {

    private final String EXTERNAL_HANDLE_ENDPOINT_FIND_ALL = "handles/magic";
    private final String EXTERNAL_HANDLE_ENDPOINT_SHORTEN = "handles";
    private final String EXTERNAL_HANDLE_ENDPOINT_UPDATE = "handles";

    @Autowired
    private HandleClarinService handleClarinService;


    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_FIND_ALL, method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<List<Handle>> getHandles(HttpServletResponse response,
                                                   HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);

        List<org.dspace.handle.Handle> magicHandles = this.handleClarinService.findAllExternalHandles(context);

        // create the external handles from the handles with magic URL
        List<Handle> externalHandles = this.handleClarinService.convertHandleWithMagicToExternalHandle(magicHandles);

        return new ResponseEntity<>(new ArrayList<>(externalHandles), HttpStatus.OK);
    }


    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_SHORTEN, method = RequestMethod.POST,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<Handle> shortenHandle(@RequestBody Handle handle, HttpServletResponse response,
                                                   HttpServletRequest request) {
        Context context = ContextUtil.obtainContext(request);

        return new ResponseEntity<>(new Handle(), HttpStatus.OK);
    }


    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_UPDATE, method = RequestMethod.PUT,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<Handle> updateHandle(@RequestBody Handle handle, HttpServletResponse response,
                                                   HttpServletRequest request) {
        Context context = ContextUtil.obtainContext(request);

        return new ResponseEntity<>(new Handle(), HttpStatus.OK);
    }
}
