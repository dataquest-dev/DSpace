package org.dspace.app.rest.repository;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.UUID;


@RestController
@RequestMapping("/api/s3/direct/download")
public class S3DownloadController {

    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(S3DownloadController.class);

    @Autowired
    private S3DirectDownloadService s3DirectDownload;

    @Autowired
    private BitstreamService bitstreamService;

    public S3DownloadController() { }

    @GetMapping("/{uuid}")
    public String getDownloadUrl(@PathVariable UUID uuid, HttpServletRequest request) throws AuthorizeException, SQLException {
        // Check permissions TODO


        Context context = ContextUtil.obtainContext(request);
        if (context == null) {
            log.error("No context for request");
            return null;
        }

        // Get bitstream
        Bitstream bitstream = bitstreamService.find(context, uuid);
        if (bitstream == null) {
            throw new IllegalArgumentException("Bitstream not found.");
        }

        // Construct object key
        String bucket = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("assetstore.s3.bucketName", "testbucket");
        String subfolder = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("assetstore.s3.subfolder", "eighty-eight");
//        String objectKey = subfolder + "/" + bitstream.getID() + "/" + bitstream.getName();

        // WARN - hardcoded
        // TODO - fetch assetstore path of the bitstream and use it as objectKey
        String objectKey = "10/27/80/102780081019350462616804225709692058487";
        // Generate pre-signed URL (1 hour expiration)
        return s3DirectDownload.generatePresignedUrl(bucket, objectKey, 3600);
    }
}