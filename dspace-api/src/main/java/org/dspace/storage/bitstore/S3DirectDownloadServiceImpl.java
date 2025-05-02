/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        try {
            if (this.s3Client == null) {
                this.s3BitStoreService.init();
                this.s3Client = s3BitStoreService.s3Service;
                if (s3Client == null) {
                    throw new RuntimeException("S3 client wasn't initialized in the S3BitStoreService.");
                }
            }
        } catch(Exception e){
            throw new RuntimeException("Cannot initialize the s3Client because:", e);
        }
    }

    public String generatePresignedUrl(String bucket, String key, int expirationSeconds, String desiredFilename) {
        if (s3Client == null) {
            init();
        }
        // Verify object exists before generating URL
        if (!s3Client.doesObjectExist(bucket, key)) {
            log.error("Cannot generate presigned URL – object does not exist: bucket={}, key={}", bucket, key);
            throw new IllegalArgumentException("Requested S3 object does not exist");
        }

        Date expiration = Date.from(Instant.now().plusSeconds(expirationSeconds));
        // Create request
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
        // Add custom response header for filename - to download the file with the desired name
        // Remove CRLF and quotes to prevent header injection
        String safeName = desiredFilename.replaceAll("[\\r\\n\"]", "_");
        // RFC-5987: percent-encode UTF-8, e.g. filename*=UTF-8''%E2%82%ACrates.txt
        String encoded = URLEncoder.encode(desiredFilename, StandardCharsets.UTF_8);
        String contentDisposition = String.format(
                "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                safeName, encoded);

        request.addRequestParameter("response-content-disposition", contentDisposition);
        try {
            return s3Client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for bucket: {}, key: {}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
