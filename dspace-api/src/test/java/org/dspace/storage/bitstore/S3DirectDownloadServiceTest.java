/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.dspace.AbstractDSpaceTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Tests that {@link S3DirectDownloadServiceImpl} signs a working GET URL with the AWS SDK v2 presigner.
 * Presigning is a local computation, so these tests need no S3 endpoint.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class S3DirectDownloadServiceTest extends AbstractDSpaceTest {

    private static final String ENDPOINT = "http://s3.example.test:9000";
    private static final String BUCKET = "dspace-assetstore";
    private static final String KEY = "12/34/56/123456789";
    private static final int EXPIRATION_SECONDS = 120;
    private static final int SEVEN_DAYS = 7 * 24 * 60 * 60;

    @Mock
    private S3BitStoreService s3BitStoreService;

    private S3DirectDownloadServiceImpl s3DirectDownloadService;

    @Before
    public void setUp() {
        when(s3BitStoreService.getAwsAccessKey()).thenReturn("test-access-key");
        when(s3BitStoreService.getAwsSecretKey()).thenReturn("test-secret-key");
        when(s3BitStoreService.getAwsRegionName()).thenReturn("eu-central-1");
        when(s3BitStoreService.getEndpoint()).thenReturn(ENDPOINT);

        s3DirectDownloadService = new S3DirectDownloadServiceImpl();
        s3DirectDownloadService.setS3BitStoreService(s3BitStoreService);
    }

    @Test
    public void generatePresignedUrl() {
        URI url = URI.create(
                s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, EXPIRATION_SECONDS, "myfile.txt"));

        assertEquals("s3.example.test", url.getHost());
        assertEquals(9000, url.getPort());
        assertEquals("/" + BUCKET + "/" + KEY, url.getPath());

        Map<String, String> query = queryOf(url);
        assertEquals("AWS4-HMAC-SHA256", query.get("X-Amz-Algorithm"));
        assertEquals(String.valueOf(EXPIRATION_SECONDS), query.get("X-Amz-Expires"));
        assertTrue(query.get("X-Amz-Credential").contains("eu-central-1"));
        assertFalse(query.get("X-Amz-Signature").isEmpty());
        assertEquals("attachment; filename=\"myfile.txt\"; filename*=UTF-8''myfile.txt",
                query.get("response-content-disposition"));
    }

    @Test
    public void expirationIsTheSignatureDuration() {
        URI url = URI.create(s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, 3600, "myfile.txt"));

        assertEquals("3600", queryOf(url).get("X-Amz-Expires"));
    }

    @Test
    public void nonAsciiFilenameKeepsBothDispositionForms() {
        URI url = URI.create(
                s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, EXPIRATION_SECONDS, "rates €.txt"));

        assertEquals("attachment; filename=\"rates €.txt\"; filename*=UTF-8''rates%20%E2%82%AC.txt",
                queryOf(url).get("response-content-disposition"));
    }

    @Test
    public void filenameCannotCloseTheDispositionHeader() {
        URI url = URI.create(
                s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, EXPIRATION_SECONDS,
                        "../secret\nname\".txt"));

        String disposition = queryOf(url).get("response-content-disposition");
        String fallbackName = disposition.split("filename=\"")[1].split("\"")[0];
        assertFalse(fallbackName.contains("\n"));
        assertFalse(fallbackName.contains("\""));
        assertTrue(fallbackName.contains("../"));
        assertTrue(disposition.contains("filename*=UTF-8''"));
    }

    @Test
    public void signsUpToSevenDaysAndNoFurther() {
        URI url = URI.create(s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, SEVEN_DAYS, "myfile.txt"));
        assertEquals(String.valueOf(SEVEN_DAYS), queryOf(url).get("X-Amz-Expires"));

        // SigV4 refuses anything outside 1s..7d, so an out-of-range expiration is reported by key name
        // instead of failing deep inside the SDK
        assertThrows(IllegalArgumentException.class,
                () -> s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, SEVEN_DAYS + 1, "myfile.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, 0, "myfile.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, -30, "myfile.txt"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullFilename() {
        s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, EXPIRATION_SECONDS, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullBucket() {
        s3DirectDownloadService.generatePresignedUrl(null, KEY, EXPIRATION_SECONDS, "myfile.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullKey() {
        s3DirectDownloadService.generatePresignedUrl(BUCKET, null, EXPIRATION_SECONDS, "myfile.txt");
    }

    @Test(expected = RuntimeException.class)
    public void presignerFailureBubblesUp() {
        S3Presigner failing = org.mockito.Mockito.mock(S3Presigner.class);
        when(failing.presignGetObject(any(GetObjectPresignRequest.class))).thenThrow(new RuntimeException("boom"));
        s3DirectDownloadService.setS3Presigner(failing);

        s3DirectDownloadService.generatePresignedUrl(BUCKET, KEY, EXPIRATION_SECONDS, "myfile.txt");
    }

    private Map<String, String> queryOf(URI url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return params;
    }
}
