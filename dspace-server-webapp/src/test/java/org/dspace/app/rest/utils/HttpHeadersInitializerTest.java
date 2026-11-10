/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests conditional bitstream requests independently of the database and file store.
 */
public class HttpHeadersInitializerTest {

    private static final String CHECKSUM = "current-checksum";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HttpHeadersInitializer initializer;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        initializer = new HttpHeadersInitializer().with(request).with(response)
                .withFileName("download.pdf").withChecksum(CHECKSUM);
    }

    @Test
    public void mismatchedIfMatchReturnsPreconditionFailed() throws IOException {
        request.addHeader("If-Match", "stale-checksum");

        assertFalse(initializer.isValid());
        assertEquals(HttpServletResponse.SC_PRECONDITION_FAILED, response.getStatus());
    }

    @Test
    public void matchingIfMatchAllowsDownload() throws IOException {
        request.addHeader("If-Match", CHECKSUM);

        assertTrue(initializer.isValid());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    public void wildcardIfMatchAllowsDownload() throws IOException {
        request.addHeader("If-Match", "*");

        assertTrue(initializer.isValid());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    public void listContainingMatchingIfMatchAllowsDownload() throws IOException {
        request.addHeader("If-Match", "stale-checksum, " + CHECKSUM);

        assertTrue(initializer.isValid());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    public void absentIfMatchAllowsDownload() throws IOException {
        assertTrue(initializer.isValid());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }
}
