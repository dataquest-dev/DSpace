/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that fetches raw IdP metadata JSON from the Shibboleth SP DiscoFeed endpoint,
 * transforms it into a compact format, and returns it as a JSON string.
 */
@Service
public class DiscoFeedsDownloadService {

    private static final Logger log = LogManager.getLogger(DiscoFeedsDownloadService.class);

    private static boolean disableSSL;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ConfigurationService configurationService;

    /**
     * Downloads the DiscoFeed JSON, applies the shrink transform, deduplicates by entityID,
     * and returns the result as a JSON string.
     *
     * @return JSON string of transformed IdP entries, or null if download failed.
     */
    public String downloadAndTransformFeeds() {
        disableSSL = configurationService.getBooleanProperty(
                "disable.ssl.check.specific.requests", false);
        String feedUrl = configurationService.getProperty("shibboleth.discofeed.url");
        if (StringUtils.isBlank(feedUrl)) {
            log.error("shibboleth.discofeed.url is not configured.");
            return null;
        }

        ArrayNode raw = downloadJSON(feedUrl);
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        // Shrink and deduplicate by entityID
        Map<String, ObjectNode> seen = new LinkedHashMap<>();
        for (JsonNode node : raw) {
            String entityID = node.path("entityID").asText(null);
            if (entityID != null && !seen.containsKey(entityID)) {
                seen.put(entityID, shrinkEntry(node));
            }
        }

        List<ObjectNode> entries = new ArrayList<>(seen.values());
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (Exception e) {
            log.error("Failed to serialize DiscoFeed entries.", e);
            return null;
        }
    }

    private ArrayNode downloadJSON(String url) {
        try {
            InputStream is;
            if (url.startsWith("TEST:")) {
                String classpathResource = url.substring("TEST:".length());
                is = getClass().getResourceAsStream(classpathResource);
                if (is == null) {
                    log.error("Classpath resource not found: {}", classpathResource);
                    return null;
                }
            } else {
                URLConnection conn = new URL(url).openConnection();
                if (conn instanceof HttpsURLConnection && disableSSL) {
                    disableCertificateValidation((HttpsURLConnection) conn);
                }
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                is = conn.getInputStream();
            }
            try (is) {
                JsonNode node = objectMapper.readTree(is);
                if (node.isArray()) {
                    return (ArrayNode) node;
                }
            }
        } catch (Exception e) {
            log.error("Failed to download/parse DiscoFeed from {}", url, e);
        }
        return null;
    }

    /**
     * Disables SSL certificate validation on a specific HTTPS connection.
     * This is for development / self-signed certificates only.
     * Never applies globally — only to the supplied connection instance.
     *
     * @param connection the HTTPS connection to disable validation on.
     */
    static void disableCertificateValidation(HttpsURLConnection connection) {
        TrustManager[] trustAll = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // no-op: trust all for dev
                }
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // no-op: trust all for dev
                }
            }
        };
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAll, new SecureRandom());
            connection.setSSLSocketFactory(sc.getSocketFactory());
            connection.setHostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to disable SSL certificate validation", e);
        }
    }

    private ObjectNode shrinkEntry(JsonNode entity) {
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("entityID", entity.path("entityID").asText(""));
        compact.put("title", buildTitle(entity));
        compact.put("country", "_all_");
        return compact;
    }

    private String buildTitle(JsonNode entity) {
        JsonNode displayNames = entity.path("DisplayNames");
        if (displayNames.isMissingNode() || !displayNames.isArray()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (JsonNode nameNode : displayNames) {
            String value = nameNode.path("value").asText(null);
            if (value != null) {
                joiner.add(value);
            }
        }
        return joiner.toString();
    }
}