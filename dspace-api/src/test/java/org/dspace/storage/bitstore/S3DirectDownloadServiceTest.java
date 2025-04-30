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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.Date;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.dspace.AbstractUnitTest;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.service.S3DirectDownloadService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test class for S3DirectDownloadService.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class S3DirectDownloadServiceTest extends AbstractUnitTest {

    private S3DirectDownloadService s3DirectDownloadService;

    @Mock
    private S3BitStoreService s3BitstoreService;
    @Mock
    private ConfigurationService configService;
    @Mock
    private AmazonS3 amazonS3;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        s3DirectDownloadService = new S3DirectDownloadServiceImpl();
        ReflectionTestUtils.setField(s3DirectDownloadService, "s3BitStoreService", s3BitstoreService);
        ReflectionTestUtils.setField(s3DirectDownloadService, "configurationService", configService);

        // Reflectively set the mock’s private/public field
        ReflectionTestUtils.setField(s3BitstoreService, "s3Service", amazonS3);

        ReflectionTestUtils.invokeMethod(s3DirectDownloadService, "init");
    }

    @Test
    public void generatePresignedUrl() throws Exception {
        // Mock the presigned URL generation
        URL fakeUrl = new URL("https://example.com/foo");
        when(amazonS3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(fakeUrl);

        // Rum the method to generate the presigned URL
        String url = s3DirectDownloadService.generatePresignedUrl("bucket", "key", 120, "myfile.txt");

        // Compare the generated URL with the mocked one
        assertEquals("https://example.com/foo", url);

        // Verify that the presigned URL was generated with the correct parameters
        GeneratePresignedUrlRequest req = captureRequest();
        assertEquals("bucket", req.getBucketName());
        assertEquals("key", req.getKey());
        assertEquals("attachment; filename=\"myfile.txt\"",
                req.getRequestParameters().get("response-content-disposition"));
        assertTrue(req.getExpiration().after(new Date()));
    }

    // Zero expiration → URL still generated with expiration == now (or slightly after)
    @Test
    public void zeroExpiration() throws Exception {
        URL fake = new URL("https://zero");
        when(amazonS3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(fake);

        s3DirectDownloadService.generatePresignedUrl("b", "k", 0, "f");
        GeneratePresignedUrlRequest req = captureRequest();
        // Expiration should be >= now
        assertFalse(req.getExpiration().before(new Date()));
    }

    // Negative expiration → expiration in the past
    @Test
    public void negativeExpiration() throws Exception {
        URL fake = new URL("https://neg");
        when(amazonS3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(fake);

        s3DirectDownloadService.generatePresignedUrl("b", "k", -30, "f");
        GeneratePresignedUrlRequest req = captureRequest();
        // Expiration < now + a small slack (1s)
        assertTrue(req.getExpiration().before(new Date(System.currentTimeMillis() + 1000)));
    }

    // DesiredFilename == null → header becomes "attachment; filename=\"null\""
    @Test
    public void nullFilename() throws Exception {
        URL fake = new URL("https://nullfn");
        when(amazonS3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(fake);

        s3DirectDownloadService.generatePresignedUrl("b", "k", 60, null);
        GeneratePresignedUrlRequest req = captureRequest();
        assertEquals("attachment; filename=\"null\"",
                req.getRequestParameters().get("response-content-disposition"));
    }

    // DesiredFilename with control chars / path traversal
    @Test
    public void weirdFilename() throws Exception {
        URL fake = new URL("https://weird");
        when(amazonS3.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(fake);

        String weird = "../secret\nname\t.txt";
        s3DirectDownloadService.generatePresignedUrl("b", "k", 60, weird);
        GeneratePresignedUrlRequest req = captureRequest();

        String cd = req.getRequestParameters().get("response-content-disposition");
        // we expect it simply wraps in quotes; internal newlines/tabs are left verbatim
        assertTrue(cd.startsWith("attachment; filename=\""));
        assertTrue(cd.endsWith("\""));
        assertTrue(cd.contains("../secret\nname\t.txt"));
    }

    // Underlying AmazonS3 throws → bubbles up
    @Test(expected = RuntimeException.class)
    public void amazonThrows() {
        when(amazonS3.generatePresignedUrl(any())).thenThrow(new RuntimeException("boom"));
        s3DirectDownloadService.generatePresignedUrl("b", "k", 1, "f");
    }

    // Bucket key == null → should NPE
    @Test(expected = NullPointerException.class)
    public void nullBucket() {
        s3DirectDownloadService.generatePresignedUrl(null, "k", 60, "f");
    }

    // Bucket key == null → should NPE
    @Test(expected = NullPointerException.class)
    public void nullKey() {
        s3DirectDownloadService.generatePresignedUrl("b", null, 60, "f");
    }

    // helper to pull out the single captured request
    private GeneratePresignedUrlRequest captureRequest() {
        ArgumentCaptor<GeneratePresignedUrlRequest> cap =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(amazonS3, atLeastOnce()).generatePresignedUrl(cap.capture());
        return cap.getValue();
    }
}