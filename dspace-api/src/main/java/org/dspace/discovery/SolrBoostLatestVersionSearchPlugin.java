package org.dspace.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.dspace.core.Context;

/**
 * A DSpace Discovery plugin that customizes Solr queries to prioritize newer versions of items.
 */
public class SolrBoostLatestVersionSearchPlugin implements SolrServiceSearchPlugin {
    private static final Logger log = LogManager.getLogger(SolrBoostLatestVersionSearchPlugin .class);

    // Boost factors
    private static final float BOOST = 2.0f;

    @Override
    public void additionalSearchParameters(Context context, DiscoverQuery discoveryQuery, SolrQuery solrQuery)
            throws SearchServiceException {
        String originalQuery = solrQuery.getQuery();
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            // No query, no boost needed
            return;
        }
        // Escape original query to avoid Solr syntax issues
        String escapedQuery = ClientUtils.escapeQueryChars(originalQuery);

        // Base query: require original terms
        String baseQuery = "+(" + escapedQuery + ")";

        // Boost if query terms appear in the title field
        String titleBoost = "title:(" + escapedQuery + ")^" + BOOST;

        // Boost items that replace others (newer versions)
        String replacesBoost = "dc.relation.replaces:[* TO *]^" + BOOST;

        // Boost latest versions: replace others but are not replaced themselves
        String latestVersionBoost = "(dc.relation.replaces:[* TO *] AND -dc.relation.isreplacedby:[* TO *])^" + BOOST;

        // Combine all parts with OR to boost
        String boostedQuery = String.join(" OR ",
                baseQuery,
                titleBoost,
                replacesBoost,
                latestVersionBoost
        );

        log.debug("Setting boosted Solr query: {}", boostedQuery);

        // set the updated query back to solrQuery
        solrQuery.setQuery(boostedQuery);
    }
}
