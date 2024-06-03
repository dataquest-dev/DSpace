/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.VersionInfoRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.clarin.VersionInfo;
import org.dspace.content.service.clarin.VersionInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller provides an endpoint for retrieving the version information of the DSpace instance.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
@RequestMapping(value = "/api/versioninfo")
@RestController
public class VersionInfoController {

    @Autowired
    private ConverterService converter;

    @Autowired
    private Utils utils;

    @Autowired
    private VersionInfoService versionInfoService;

    @RequestMapping(method = RequestMethod.GET)
    public VersionInfoRest getVersionInfo(HttpServletRequest request, HttpServletResponse response) {
        // Fetch the version info data from the file and wrap it into Java object.
        VersionInfo versionInfo = versionInfoService.fetchVersionInfoFromFile();

        Projection projection = utils.obtainProjection();
        // Convert VersionInfo into VersionInfoRest object using VersionInfoConverter.
        return converter.toRest(versionInfo, projection);
    }
}
