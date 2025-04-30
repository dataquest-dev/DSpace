/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.time.Instant;
import java.util.Date;
import javax.annotation.PostConstruct;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of the S3DirectDownloadService interface for generating presigned URLs for S3 downloads.
 * This implementation uses the AmazonS3 client provided by the S3BitStoreService.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class S3DirectDownloadServiceImpl implements S3DirectDownloadService {

    private static final Logger log = LoggerFactory.getLogger(S3DirectDownloadServiceImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private S3BitStoreService s3BitStoreService;

    private AmazonS3 s3Client;

    @PostConstruct
    private void init() {
        // Use the S3BitStoreService to get the AmazonS3 client - do not create a new one
        this.s3Client = s3BitStoreService.s3Service;
    }

    public String generatePresignedUrl(String bucket, String key, int expirationSeconds, String desiredFilename) {
        if (s3Client == null) {
            init();
        }
        Date expiration = Date.from(Instant.now().plusSeconds(expirationSeconds));
        // Create request
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
        // Add custom response header for filename - to download the file with the desired name
        String contentDisposition = "attachment; filename=\"" + desiredFilename + "\"";
        request.addRequestParameter("response-content-disposition", contentDisposition);
        return s3Client.generatePresignedUrl(request).toString();
    }
}
