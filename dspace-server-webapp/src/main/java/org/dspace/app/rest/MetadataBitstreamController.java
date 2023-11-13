 /**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.Deflater;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizationBitstreamUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.RequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

 /**
 * This CLARIN Controller download a single file or a ZIP file from the Item's bitstream.
 */
@RestController
@RequestMapping("/api/" + ItemRest.CATEGORY + "/" + ItemRest.PLURAL_NAME + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
public class MetadataBitstreamController {

    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataBitstreamController.class);

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    private HandleService handleService;
    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    AuthorizationBitstreamUtils authorizationBitstreamUtils;
    @Autowired
    private RequestService requestService;


    @GetMapping("/handle/{id}/{subId}/{fileName}")
    public ResponseEntity<Resource> downloadSingleFile(@PathVariable("id") String id,
                                                       @PathVariable("subId") String subId,
                                                       @PathVariable("fileName") String fileName,
                                                       HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException, AuthorizeException {
        String handleID = id + "/" + subId;
        if (StringUtils.isBlank(id) || StringUtils.isBlank(subId)) {
            log.error("Handle cannot be null! PathVariable `id` or `subId` is null.");
            throw new DSpaceBadRequestException("Handle cannot be null!");
        }

        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            log.error("Cannot obtain the context from the request.");
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        DSpaceObject dso = null;
        try {
            dso = handleService.resolveToObject(context, handleID);
        } catch (Exception e) {
            log.error("Cannot resolve handle: " + handleID);
            throw new RuntimeException("Cannot resolve handle: " + handleID);
        }


        if (Objects.isNull(dso)) {
            log.error("DSO is null");
            return null;
        }

        if (!(dso instanceof Item)) {
            log.error("DSO is not instance of Item");
            return null;
        }

        Item item = (Item) dso;
        List<Bundle> bundles = item.getBundles();
        // Find bitstream and start downloading.
        for (Bundle bundle: bundles) {
            for (Bitstream bitstream: bundle.getBitstreams()) {

//                authorizeBitstreamAction(context, bitstream, response);

                String btName = bitstream.getName();
                if (!(btName.equalsIgnoreCase(fileName))) {
                    continue;
                }
                try {
                    BitstreamFormat bitstreamFormat = bitstream.getFormat(context);
                    // Check if the bitstream has some extensions e.g., `.txt, .jpg,..`
                    checkBitstreamExtensions(bitstreamFormat);

                    // Get content of the bitstream
                    // Retrieve method authorize bitstream download action.
                    InputStream inputStream = bitstreamService.retrieve(context, bitstream);
                    InputStreamResource resource = new InputStreamResource(inputStream);
                    HttpHeaders header = new HttpHeaders();
                    header.add(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=" + fileName);
                    header.add("Cache-Control", "no-cache, no-store, must-revalidate");
                    header.add("Pragma", "no-cache");
                    header.add("Expires", "0");
                    return ResponseEntity.ok()
                            .headers(header)
                            .contentLength(inputStream.available())
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return null;
    }

    /**
     * Download all Item's bitstreams as single ZIP file.
     */
    @PreAuthorize("hasPermission(#uuid, 'ITEM', 'READ')")
    @RequestMapping( method = {RequestMethod.GET, RequestMethod.HEAD}, value = "allzip")
    public void downloadFileZip(@PathVariable UUID uuid, @RequestParam("handleId") String handleId,
                                HttpServletResponse response,
                                HttpServletRequest request) throws IOException, SQLException, AuthorizeException {
        if (StringUtils.isBlank(handleId)) {
            log.error("Handle cannot be null!");
            throw new DSpaceBadRequestException("Handle cannot be null!");
        }
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            log.error("Cannot obtain the context from the request.");
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        DSpaceObject dso = null;
        String name = "";
        try {
            dso = handleService.resolveToObject(context, handleId);
        } catch (Exception e) {
            log.error("Cannot resolve handle: " + handleId);
            throw new RuntimeException("Cannot resolve handle: " + handleId);
        }

        if (Objects.isNull(dso)) {
            log.error("DSO is null");
            throw new UnprocessableEntityException("Retrieved DSO is null, handle: " + handleId);
        }

        if (!(dso instanceof Item)) {
            log.info("DSO is not instance of Item");
        }

        Item item = (Item) dso;
        name = item.getName() + ".zip";
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment;filename=\"%s\"", name));
        response.setContentType("application/zip");
        List<Bundle> bundles = item.getBundles("ORIGINAL");

        ZipArchiveOutputStream zip = new ZipArchiveOutputStream(response.getOutputStream());
        zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
        zip.setLevel(Deflater.NO_COMPRESSION);
        for (Bundle original : bundles) {
            List<Bitstream> bss = original.getBitstreams();
            for (Bitstream bitstream : bss) {
                String filename = bitstream.getName();
                ZipArchiveEntry ze = new ZipArchiveEntry(filename);
                zip.putArchiveEntry(ze);
                // Get content of the bitstream
                // Retrieve method authorize bitstream download action.
                InputStream is = bitstreamService.retrieve(context, bitstream);
                IOUtils.copy(is, zip);
                zip.closeArchiveEntry();
                is.close();
            }
        }
        zip.close();
        response.getOutputStream().flush();
    }


    /**
     * Check if the bitstream has file extension.
     */
    private void checkBitstreamExtensions(BitstreamFormat bitstreamFormat) {
        if ( Objects.isNull(bitstreamFormat) ||  CollectionUtils.isEmpty(bitstreamFormat.getExtensions())) {
            log.error("Bitstream Extensions cannot be empty for downloading/previewing bitstreams.");
            throw new RuntimeException("Bitstream Extensions cannot be empty for downloading/previewing bitstreams.");
        }
    }
}
