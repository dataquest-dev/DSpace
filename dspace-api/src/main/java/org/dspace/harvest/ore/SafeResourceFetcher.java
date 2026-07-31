/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.dspace.app.client.DSpaceHttpClientFactory;

/**
 * Fetches an ORE aggregated resource under an {@link OreEgressPolicy}: redirects are followed by hand so
 * that every hop is validated again with a fresh DNS lookup, the socket is pinned to the address that was
 * validated, and the body is capped.
 */
public class SafeResourceFetcher {

    private static final String HTTPS = "https";

    /** HttpClient 4.5 has no HttpStatus constant for 308. */
    private static final int SC_PERMANENT_REDIRECT = 308;

    private final OreUrlValidator validator = new OreUrlValidator();

    /**
     * Fetch a resource.
     *
     * @param url    the resource to fetch
     * @param policy the rules to apply, to this URL and to every redirect hop
     * @return the response body; closing it releases the connection
     * @throws OreResourceRejectedException if the URL, a redirect hop or the response is not acceptable
     * @throws IOException                  if the transfer itself fails
     */
    public InputStream fetch(URI url, OreEgressPolicy policy) throws OreResourceRejectedException, IOException {
        CloseableHttpClient client = buildClient(policy);
        boolean streamReturned = false;
        try {
            URI current = url;
            int hops = 0;
            while (true) {
                OreUrlValidator.Decision decision = validator.validate(current, policy);
                if (!decision.isAllowed()) {
                    throw new OreResourceRejectedException(decision.reason(), current.toString(), decision.detail());
                }

                CloseableHttpResponse response = execute(client, current, decision.addresses());
                boolean responseReturned = false;
                try {
                    int status = response.getStatusLine().getStatusCode();
                    if (isRedirect(status)) {
                        current = nextHop(current, response, policy, hops++);
                        continue;
                    }
                    InputStream body = openBody(current, response, policy);
                    responseReturned = true;
                    streamReturned = true;
                    return new CappedStream(body, response, client, policy.maxBytes());
                } finally {
                    if (!responseReturned) {
                        response.close();
                    }
                }
            }
        } finally {
            if (!streamReturned) {
                client.close();
            }
        }
    }

    private InputStream openBody(URI current, CloseableHttpResponse response, OreEgressPolicy policy)
        throws OreResourceRejectedException, IOException {
        int status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_OK) {
            throw new OreResourceRejectedException(RejectionReason.FETCH_FAILED, current.toString(),
                                                   "HTTP status " + status);
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new OreResourceRejectedException(RejectionReason.FETCH_FAILED, current.toString(),
                                                   "no response body");
        }
        if (policy.maxBytes() >= 0 && entity.getContentLength() > policy.maxBytes()) {
            throw new OreResourceRejectedException(RejectionReason.RESPONSE_TOO_LARGE, current.toString(),
                                                   "declared " + entity.getContentLength() + " bytes");
        }
        return entity.getContent();
    }

    private CloseableHttpClient buildClient(OreEgressPolicy policy) {
        RequestConfig requestConfig = RequestConfig.custom()
                                                   .setRedirectsEnabled(false)
                                                   .setConnectTimeout(policy.connectTimeoutMs())
                                                   .setConnectionRequestTimeout(policy.connectTimeoutMs())
                                                   .setSocketTimeout(policy.readTimeoutMs())
                                                   .build();
        // compression is off so that a compressed body cannot expand past the byte cap; builder(true) keeps the
        // configured egress proxy, which then owns the connect and therefore the address pinning
        return DSpaceHttpClientFactory.getInstance().builder(true)
                                      .setDefaultRequestConfig(requestConfig)
                                      .disableContentCompression()
                                      .disableAutomaticRetries()
                                      .build();
    }

    /**
     * Try the validated addresses in turn. The validator vetted every one of them, so failing over is
     * security-neutral, and it keeps a round-robin DNS entry with one dead address working as it did before.
     */
    private CloseableHttpResponse execute(CloseableHttpClient client, URI uri, List<InetAddress> addresses)
        throws IOException {
        IOException lastFailure = null;
        for (InetAddress address : addresses) {
            try {
                return client.execute(pinnedHost(uri, address), newRequest(uri));
            } catch (ConnectTimeoutException | HttpHostConnectException e) {
                lastFailure = e;
            }
        }
        // the list is never empty: the validator rejects a host that resolves to nothing
        throw lastFailure != null ? lastFailure : new IOException("no address to connect to for " + uri.getHost());
    }

    private HttpGet newRequest(URI uri) {
        HttpGet request = new HttpGet(requestTarget(uri));
        if (uri.getPort() <= 0) {
            // pinning forces an explicit port onto the HttpHost, but the default port must stay out of Host:
            // or presigned S3 and signed-CDN URLs fail their signature check
            request.setHeader(HTTP.TARGET_HOST, uri.getHost());
        }
        return request;
    }

    /**
     * Connect to the address that was validated, but keep the hostname so Host: and SNI still carry the name.
     */
    private HttpHost pinnedHost(URI uri, InetAddress address) {
        String scheme = lower(uri.getScheme());
        // the port has to be explicit: DefaultRoutePlanner rebuilds a portless HttpHost by name and would
        // throw the pinned address away, leaving the connection to a second, unvalidated DNS lookup
        int port = uri.getPort() > 0 ? uri.getPort() : (HTTPS.equals(scheme) ? 443 : 80);
        return new HttpHost(address, uri.getHost(), port, scheme);
    }

    private String requestTarget(URI uri) {
        String path = StringUtils.defaultIfEmpty(uri.getRawPath(), "/");
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private URI nextHop(URI current, CloseableHttpResponse response, OreEgressPolicy policy, int hop)
        throws OreResourceRejectedException {
        if (hop >= policy.maxRedirects()) {
            throw new OreResourceRejectedException(RejectionReason.TOO_MANY_REDIRECTS, current.toString(),
                                                   "more than " + policy.maxRedirects() + " hops");
        }
        Header location = response.getFirstHeader("Location");
        if (location == null || StringUtils.isBlank(location.getValue())) {
            throw new OreResourceRejectedException(RejectionReason.FETCH_FAILED, current.toString(),
                                                   "redirect without a Location header");
        }
        URI next;
        try {
            next = current.resolve(new URI(location.getValue().trim()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new OreResourceRejectedException(RejectionReason.MALFORMED_AUTHORITY, current.toString(),
                                                   "unparseable Location header", e);
        }
        if (HTTPS.equals(lower(current.getScheme())) && !HTTPS.equals(lower(next.getScheme()))) {
            throw new OreResourceRejectedException(RejectionReason.REDIRECT_DOWNGRADE, next.toString(),
                                                   "https redirected to " + next.getScheme());
        }
        return next;
    }

    private static boolean isRedirect(int status) {
        return status == HttpStatus.SC_MOVED_PERMANENTLY || status == HttpStatus.SC_MOVED_TEMPORARILY
            || status == HttpStatus.SC_SEE_OTHER || status == HttpStatus.SC_TEMPORARY_REDIRECT
            || status == SC_PERMANENT_REDIRECT;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Signals that the response outgrew the configured cap while it was being read. It has to be an
     * {@link IOException} to be throwable from {@link InputStream#read()}.
     */
    public static class ResponseTooLargeException extends IOException {
        public ResponseTooLargeException(long maxBytes) {
            super("ORE resource exceeded the configured maximum of " + maxBytes + " bytes");
        }
    }

    /**
     * Counts what is actually read rather than trusting Content-Length, and releases the connection and the
     * client when it is closed.
     */
    private static class CappedStream extends FilterInputStream {

        private final CloseableHttpResponse response;
        private final CloseableHttpClient client;
        private final long maxBytes;
        private long consumed;

        CappedStream(InputStream in, CloseableHttpResponse response, CloseableHttpClient client, long maxBytes) {
            super(in);
            this.response = response;
            this.client = client;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            if (read >= 0) {
                count(1);
            }
            return read;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(long bytes) throws IOException {
            consumed += bytes;
            if (maxBytes >= 0 && consumed > maxBytes) {
                throw new ResponseTooLargeException(maxBytes);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    response.close();
                } finally {
                    client.close();
                }
            }
        }
    }
}
