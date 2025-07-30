/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.dspace.core.Context;

/**
 * A DSpace Discovery plugin that customizes Solr queries to prioritize newer versions of items.
 */
public class SolrServiceTweaksPlugin implements SolrServiceSearchPlugin {
    private static final Logger log = LogManager.getLogger(SolrServiceTweaksPlugin.class);

    /**
     * Results are sorted by "versionNumber" in descending order to show the most
     * recent versions first.
     */
    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery) {
        solrQuery.setSort("versionNumber", SolrQuery.ORDER.desc);
        log.debug("Modified Solr query: {}", solrQuery);
    }
}
