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

    @Value("${solr.boost.replaces:2.0}")
    private float replacesBoostValue;

    @Value("${solr.boost.latestVersion:2.0}")
    private float latestVersionBoostValue;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {
        String query = solrQuery.getQuery();
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        if (query.contains("search.resourceid:")) {
            log.debug("Exact resourceid search detected, skipping boosts.");
            return;
        }

        String baseQuery = "+(" + query + ")" ;
        String titleBoost = "title:(" + query + ")^" + titleBoostValue;
        String replacesBoost = "dc.relation.replaces:[* TO *]^" + replacesBoostValue;
        String latestVersionBoost = "(dc.relation.replaces:[* TO *] AND -dc.relation.isreplacedby:[* TO *])^"
                + latestVersionBoostValue;

        String boostedQuery = baseQuery
                + " OR " + titleBoost
                + " OR " + replacesBoost
                + " OR " + latestVersionBoost;

        solrQuery.setQuery(boostedQuery);

        log.debug("Boosted query: {}", boostedQuery);
    }
}
