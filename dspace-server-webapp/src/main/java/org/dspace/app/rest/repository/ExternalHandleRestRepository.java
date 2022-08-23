/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.RandomStringUtils;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.DCDate;
import org.dspace.core.Context;
import org.dspace.handle.HandlePlugin;
import org.dspace.handle.dao.HandleDAO;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.dspace.handle.external.ExternalHandleConstants.MAGIC_BEAN;


@RestController
@RequestMapping("/api/services")
public class ExternalHandleRestRepository {

    private final String EXTERNAL_HANDLE_ENDPOINT_FIND_ALL = "handles/magic";
    private final String EXTERNAL_HANDLE_ENDPOINT_SHORTEN = "handles";
    private final String EXTERNAL_HANDLE_ENDPOINT_UPDATE = "handles";

    @Autowired
    private HandleClarinService handleClarinService;

    @Autowired
    private ConfigurationService configurationService;


    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_FIND_ALL, method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<List<Handle>> getHandles(HttpServletResponse response,
                                                   HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);

        List<org.dspace.handle.Handle> magicHandles = this.handleClarinService.findAllExternalHandles(context);

        // create the external handles from the handles with magic URL
        List<Handle> externalHandles = this.handleClarinService.convertHandleWithMagicToExternalHandle(magicHandles);

        ResponseEntity<List<Handle>> responsee = new ResponseEntity<>(new ArrayList<>(externalHandles), HttpStatus.OK);
        return responsee;
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

        if(validHandleUrl(handle.url) && isNotBlank(handle.title) && isNotBlank(handle.reportemail)){
            try {
                context = new org.dspace.core.Context();
                String submitdate = new DCDate(new Date()).toString();
                handle.submitdate = submitdate;
                String subprefix = (isNotBlank(handle.subprefix)) ? handle.subprefix + "-" : "";
                String magicURL = handle.getMagicUrl();
                String hdl = createHandle(subprefix, magicURL, context);
                context.complete();
                return new Handle(hdl, magicURL);
            }catch (SQLException e){
                processException("Could not create handle, SQLException. Message: " + e.getMessage(), context);
            }
        }
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(
                configurationService.getPropertyAsType("lr.shortener.post.error",
                        "Invalid handle values")).build());

        return new ResponseEntity<>(new Handle(), HttpStatus.OK);
    }

    private String createHandle(String subprefix, String url, Context context) throws SQLException {
        Handle handle = handleDAO.create(context, new Handle());
        org.dspace.handle.Handle newHandle = handleDAO.create(context, new Handle());

        String handle;
        TableRowIterator tri;
        String query = "select * from handle where handle like ? ;";
        while(true){
            String rnd = RandomStringUtils.random(4,true,true).toUpperCase();
            handle = prefix + "/" + subprefix + rnd;
            tri = DatabaseManager.query(context, query, handle);
            if(!tri.hasNext()){
                //no row matches stop generation;
                break;
            }
        }
        TableRow row = DatabaseManager.row("handle");
        row.setColumn("handle", handle);
        row.setColumn("url", url);
        DatabaseManager.insert(context, row);
        return handle;
    }

    private boolean validHandleUrl(String url){
        if(isBlank(url)){
            return false;
        }
        if(url.contains(MAGIC_BEAN)){
            return false;
        }
        try {
            final URL url_o = new URL(url);
            final String host = url_o.getHost();
            //whitelist host
            if(matchesAnyOf(host, "lr.shortener.post.host.whitelist.regexps")){
                return true;
            }
            //blacklist url
            if(matchesAnyOf(url, "lr.shortener.post.url.blacklist.regexps")){
                return false;
            }
            //blacklist host
            if(matchesAnyOf(host, "lr.shortener.post.host.blacklist.regexps")){
                return false;
            }
        }catch (MalformedURLException e){
            return false;
        }
        return true;
    }

    private boolean matchesAnyOf(String tested, String configPropertyWithPatterns){
        final String patterns = this.configurationService.getProperty(configPropertyWithPatterns);
        String[] list = patterns.split(";");
        for(String regexp : list){
            if(tested.matches(regexp.trim())){
                return true;
            }
        }
        return false;
    }
}
