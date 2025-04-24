package org.dspace.app.rest.repository;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.S3DirectDownloadDTO;
import org.dspace.app.rest.model.S3DirectDownloadDTORest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import java.sql.SQLException;
import java.util.UUID;


@RestController
@RequestMapping("/api/s3/direct/download")
public class S3DownloadController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(S3DownloadController.class);

    @Autowired
    private ConverterService converterService;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    private S3DirectDownloadService s3DirectDownload;

    @Autowired
    private BitstreamService bitstreamService;

    @Autowired
    protected Utils utils;

    public S3DownloadController() { }

    @GetMapping("/{uuid}")
    @PreAuthorize("permitAll()")
    public S3DirectDownloadDTORest getDownloadUrl(@PathVariable UUID uuid, HttpServletRequest request) throws AuthorizeException, SQLException {
        Context context = ContextUtil.obtainContext(request);
        if (context == null) {
            log.error("No context for request");
            throw new BadRequestException("The context is null for this request");
        }

        // Get bitstream
        Bitstream bitstream = bitstreamService.find(context, uuid);
        if (bitstream == null) {
            throw new IllegalArgumentException("Bitstream not found.");
        }

        // Construct object key
        String bucket = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("assetstore.s3.bucketName");
        String subfolder = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("assetstore.s3.subfolder");
//        String objectKey = subfolder + "/" + bitstream.getID() + "/" + bitstream.getName();

        // WARN - hardcoded
        // TODO - fetch assetstore path of the bitstream and use it as objectKey
        String objectKey = "eighty-eight/15/23/63/152363485915157744195536472411491704057";

        // Generate pre-signed URL (1 hour expiration)
        String presignedUrl = s3DirectDownload.generatePresignedUrl(bucket, objectKey, 3600);
        String name = bitstream.getName();

        S3DirectDownloadDTO s3DirectDownloadDTO = new S3DirectDownloadDTO();
        s3DirectDownloadDTO.setBitstreamName(name);
        s3DirectDownloadDTO.setUrl(presignedUrl);

        return converterService.toRest(s3DirectDownloadDTO, utils.obtainProjection());
    }
}
