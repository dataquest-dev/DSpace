/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller provides a direct download endpoint for bitstreams
 * identified by an Item handle and the bitstream filename.
 *
 * Endpoint: GET /api/core/bitstreams/handle/{prefix}/{suffix}/{filename}
 *
 * This is used by the command-line download instructions (curl commands)
 * shown on the item page in the UI.
 */
@RestController
@RequestMapping("/api/" + BitstreamRest.CATEGORY + "/" + BitstreamRest.PLURAL_NAME + "/handle")
public class BitstreamByHandleRestController {

    private static final Logger log =
            org.apache.logging.log4j.LogManager.getLogger(BitstreamByHandleRestController.class);

    private static final int BUFFER_SIZE = 4096 * 10;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private HandleService handleService;

    @Autowired
    private AuthorizeService authorizeService;

    /**
     * Download a bitstream by item handle and filename.
     *
     * @param prefix   the handle prefix (e.g. "11234")
     * @param suffix   the handle suffix (e.g. "1-5814")
     * @param filename the bitstream filename (e.g. "pdtvallex-4.5.xml")
     * @param request  the HTTP request
     * @param response the HTTP response
     */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD},
                    value = "/{prefix}/{suffix}/{filename:.+}")
    public void downloadBitstreamByHandle(@PathVariable String prefix,
                                          @PathVariable String suffix,
                                          @PathVariable String filename,
                                          HttpServletRequest request,
                                          HttpServletResponse response) throws IOException {
        String handle = prefix + "/" + suffix;

        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            log.error("Cannot obtain the context from the request.");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Cannot obtain the context from the request.");
            return;
        }

        try {
            // Resolve handle to DSpaceObject
            DSpaceObject dso = handleService.resolveToObject(context, handle);
            if (Objects.isNull(dso) || !(dso instanceof Item)) {
                log.warn("Handle '{}' does not resolve to a valid Item.", handle);
                response.sendError(HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        "Handle '" + handle + "' does not resolve to a valid item.");
                return;
            }

            Item item = (Item) dso;
            Bitstream bitstream = findBitstreamByName(item, filename);

            if (bitstream == null) {
                log.warn("No bitstream with name '{}' found for handle '{}'.", filename, handle);
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Bitstream '" + filename + "' not found for handle '" + handle + "'.");
                return;
            }

            // Check authorization
            authorizeService.authorizeAction(context, bitstream, Constants.READ);

            // Retrieve content and stream it
            BitstreamFormat format = bitstream.getFormat(context);
            String mimeType = (format != null) ? format.getMIMEType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            String name = StringUtils.isNotBlank(bitstream.getName())
                    ? bitstream.getName() : bitstream.getID().toString();

            response.setContentType(mimeType);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    String.format("attachment; filename=\"%s\"", name));
            long size = bitstream.getSizeBytes();
            if (size > 0) {
                response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(size));
            }

            if (RequestMethod.HEAD.name().equals(request.getMethod())) {
                // HEAD request — only headers, no body
                return;
            }

            try (InputStream is = bitstreamService.retrieve(context, bitstream)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
                response.getOutputStream().flush();
            }
        } catch (AuthorizeException e) {
            log.warn("Unauthorized access to bitstream '{}' for handle '{}'.", filename, handle);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "You are not authorized to download this file.");
        } catch (SQLException e) {
            log.error("Database error while downloading bitstream '{}' for handle '{}': {}",
                    filename, handle, e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred.");
        } finally {
            if (context != null && context.isValid()) {
                try {
                    context.complete();
                } catch (SQLException e) {
                    log.error("Error completing context: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Find a bitstream by name in the ORIGINAL bundles of an item.
     */
    private Bitstream findBitstreamByName(Item item, String filename) {
        List<Bundle> bundles = item.getBundles("ORIGINAL");
        for (Bundle bundle : bundles) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                if (StringUtils.equals(bitstream.getName(), filename)) {
                    return bitstream;
                }
            }
        }
        return null;
    }
}
