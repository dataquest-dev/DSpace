/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.external;

import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

import java.util.Objects;

public final class ExternalHandleConstants {
    public static final String MAGIC_BEAN = "@magicLindat@";

    public static final String DEFAULT_CANONICAL_HANDLE_PREFIX = "http://hdl.handle.net/";


    private static String repositoryName;

    private static String repositoryEmail;

    private static String canonicalHandlePrefix;

    /**
     * References to DSpace Services
     **/
    private static ConfigurationService configurationService;

    public static String getRepositoryName() {
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        if (Objects.nonNull(configurationService)) {
            String name = configurationService.getProperty(
                    "dspace.name");
            if (name != null) {
                repositoryName = name.trim();
            } else {
                repositoryName = null;
            }
        }

        return repositoryName;
    }

    private ExternalHandleConstants() {
    }
}
