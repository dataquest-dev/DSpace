/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Implementation of the S3DirectDownloadService interface for generating presigned URLs for S3 downloads.
 * This implementation reuses the S3 client provided by the S3BitStoreService and derives the presigner
 * from the same configuration.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class S3DirectDownloadServiceImpl implements S3DirectDownloadService {

    private static final Logger log = LoggerFactory.getLogger(S3DirectDownloadServiceImpl.class);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private S3BitStoreService s3BitStoreService;

    private S3AsyncClient s3Client;

    /**
     * Unlike the v1 SDK, presigning in v2 is done by a separate object rather than by the client itself.
     */
    private S3Presigner s3Presigner;

    private void init() {
        // Use the S3BitStoreService to get the S3 client - do not create a new one
        this.s3Client = s3BitStoreService.s3AsyncClient;

        if (this.s3Client == null) {
            try {
                s3BitStoreService.init();
                this.s3Client = s3BitStoreService.s3AsyncClient;
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize S3 client from S3BitStoreService", e);
            }

            if (this.s3Client == null) {
                throw new IllegalStateException("S3 client was not initialized after calling init() " +
                        "on S3BitStoreService.");
            }
        }

        if (this.s3Presigner == null) {
            this.s3Presigner = buildPresigner();
        }
    }

    /**
     * Build a presigner from the same credentials, region and endpoint the bitstore client uses, so that
     * the signed URL points at the same S3 provider the assets actually live on.
     */
    private S3Presigner buildPresigner() {
        S3Presigner.Builder builder = S3Presigner.builder();

        String accessKey = s3BitStoreService.getAwsAccessKey();
        String secretKey = s3BitStoreService.getAwsSecretKey();
        if (StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        // otherwise fall back to the default provider chain (IAM role / environment), as S3BitStoreService does

        // Mirror S3BitStoreService: an unset or unparseable region falls back to us-east-1
        Region region = Region.US_EAST_1;
        String regionName = s3BitStoreService.getAwsRegionName();
        if (StringUtils.isNotBlank(regionName)) {
            try {
                region = Region.of(regionName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid aws_region: {}", regionName);
            }
        }
        builder.region(region);

        String endpoint = s3BitStoreService.getEndpoint();
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(s3BitStoreService.getPathStyleAccessEnabled())
                    .build());
        }

        return builder.build();
    }

    /**
     * Whether the object is really there. A URL we cannot verify is never signed.
     */
    private boolean doesObjectExist(String bucket, String key) {
        try {
            s3Client.headObject(r -> r.bucket(bucket).key(key)).join();
            return true;
        } catch (Exception e) {
            log.debug("headObject(bucket={}, key={}) failed", bucket, key, e);
            return false;
        }
    }

    public String generatePresignedUrl(String bucket, String key, int expirationSeconds, String desiredFilename) {
        if (desiredFilename == null) {
            log.error("Cannot generate presigned URL – desired filename is null");
            throw new IllegalArgumentException("Desired filename cannot be null");
        }
        if (s3Client == null || s3Presigner == null) {
            init();
        }
        // Verify object exists before generating URL
        if (!doesObjectExist(bucket, key)) {
            log.error("Cannot generate presigned URL – object does not exist: bucket={}, key={}", bucket, key);
            throw new IllegalArgumentException("Requested S3 object does not exist");
        }

        // Add custom response header for filename - to download the file with the desired name
        // Remove CRLF and quotes to prevent header injection
        String safeName = desiredFilename.replaceAll("[\\r\\n\"]", "_");
        // RFC-5987: percent-encode UTF-8, e.g. filename*=UTF-8''%E2%82%ACrates.txt
        String encoded = URLEncoder.encode(desiredFilename, StandardCharsets.UTF_8);
        String contentDisposition = String.format(
                "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                safeName, encoded);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .responseContentDisposition(contentDisposition)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for bucket: {}, key: {}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
