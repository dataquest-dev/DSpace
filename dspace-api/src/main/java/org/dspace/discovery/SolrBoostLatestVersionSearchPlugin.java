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
import org.apache.solr.client.solrj.util.ClientUtils;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Value;

/**
 * A DSpace Discovery plugin that customizes Solr queries to prioritize newer versions of items.
 */
public class SolrBoostLatestVersionSearchPlugin implements SolrServiceSearchPlugin {
    private static final Logger log = LogManager.getLogger(SolrBoostLatestVersionSearchPlugin.class);

    @Value("${solr.boost.replaces:2.0}")
    private float replacesBoost;

    @Value("${solr.boost.latestVersion:3.0}")
    private float latestVersionBoost;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {
        String originalQuery = solrQuery.getQuery();
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            // No query, no boost needed
            return;
        }
        String baseQuery = "+(" + originalQuery + ")";
        String titleBoost = "title:(" + originalQuery + ")^" + replacesBoost;
        String replacesBoostQuery = "dc.relation.replaces:[* TO *]^" + replacesBoost;
        String latestVersionBoostQuery = "(dc.relation.replaces:[* TO *] AND -dc.relation.isreplacedby:[* TO *])^" + latestVersionBoost;

        // Combine base query (mandatory) with boost queries (optional scoring enhancement)
        String boostedQuery = baseQuery + " OR " + String.join(" OR ",
                titleBoost,
                replacesBoostQuery,
                latestVersionBoostQuery
        );

        log.debug("Setting boosted Solr query: {}", boostedQuery);

        // set the updated query back to solrQuery
        solrQuery.setQuery(boostedQuery);
    }
}
