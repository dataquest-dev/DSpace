/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that serves the cached IdP discovery feed JSON.
 */
@RestController
@RequestMapping("/api/discojuice/feeds")
public class DiscoFeedsController {

    @Autowired
    private DiscoFeedsUpdateScheduler discoFeedsUpdateScheduler;

    /**
     * Returns the cached IdP feed as a JSON array.
     *
     * @return HTTP 200 with the JSON feed, or HTTP 503 if the feed is not yet available.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> getDiscoFeeds() {
        String feedsContent = discoFeedsUpdateScheduler.getFeedsContent();
        if (StringUtils.isBlank(feedsContent)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("[]");
        }
        return ResponseEntity.ok(feedsContent);
    }
}