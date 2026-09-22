/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.utils;

import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.dspace.app.rest.filter.IgnorableRangeRequestFilter;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Tests the conditional request headers handled by {@link HttpHeadersInitializer}: the ETag we
 * send out and the If-Match, If-None-Match and If-Range headers a client sends back.
 */
public class HttpHeadersInitializerTest {

    private static final String CHECKSUM = "184620a503489ab88e9e678feea682fa";
    private static final String OTHER_CHECKSUM = "deadbeefdeadbeefdeadbeefdeadbeef";
    private static final String QUOTED_CHECKSUM = "\"" + CHECKSUM + "\"";
    private static final String OTHER_QUOTED_CHECKSUM = "\"" + OTHER_CHECKSUM + "\"";
    private static final long LAST_MODIFIED = 1_600_000_000_000L;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private HttpHeadersInitializer initializerFor(HttpServletRequest httpRequest) {
        return new HttpHeadersInitializer()
            .withFileName("test.txt")
            .withChecksum(CHECKSUM)
            .withLength(10)
            .withMimetype("text/plain")
            .withLastModified(LAST_MODIFIED)
            .with(httpRequest)
            .with(response);
    }

    private HttpHeadersInitializer initializer() {
        return initializerFor(request);
    }

    /**
     * Run the request through the filter that lets a controller hide the Range header, and return
     * the request as the rest of the chain sees it.
     */
    private HttpServletRequest throughRangeFilter() throws Exception {
        AtomicReference<HttpServletRequest> wrapped = new AtomicReference<>();
        new IgnorableRangeRequestFilter()
            .doFilter(request, response, (req, res) -> wrapped.set((HttpServletRequest) req));
        return wrapped.get();
    }

    @Test
    public void etagIsSentQuoted() throws Exception {
        assertEquals(QUOTED_CHECKSUM, initializer().initialiseHeaders().getFirst("ETag"));
    }

    @Test
    public void ifMatchOnTheEtagWeSentIsAccepted() throws Exception {
        request.addHeader("If-Match", QUOTED_CHECKSUM);

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifMatchOnAnUnquotedChecksumIsAccepted() throws Exception {
        request.addHeader("If-Match", CHECKSUM);

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifMatchWithAWildcardIsAccepted() throws Exception {
        request.addHeader("If-Match", "*");

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifMatchListingOurEtagAmongOthersIsAccepted() throws Exception {
        request.addHeader("If-Match", OTHER_QUOTED_CHECKSUM + ", " + QUOTED_CHECKSUM);

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifMatchOnAWeakEtagIsRejected() throws Exception {
        request.addHeader("If-Match", "W/" + QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(SC_PRECONDITION_FAILED, response.getStatus());
    }

    @Test
    public void ifMatchOnAnotherEtagFailsWithPreconditionFailed() throws Exception {
        request.addHeader("If-Match", OTHER_QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(SC_PRECONDITION_FAILED, response.getStatus());
    }

    @Test
    public void ifNoneMatchOnTheEtagWeSentReturnsNotModified() throws Exception {
        request.addHeader("If-None-Match", QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(SC_NOT_MODIFIED, response.getStatus());
        assertEquals(QUOTED_CHECKSUM, response.getHeader("ETag"));
    }

    @Test
    public void ifNoneMatchOnAWeakEtagReturnsNotModified() throws Exception {
        request.addHeader("If-None-Match", "W/" + QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(SC_NOT_MODIFIED, response.getStatus());
    }

    @Test
    public void ifNoneMatchOnAnotherEtagServesTheFile() throws Exception {
        request.addHeader("If-None-Match", OTHER_QUOTED_CHECKSUM);

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifModifiedSinceAlsoReturnsNotModified() throws Exception {
        request.addHeader("If-Modified-Since", LAST_MODIFIED);

        assertFalse(initializer().isValid());
        assertEquals(SC_NOT_MODIFIED, response.getStatus());
        assertEquals(QUOTED_CHECKSUM, response.getHeader("ETag"));
    }

    @Test
    public void notModifiedRepeatsTheCachingHeaders() throws Exception {
        request.addHeader("If-None-Match", QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(FastHttpDateFormat.formatDate(LAST_MODIFIED), response.getHeader("Last-Modified"));
        assertEquals("private,no-cache", response.getHeader("Cache-Control"));
        assertNotNull(response.getHeader("Expires"));
    }

    @Test
    public void notModifiedWithoutAChecksumSendsNoEtag() throws Exception {
        request.addHeader("If-None-Match", "*");
        HttpHeadersInitializer sitemapLikeSender = new HttpHeadersInitializer()
            .withFileName("sitemap.xml")
            .withLength(10)
            .withMimetype("text/xml")
            .withLastModified(LAST_MODIFIED)
            .with(request)
            .with(response);

        assertFalse(sitemapLikeSender.isValid());
        assertEquals(SC_NOT_MODIFIED, response.getStatus());
        assertNull(response.getHeader("ETag"));
    }

    @Test
    public void ifMatchIsWeighedBeforeIfNoneMatch() throws Exception {
        request.addHeader("If-Match", OTHER_QUOTED_CHECKSUM);
        request.addHeader("If-None-Match", QUOTED_CHECKSUM);

        assertFalse(initializer().isValid());
        assertEquals(SC_PRECONDITION_FAILED, response.getStatus());
    }

    @Test
    public void ifMatchMakesIfUnmodifiedSinceIrrelevant() throws Exception {
        request.addHeader("If-Match", QUOTED_CHECKSUM);
        request.addHeader("If-Unmodified-Since", LAST_MODIFIED - 10_000L);

        assertTrue(initializer().isValid());
    }

    @Test
    public void ifRangeOnTheEtagWeSentKeepsTheRange() throws Exception {
        request.addHeader("Range", "bytes=1-3");
        request.addHeader("If-Range", QUOTED_CHECKSUM);
        HttpServletRequest filtered = throughRangeFilter();

        assertTrue(initializerFor(filtered).isValid());
        assertNotNull(filtered.getHeader("Range"));
    }

    @Test
    public void ifRangeOnTheDateWeSentKeepsTheRange() throws Exception {
        request.addHeader("Range", "bytes=1-3");
        request.addHeader("If-Range", FastHttpDateFormat.formatDate(LAST_MODIFIED));
        HttpServletRequest filtered = throughRangeFilter();

        assertTrue(initializerFor(filtered).isValid());
        assertNotNull(filtered.getHeader("Range"));
    }

    @Test
    public void ifRangeOnAnotherEtagDropsTheRange() throws Exception {
        request.addHeader("Range", "bytes=1-3");
        request.addHeader("If-Range", OTHER_QUOTED_CHECKSUM);
        HttpServletRequest filtered = throughRangeFilter();

        assertTrue(initializerFor(filtered).isValid());
        assertNull(filtered.getHeader("Range"));
    }

    @Test
    public void ifRangeOnAnUnquotedChecksumKeepsTheRange() throws Exception {
        request.addHeader("Range", "bytes=1-3");
        request.addHeader("If-Range", CHECKSUM);
        HttpServletRequest filtered = throughRangeFilter();

        assertTrue(initializerFor(filtered).isValid());
        assertNotNull(filtered.getHeader("Range"));
    }

    @Test
    public void ifRangeWithoutARangeHeaderChangesNothing() throws Exception {
        request.addHeader("If-Range", OTHER_QUOTED_CHECKSUM);

        assertTrue(initializer().isValid());
    }
}
