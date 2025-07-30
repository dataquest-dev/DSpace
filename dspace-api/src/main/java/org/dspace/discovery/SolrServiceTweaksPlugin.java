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
import org.springframework.beans.factory.annotation.Value;

/**
 * A DSpace Discovery plugin that customizes Solr queries to prioritize newer versions of items.
 */
public class SolrServiceTweaksPlugin implements SolrServiceSearchPlugin {
    private static final Logger log = LogManager.getLogger(SolrServiceTweaksPlugin.class);

    @Value("${solr.boost.title:2.0}")
    private float titleBoostValue;

    /**
     * Enhances Solr search by boosting matches in the "title" field
     * to prioritize them in the results. Additionally, results are
     * sorted by "versionNumber" in descending order to show the most
     * recent versions first.
     */
    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery) {
        String userQuery = discoveryQuery.getQuery();
        String query = String.format(
                "title:(%s)^%.1f",
                userQuery,
                titleBoostValue
        );

        solrQuery.setQuery(query);
        solrQuery.setSort("versionNumber", SolrQuery.ORDER.desc);

        log.debug("Modified Solr query: {}", solrQuery);
    }
}
