/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Date;

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

    private void init() {
        // Use the S3BitStoreService to get the AmazonS3 client - do not create a new one
        this.s3Client = s3BitStoreService.s3Service;

        if (this.s3Client == null) {
            try {
                s3BitStoreService.init();
                this.s3Client = s3BitStoreService.s3Service;
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize S3 client from S3BitStoreService", e);
            }

            if (this.s3Client == null) {
                throw new IllegalStateException("S3 client was not initialized after calling init() " +
                        "on S3BitStoreService.");
            }
        }
    }

    public String generatePresignedUrl(String bucket, String key, int expirationSeconds, String desiredFilename) {
        if (desiredFilename == null) {
            log.error("Cannot generate presigned URL – desired filename is null");
            throw new IllegalArgumentException("Desired filename cannot be null");
        }
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
        request.addRequestParameter("response-content-disposition",
                buildContentDisposition(desiredFilename));
        try {
            return s3Client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for bucket: {}, key: {}", bucket, key, e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * Build the Content-Disposition value the way vanilla's HttpHeadersInitializer does: an ASCII
     * fallback in {@code filename} for clients that predate RFC 5987, plus the real UTF-8 name in
     * {@code filename*} for everyone else. S3 direct download has no upstream counterpart, so the
     * logic is copied from vanilla rather than shared.
     */
    private String buildContentDisposition(String name) {
        return String.format("attachment; filename=\"%s\"; filename*=UTF-8''%s",
                createFallbackAsciiName(name), createEncodedUtf8Name(name));
    }

    /**
     * Creates a safe ASCII-only fallback filename by removing diacritics (accents)
     * and replacing any remaining non-ASCII characters.
     * E.g., "ä-ö-é.pdf" becomes "a-o-e.pdf".
     * @param originalFilename The original filename.
     * @return A string containing only ASCII characters.
     */
    private String createFallbackAsciiName(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String normalized = Normalizer.normalize(originalFilename, Normalizer.Form.NFD);
        String withoutAccents = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Two deviations from vanilla, both of them behaviour this class already had: CR/LF are
        // dropped so a crafted name cannot inject a header, and \ and " are escaped so a name
        // containing a quote cannot close the quoted-string early.
        return withoutAccents.replaceAll("[^\\x00-\\x7F]", "")
                .replaceAll("[\\r\\n]", "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * Creates a percent-encoded UTF-8 filename according to RFC 5987.
     * This is for the `filename*` parameter.
     * E.g., "ä ö é.pdf" becomes "%C3%A4%20%C3%B6%20%C3%A9.pdf".
     * @param originalFilename The original filename.
     * @return A percent-encoded string.
     */
    private String createEncodedUtf8Name(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return URLEncoder.encode(originalFilename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
