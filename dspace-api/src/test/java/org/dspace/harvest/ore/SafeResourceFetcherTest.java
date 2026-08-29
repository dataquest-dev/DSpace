/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.dspace.app.client.DSpaceHttpClientFactory;
import org.dspace.harvest.HarvestedCollection;
import org.dspace.services.ConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * Unit tests for {@link SafeResourceFetcher}, driven against a {@link MockWebServer}.
 */
public class SafeResourceFetcherTest {

    private static final String PREFIX = "oai.harvester.ore.file.";

    private final SafeResourceFetcher fetcher = new SafeResourceFetcher();

    private final Map<String, Object> config = new HashMap<>();

    private MockWebServer server;

    private MockedStatic<DSpaceHttpClientFactory> httpClientFactory;

    @Before
    public void setUp() throws IOException {
        // MockWebServer answers on 127.0.0.1, which the predicate blocks, so the tests exempt it explicitly
        config.put(PREFIX + "allowedInternalHosts", new String[] {"127.0.0.1"});
        server = new MockWebServer();
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        httpClientFactory = mockStatic(DSpaceHttpClientFactory.class);
        httpClientFactory.when(() -> DSpaceHttpClientFactory.getInstance()).thenReturn(new PlaintextClientFactory());
    }

    @After
    public void tearDown() throws IOException {
        httpClientFactory.close();
        server.shutdown();
    }

    @Test
    public void returnsTheBodyOfAnAllowedUrl() throws Exception {
        server.enqueue(new MockResponse().setBody("aggregated bitstream"));
        try (InputStream body = fetcher.fetch(url("/bitstreams/1"), policy())) {
            assertEquals("aggregated bitstream", new String(body.readAllBytes(), UTF_8));
        }
        RecordedRequest request = server.takeRequest();
        assertEquals("/bitstreams/1", request.getPath());
    }

    @Test
    public void rejectsTheServerAddressWhenItIsNotExempted() {
        config.remove(PREFIX + "allowedInternalHosts");
        OreResourceRejectedException rejected =
            assertThrows(OreResourceRejectedException.class, () -> fetcher.fetch(url("/bitstreams/1"), policy()));
        assertEquals(RejectionReason.ADDRESS_BLOCKED, rejected.getReason());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void rejectsARedirectToTheMetadataService() {
        String metadata = "http://169.254.169.254/latest/meta-data/iam/security-credentials/";
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", metadata));
        OreResourceRejectedException rejected =
            assertThrows(OreResourceRejectedException.class, () -> fetcher.fetch(url("/ore"), policy()));
        assertEquals(RejectionReason.ADDRESS_BLOCKED, rejected.getReason());
        assertEquals(metadata, rejected.getUrl());
        // the hop is judged before it is fetched, so nothing was ever sent to the metadata service
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void rejectsARedirectChainOverTheCap() {
        config.put(PREFIX + "maxRedirects", 2);
        for (int hop = 0; hop < 3; hop++) {
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/hop" + hop));
        }
        OreResourceRejectedException rejected =
            assertThrows(OreResourceRejectedException.class, () -> fetcher.fetch(url("/ore"), policy()));
        assertEquals(RejectionReason.TOO_MANY_REDIRECTS, rejected.getReason());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void refusesAnHttpsToHttpDowngrade() {
        String downgraded = "http://127.0.0.1:" + server.getPort() + "/plain";
        server.enqueue(new MockResponse().setResponseCode(301).addHeader("Location", downgraded));
        URI secure = URI.create("https://127.0.0.1:" + server.getPort() + "/secure");
        OreResourceRejectedException rejected =
            assertThrows(OreResourceRejectedException.class, () -> fetcher.fetch(secure, policy()));
        assertEquals(RejectionReason.REDIRECT_DOWNGRADE, rejected.getReason());
        assertEquals(downgraded, rejected.getUrl());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void rejectsADeclaredLengthOverTheCap() {
        config.put(PREFIX + "maxBytes", 16L);
        server.enqueue(new MockResponse().setBody(body(4096)));
        OreResourceRejectedException rejected =
            assertThrows(OreResourceRejectedException.class, () -> fetcher.fetch(url("/big"), policy()));
        assertEquals(RejectionReason.RESPONSE_TOO_LARGE, rejected.getReason());
    }

    @Test
    public void rejectsABodyThatOutgrowsTheCapWhileStreaming() throws Exception {
        config.put(PREFIX + "maxBytes", 16L);
        server.enqueue(new MockResponse().setChunkedBody(body(4096), 512));
        try (InputStream body = fetcher.fetch(url("/big"), policy())) {
            assertThrows(SafeResourceFetcher.ResponseTooLargeException.class, () -> body.readAllBytes());
        }
    }

    @Test
    public void rejectsABodyThatLiesAboutItsLength() throws Exception {
        config.put(PREFIX + "maxBytes", 16L);
        // chunked framing wins over the header, so the declared length never gets the chance to be trusted
        server.enqueue(new MockResponse().setChunkedBody(body(4096), 512).setHeader("Content-Length", "4"));
        try (InputStream body = fetcher.fetch(url("/liar"), policy())) {
            assertThrows(SafeResourceFetcher.ResponseTooLargeException.class, () -> body.readAllBytes());
        }
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getPort() + path);
    }

    private static String body(int length) {
        return "x".repeat(length);
    }

    /**
     * External URLs are allowed, which is the state in which the address checks have to hold on their own.
     */
    private OreEgressPolicy policy() {
        HarvestedCollection harvestRow = mock(HarvestedCollection.class);
        when(harvestRow.isAllowExternalUrls()).thenReturn(true);
        when(harvestRow.getOaiSource()).thenReturn(null);
        return OreEgressPolicy.from(harvestRow, configuration());
    }

    /**
     * Answers with whatever default the caller passes in, so the tests run against the shipped defaults and
     * only the entries a test puts in {@link #config} differ.
     */
    private ConfigurationService configuration() {
        return mock(ConfigurationService.class, invocation -> {
            Object[] arguments = invocation.getArguments();
            if (arguments.length == 0) {
                return null;
            }
            Object value = config.get(arguments[0]);
            if (value != null) {
                return value;
            }
            return arguments.length > 1 ? arguments[1] : null;
        });
    }

    /**
     * Stands in for the Spring-managed factory. It also serves https over a plaintext socket, so the redirect
     * rules can be exercised against MockWebServer without a test certificate authority.
     */
    private static class PlaintextClientFactory extends DSpaceHttpClientFactory {

        @Override
        public HttpClientBuilder builder(boolean setProxy) {
            Registry<ConnectionSocketFactory> plaintext = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", PlainConnectionSocketFactory.getSocketFactory())
                .build();
            return HttpClients.custom().setConnectionManager(new PoolingHttpClientConnectionManager(plaintext));
        }
    }
}
