/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that periodically fetches and caches the IdP discovery feed.
 */
@Component
public class DiscoFeedsUpdateScheduler implements InitializingBean {

    private static final Logger log = LogManager.getLogger(DiscoFeedsUpdateScheduler.class);

    private String feedsContent;

    @Autowired
    private DiscoFeedsDownloadService discoFeedsDownloadService;

    @Autowired
    private ConfigurationService configurationService;

    @Override
    public void afterPropertiesSet() throws Exception {
        refreshFeeds();
    }

    /**
     * Fetch and cache the IdP discovery feed on a cron schedule.
     */
    @Scheduled(cron = "${discojuice.refresh:-}")
    public void refreshFeeds() {
        boolean isAllowed = configurationService.getBooleanProperty("shibboleth.discofeed.allowed", false);
        if (!isAllowed) {
            return;
        }
        log.debug("Refreshing discovery feeds.");
        String newContent = discoFeedsDownloadService.downloadAndTransformFeeds();
        if (StringUtils.isNotBlank(newContent)) {
            feedsContent = newContent;
        } else {
            log.error("Failed to download discovery feeds.");
        }
    }

    /**
     * Returns the cached feed content.
     *
     * @return JSON string of the IdP feed, or null if not yet loaded.
     */
    public String getFeedsContent() {
        return feedsContent;
    }
}