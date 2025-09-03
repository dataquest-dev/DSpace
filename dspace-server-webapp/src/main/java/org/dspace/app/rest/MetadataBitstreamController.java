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
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CLARIN Controller for downloading individual bitstreams from Items.
 * This controller provides endpoints to download specific bitstreams by name
 * without creating ZIP archives, allowing users to download multiple files
 * separately instead of as a single compressed archive.
 *
 * @author DSpace Community
 */
@RestController
@RequestMapping("/api/" + ItemRest.CATEGORY + "/" + BitstreamRest.PLURAL_NAME)
public class MetadataBitstreamController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(MetadataBitstreamController.class);

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private HandleService handleService;

    @Autowired
    private AuthorizeService authorizeService;

    /**
     * Downloads a specific bitstream by name from an Item identified by its handle.
     * This method allows downloading individual files based on their exact names
     * without creating a ZIP archive.
     *
     * @param prefix The prefix part of the handle identifier (before the slash)
     * @param suffix The suffix part of the handle identifier (after the slash)
     * @param name The exact name of the bitstream to download
     * @param request The HTTP servlet request
     * @param response The HTTP servlet response where the bitstream content will be written
     * @throws SQLException if there is a database access error
     * @throws IOException if there is an I/O error during the download process
     * @throws AuthorizeException if the user does not have permission to read the Item
     * @throws UnprocessableEntityException if the handle does not resolve to a valid Item
     *                                     or if the bitstream with the specified name is not found
     */
    @GetMapping("/handle/{prefix}/{suffix}/{name:.+}")
    public void downloadBitstreamByName(
            @PathVariable String prefix,
            @PathVariable String suffix,
            @PathVariable String name,
            HttpServletRequest request,
            HttpServletResponse response) throws SQLException, IOException, AuthorizeException {

        final String handleId = prefix + "/" + suffix;
        Context context = ContextUtil.obtainContext(request);

        try {
            DSpaceObject dso = handleService.resolveToObject(context, handleId);

            if (Objects.isNull(dso)) {
                throw new UnprocessableEntityException("No DSpace object found for handle: " + handleId);
            }

            if (!(dso instanceof Item)) {
                throw new UnprocessableEntityException("The handle does not resolve to an Item: " + handleId);
            }

            Item item = (Item) dso;

            // Check READ permission on the actual Item object
            if (!authorizeService.authorizeActionBoolean(context, item, Constants.READ)) {
                throw new AuthorizeException("User does not have permission to read Item: " + item.getHandle());
            }

            Bitstream targetBitstream = findBitstreamByName(item, name);

            if (Objects.isNull(targetBitstream)) {
                throw new UnprocessableEntityException(
                        "No bitstream with name '" + name + "' found in Item " + item.getID());
            }

            // Set response headers for file download
            String mime = java.util.Optional.ofNullable(targetBitstream.getFormat(context))
                    .map(fmt -> fmt.getMIMEType())
                    .orElse("application/octet-stream");

            // Set content type without charset to match test expectations
            response.setHeader(HttpHeaders.CONTENT_TYPE, mime);

            org.springframework.http.ContentDisposition cd =
                    org.springframework.http.ContentDisposition.attachment()
                            .filename(targetBitstream.getName(), java.nio.charset.StandardCharsets.UTF_8)
                            .build();
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, cd.toString());

            // Stream the bitstream content to the response
            try (InputStream is = bitstreamService.retrieve(context, targetBitstream)) {
                streamBitstreamToResponse(is, response);
            } catch (AuthorizeException e) {
                log.error("Authorization error while retrieving bitstream: {}", targetBitstream.getName(), e);
                throw new AccessDeniedException(
                        "Access denied to bitstream: " + targetBitstream.getName(), e);
            }
        } finally {
            if (context != null) {
                try {
                    context.complete();
                } catch (SQLException e) {
                    log.error("Error completing DSpace context", e);
                }
            }
        }
    }

    /**
     * Finds a bitstream by name within an Item's ORIGINAL bundles.
     * This method searches through all ORIGINAL bundles of the item to locate
     * a bitstream with the exact matching name.
     *
     * @param item The Item to search for bitstreams
     * @param name The exact name of the bitstream to find
     * @return The matching Bitstream object, or null if not found
     */
    private Bitstream findBitstreamByName(Item item, String name) {
        for (Bundle bundle : item.getBundles(org.dspace.core.Constants.CONTENT_BUNDLE_NAME)) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                if (name.equals(bitstream.getName())) {
                    return bitstream;
                }
            }
        }
        return null;
    }

    /**
     * Streams bitstream content to the HTTP response output stream.
     * Uses a buffered approach for efficient streaming of large files.
     *
     * @param inputStream The input stream containing the bitstream data
     * @param response The HTTP response to write the data to
     * @throws IOException if an I/O error occurs during streaming
     */
    private void streamBitstreamToResponse(InputStream inputStream, HttpServletResponse response)
            throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            response.getOutputStream().write(buffer, 0, bytesRead);
        }
        response.getOutputStream().flush();
    }
}
