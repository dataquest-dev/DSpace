/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;


import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.model.MetadataBitstreamWrapper;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.MissingLicenseAgreementException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.*;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.dspace.core.Constants;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/bitstream")
public class MetadataBitstreamController {

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    HandleService handleService;
    @Autowired
    private AuthorizeService authorizeService;
    @GetMapping("/handle/{id}/{subId}/{fileName}")
    public ResponseEntity<Resource> downloadSingleFile(
            @PathVariable("id") String id,
            @PathVariable("subId") String subId,
            @PathVariable("fileName") String fileName,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String handleID = id + "/" + subId;
        if (StringUtils.isBlank(handleID)) {
            throw new DSpaceBadRequestException("handle cannot be null!");
        }
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        DSpaceObject dso = null;

        try{
            dso = handleService.resolveToObject(context, handleID);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve handle: " + handleID);
        }

        if (dso != null && dso instanceof Item) {
            Item item = (Item) dso;
            List<Bundle> bundles = item.getBundles();
            for (Bundle bundle:
                 bundles) {
                for (Bitstream bitstream:
                     bundle.getBitstreams()) {
                    try {
                        authorizeService.authorizeAction(context, bitstream, Constants.READ);
                    } catch (MissingLicenseAgreementException e) {
                        response.sendRedirect("http://localhost:4000/bitstream/" + bitstream.getID() + "/download");
                    } catch (AuthorizeException e) {
                        response.sendRedirect("http://localhost:4000" + "/login");
                    } catch (SQLException e) {
                        response.sendRedirect("http://localhost:4000" + "/login");
                    }
                    String btName = bitstream.getName();
                    if (btName.equalsIgnoreCase(fileName)) {
                        try {
                            BitstreamFormat bitstreamFormat = bitstream.getFormat(context);
                            if (bitstreamFormat == null || bitstreamFormat.getExtensions() == null || bitstreamFormat.getExtensions().size() == 0) {
//                                throw new RuntimeException("Cannot find the bitstream format.");
                            }
                            InputStream inputStream = bitstreamService.retrieve(context, bitstream);
                            InputStreamResource resource = new InputStreamResource(inputStream);
                            HttpHeaders header = new HttpHeaders();
                            header.add(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=" + fileName);
//                                    "attachment; filename=" + fileName +".pdf");
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
            }
        }

        return null;
    }


    @GetMapping("/allzip")
    public void downloadFileZip(@RequestParam("handleId") String handleId,
                                HttpServletResponse response,
                                HttpServletRequest request) throws IOException, SQLException, AuthorizeException {
        //TODO handle authorization
        if (StringUtils.isBlank(handleId)) {
            throw new DSpaceBadRequestException("handle cannot be null!");
        }
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        DSpaceObject dso = null;
        String name = "";
        try{
            dso = handleService.resolveToObject(context, handleId);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve handle: " + handleId);
        }

        if (dso != null && dso instanceof Item) {
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
                    try {
                        authorizeService.authorizeAction(context, bitstream, Constants.READ);
                    } catch (AuthorizeException e) {
                        response.sendRedirect("http://localhost:4000" + "/login");
                    } catch (SQLException e) {
                        response.sendRedirect("http://localhost:4000" + "/login");
                    }
                    String filename = bitstream.getName();
                    ZipArchiveEntry ze = new ZipArchiveEntry(filename);
                    zip.putArchiveEntry(ze);
                    InputStream is = bitstreamService.retrieve(context, bitstream);
                    IOUtils.copy(is, zip);
                    zip.closeArchiveEntry();
                    is.close();
                }
            }
            zip.close();
            response.getOutputStream().flush();
        }
    }
}
