/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.dspace.harvest.HarvestedCollection;
import org.dspace.services.ConfigurationService;

/**
 * Immutable set of rules applied when the ORE ingest crosswalk fetches an aggregated resource.
 * <p>
 * Only {@link #isAllowExternalUrls()} is per collection; it widens the set of permitted public hosts and
 * nothing else. Internal, private and reserved addresses stay blocked in both states.
 */
public final class OreEgressPolicy {

    static final String CONFIG_PREFIX = "oai.harvester.ore.file.";

    private final boolean allowExternalUrls;
    private final URI anchor;
    private final boolean blockInternalAddresses;
    private final List<String> allowedUrlPrefix;
    private final List<String> allowedInternalHosts;
    private final int maxRedirects;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final long maxBytes;

    private OreEgressPolicy(boolean allowExternalUrls, URI anchor, ConfigurationService configurationService) {
        this.allowExternalUrls = allowExternalUrls;
        this.anchor = anchor;
        this.blockInternalAddresses =
            configurationService.getBooleanProperty(CONFIG_PREFIX + "blockInternalAddresses", true);
        this.allowedUrlPrefix = trimmedList(configurationService.getArrayProperty(CONFIG_PREFIX + "allowedUrlPrefix"));
        this.allowedInternalHosts =
            trimmedList(configurationService.getArrayProperty(CONFIG_PREFIX + "allowedInternalHosts"));
        this.maxRedirects = configurationService.getIntProperty(CONFIG_PREFIX + "maxRedirects", 3);
        this.connectTimeoutMs = configurationService.getIntProperty(CONFIG_PREFIX + "connectTimeout", 10000);
        this.readTimeoutMs = configurationService.getIntProperty(CONFIG_PREFIX + "readTimeout", 30000);
        this.maxBytes = configurationService.getLongProperty(CONFIG_PREFIX + "maxBytes", 2147483648L);
    }

    /**
     * Policy for callers that have no harvest context, such as the AIP/METS/SWORD packagers and the XSLT CLI:
     * no anchor and the flag off, so only an explicit allowedUrlPrefix can satisfy the host check.
     *
     * @param configurationService the DSpace configuration
     * @return the fail-closed policy
     */
    public static OreEgressPolicy strictest(ConfigurationService configurationService) {
        return new OreEgressPolicy(false, null, configurationService);
    }

    /**
     * Policy for a harvested collection. The trust anchor is the administrator-supplied oai_source, never a
     * value read out of the harvested document.
     *
     * @param harvestRow           the collection being harvested, may be null
     * @param configurationService the DSpace configuration
     * @return the policy to apply to that collection's ORE ingest
     */
    public static OreEgressPolicy from(HarvestedCollection harvestRow, ConfigurationService configurationService) {
        if (harvestRow == null) {
            return strictest(configurationService);
        }
        return new OreEgressPolicy(harvestRow.isAllowExternalUrls(), toUri(harvestRow.getOaiSource()),
                                   configurationService);
    }

    public boolean isAllowExternalUrls() {
        return allowExternalUrls;
    }

    /**
     * @return the oai_source this collection harvests from, or null when there is no harvest context or the
     *         configured value is not a usable absolute URL
     */
    public URI getAnchor() {
        return anchor;
    }

    public boolean isBlockInternalAddresses() {
        return blockInternalAddresses;
    }

    public List<String> getAllowedUrlPrefix() {
        return allowedUrlPrefix;
    }

    public List<String> getAllowedInternalHosts() {
        return allowedInternalHosts;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * @return the response size cap in bytes, or a negative value for unlimited
     */
    public long getMaxBytes() {
        return maxBytes;
    }

    private static URI toUri(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static List<String> trimmedList(String[] values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(values)
                     .filter(StringUtils::isNotBlank)
                     .map(String::trim)
                     .collect(Collectors.toUnmodifiableList());
    }
}
