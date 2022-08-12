/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.sql.SQLException;
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DspaceObjectClarinService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.utils.DSpace;
import org.springframework.stereotype.Component;


@Component
public class PIDConfiguration {
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(PIDConfiguration.class);
    private static PIDConfiguration instance;

    private static final String CLARIN_PID_COMMUNITY_CONFIGURATIONS_KEYWORD = "lr.pid.community.configurations";

    private static Map<UUID, PIDCommunityConfiguration> pidCommunityConfigurations;

    private ConfigurationService configurationService = new DSpace().getConfigurationService();

    private DspaceObjectClarinService dspaceObjectClarinService =
            ContentServiceFactory.getInstance().getDspaceObjectClarinService();

    private PIDConfiguration() {
        initialize();
    }

    /**
     * Initializes the singleton
     */
    private void initialize() {
        //edid this metod for more communities
        String[] pidCommunityConfigurationsArray= configurationService.getArrayProperty
                (CLARIN_PID_COMMUNITY_CONFIGURATIONS_KEYWORD);

        pidCommunityConfigurations = new HashMap<UUID, PIDCommunityConfiguration>();
        if (pidCommunityConfigurationsArray != null) {
            PIDCommunityConfiguration pidCommunityConfiguration = PIDCommunityConfiguration
                    .fromString(pidCommunityConfigurationsArray);
            pidCommunityConfigurations.put(
                    pidCommunityConfiguration.getCommunityID(),
                    pidCommunityConfiguration);
        }
    }

    /**
     * Returns the only instance of this singleton
     *
     * @return PIDConfiguration
     */
    public static PIDConfiguration getInstance() {
        if (instance == null) {
            instance = new PIDConfiguration();
        }
        return instance;
    }

    /**
     * Returns PID community configuration by community ID
     *
     * @param communityID
     *            Community ID
     * @return PID community configuration or null
     */
    public static PIDCommunityConfiguration getPIDCommunityConfiguration(
            UUID communityID) {
        instance = getInstance();
        PIDCommunityConfiguration pidCommunityConfiguration = pidCommunityConfigurations
                .get(communityID);
        if (pidCommunityConfiguration == null) {
            pidCommunityConfiguration = pidCommunityConfigurations.get(null);
        }
        if (pidCommunityConfiguration == null) {
            throw new IllegalStateException("Missing configuration entry in "
                    + CLARIN_PID_COMMUNITY_CONFIGURATIONS_KEYWORD
                    + " for community with ID " + communityID);
        }
        return pidCommunityConfiguration;
    }

    /**
     * Returns PID community configuration by DSpace object (according to
     * principal community)
     *
     * @param dso
     *            DSpaceObject
     * @return PID community configuration or null
     */
    public PIDCommunityConfiguration getPIDCommunityConfiguration(Context context,
                                                                  DSpaceObject dso) throws SQLException {
        instance = getInstance();
        UUID communityID = null;
        Community community = dspaceObjectClarinService.getPrincipalCommunity(context, dso);
        if (community != null) {
            communityID = community.getID();
        }
        PIDCommunityConfiguration pidCommunityConfiguration = getPIDCommunityConfiguration(communityID);
        return pidCommunityConfiguration;
    }

    /**
     * Returns map of PID community communications
     *
     * @return Map of PID community communications
     */
    public Map<UUID, PIDCommunityConfiguration> getPIDCommunityConfigurations() {
        instance = getInstance();
        return pidCommunityConfigurations;
    }

    /**
     * Returns default PID community configuration
     *
     * @return Default PID community configuration or null
     */
    public PIDCommunityConfiguration getDefaultCommunityConfiguration() {
        instance = getInstance();
        PIDCommunityConfiguration pidCommunityConfiguration = getPIDCommunityConfiguration((UUID)null);
        if (pidCommunityConfiguration == null) {
            UUID[] keys = pidCommunityConfigurations.keySet().toArray(new UUID[0]);
            if (keys.length > 0) {
                pidCommunityConfiguration = getPIDCommunityConfiguration(keys[0]);
            }
        }
        return pidCommunityConfiguration;
    }

    /**
     * Returns array of distinct alternative prefixes from all community configurations
     *
     * @return Array of distinct alternative prefixes from all community configurations (can be empty)
     */
    public String[] getAlternativePrefixes(String mainPrefix) {
        instance = getInstance();
        Set<String> alternativePrefixes = new HashSet<String>();
        for (PIDCommunityConfiguration pidCommunityConfiguration : pidCommunityConfigurations.values()) {
            if (mainPrefix != null && mainPrefix.equals(pidCommunityConfiguration.getPrefix())) {
                Collections.addAll(alternativePrefixes, pidCommunityConfiguration.getAlternativePrefixes());
            }
        }
        return (String[])alternativePrefixes.toArray(new String[alternativePrefixes.size()]);
    }

    /**
     * Returns prefix from default community configuration
     *
     * @return Prefix from default community configuration
     */
    public String getDefaultPrefix() {
        instance = getInstance();
        String prefix = null;
        PIDCommunityConfiguration pidCommunityConfiguration = getDefaultCommunityConfiguration();
        if (pidCommunityConfiguration != null) {
            prefix = pidCommunityConfiguration.getPrefix();
        }
        return prefix;
    }

    /**
     * Returns all possible prefixes for all communities
     *
     * @return All possible prefixes for all communities
     */
    public Set<String> getSupportedPrefixes() {
        instance = getInstance();
        Set<String> prefixes = new HashSet<String>();
        for (PIDCommunityConfiguration pidCommunityConfiguration : pidCommunityConfigurations.values()) {
            prefixes.add(pidCommunityConfiguration.getPrefix());
            Collections.addAll(prefixes, pidCommunityConfiguration.getAlternativePrefixes());
        }
        return prefixes;
    }
}
