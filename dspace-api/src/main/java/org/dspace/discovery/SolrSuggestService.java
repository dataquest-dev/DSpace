/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Query the /suggest Solr search request handler to provide
 * weighted suggestions from dictionaries based on search index
 * documents and/or file-based word lists.
 *
 * Extended with support for:
 * - JSON static file based dictionaries (e.g. language lists)
 * - Dictionary allowlist security
 * - Result deduplication
 * - Configurable value formatting with separators
 *
 * @author Kim Shepherd
 * @author Milan Majchrak (CLARIN extensions)
 */
public class SolrSuggestService {

    private static final Logger log = LogManager.getLogger();

    /**
     * Prefix for dictionaries backed by JSON static files on the classpath.
     */
    public static final String JSON_STATIC_PREFIX = "json_static-";

    /**
     * Default maximum number of results from JSON static file sources.
     */
    public static final int JSON_SUGGESTIONS_LIMIT = 8;

    @Autowired
    protected SolrSearchCore solrSearchCore;

    @Autowired
    protected ConfigurationService configurationService;

    /**
     * In-memory cache for JSON static file data.
     */
    private final Map<String, JsonNode> jsonCache = new ConcurrentHashMap<>();

    protected SolrSuggestService() {
    }

    /**
     * Check whether the given dictionary name is in the configured allowlist.
     * If no allowlist is configured, all dictionaries are allowed.
     *
     * @param dictionary the dictionary name to check
     * @return true if allowed, false otherwise
     */
    public boolean isAllowedDictionary(String dictionary) {
        String[] allowed = configurationService.getArrayProperty(
                "discovery.suggest.allowed-dictionaries");
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        return Arrays.asList(allowed).contains(dictionary);
    }

    /**
     * Get suggestions, routing to the appropriate source based on the dictionary name.
     * Dictionaries prefixed with {@link #JSON_STATIC_PREFIX} are routed to the
     * JSON static file handler. All other dictionaries are routed to the Solr suggest handler.
     *
     * @param query      the current text input
     * @param dictionary the name of the dictionary to search
     * @return a Map structure compatible with Solr suggest response format
     */
    public Map<String, Object> getSuggestions(String query, String dictionary) {
        if (dictionary.startsWith(JSON_STATIC_PREFIX)) {
            String filename = dictionary.substring(JSON_STATIC_PREFIX.length());
            return getJsonStaticSuggestions(query, filename, dictionary);
        }
        return getSolrSuggestions(query, dictionary);
    }

    /**
     * Get a list of suggested terms from the Solr suggest request handler.
     *
     * @param query      the current text input
     * @param dictionary the name of the Solr suggest dictionary to search
     * @return simple serialised JSON containing Solr suggest results
     */
    public Map<String, Object> getSolrSuggestions(String query, String dictionary) {
        SolrClient solrClient = solrSearchCore.getSolr();
        try {
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.set("suggest", true);
            solrQuery.set("suggest.q", query);
            solrQuery.set("suggest.dictionary", dictionary);
            solrQuery.setRequestHandler("/suggest");

            QueryResponse response = solrClient.query(solrQuery);
            ObjectMapper mapper = new ObjectMapper();
            String json = response.jsonStr();
            Map<String, Object> result = mapper.readValue(json,
                    new TypeReference<Map<String, Object>>() { });
            return deduplicateSuggestions(result, dictionary);
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException("Unable to retrieve suggest response.", e);
        }
    }

    /**
     * Get suggestions from a JSON static file on the classpath.
     * The JSON file is expected to be a flat object where keys are display labels
     * and values are stored codes (e.g. {@code {"English": "eng", "French": "fra"}}).
     *
     * The response is formatted to match the Solr suggest response structure for
     * uniform frontend handling.
     *
     * @param query      the search text
     * @param filename   the JSON filename (without prefix)
     * @param dictionary the full dictionary name (including prefix)
     * @return a Map mimicking the Solr suggest response format
     */
    public Map<String, Object> getJsonStaticSuggestions(String query, String filename,
                                                        String dictionary) {
        JsonNode jsonNode = loadJsonFile(filename);
        if (jsonNode == null) {
            log.warn("Could not load JSON static file: {}", filename);
            return buildEmptySuggestResponse(dictionary, query);
        }

        List<Map<String, Object>> suggestions = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        Iterator<String> fieldNames = jsonNode.fieldNames();
        int count = 0;

        while (fieldNames.hasNext() && count < JSON_SUGGESTIONS_LIMIT) {
            String key = fieldNames.next();
            if (key.toLowerCase().contains(lowerQuery)) {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("term", key);
                suggestion.put("weight", 1);
                suggestion.put("payload", jsonNode.get(key).asText());
                suggestions.add(suggestion);
                count++;
            }
        }

        return buildSuggestResponse(dictionary, query, suggestions);
    }

    /**
     * Apply value formatting using a configured separator for the given dictionary.
     * If a separator is configured, the stored value is split and the second part
     * (original display value) is used as the term.
     *
     * @param dictionary the dictionary name
     * @param term       the raw term value
     * @return the formatted term, or the original term if no separator is configured
     */
    public String formatValue(String dictionary, String term) {
        String separatorKey = "discovery.suggest.separator." + dictionary;
        String separator = configurationService.getProperty(separatorKey);
        if (StringUtils.isNotBlank(separator)) {
            String[] parts = term.split(separator);
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        return term;
    }

    /**
     * Load a JSON file from the classpath and cache the result.
     *
     * @param filename the filename to load
     * @return the parsed JsonNode, or null if loading fails
     */
    private JsonNode loadJsonFile(String filename) {
        return jsonCache.computeIfAbsent(filename, fn -> {
            try {
                ClassPathResource resource = new ClassPathResource(fn);
                InputStream inputStream = resource.getInputStream();
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readTree(inputStream);
            } catch (IOException e) {
                log.error("Failed to load JSON static file: {}", fn, e);
                return null;
            }
        });
    }

    /**
     * Remove duplicate suggestions from a Solr suggest response.
     * Duplicates are identified by their term value.
     *
     * @param response   the raw Solr suggest response map
     * @param dictionary the dictionary name
     * @return the response with duplicates removed
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deduplicateSuggestions(Map<String, Object> response,
                                                       String dictionary) {
        try {
            Map<String, Object> suggest = (Map<String, Object>) response.get("suggest");
            if (suggest == null) {
                return response;
            }
            Map<String, Object> dictMap = (Map<String, Object>) suggest.get(dictionary);
            if (dictMap == null) {
                return response;
            }
            for (Map.Entry<String, Object> entry : dictMap.entrySet()) {
                Map<String, Object> termData = (Map<String, Object>) entry.getValue();
                List<Map<String, Object>> suggestions =
                        (List<Map<String, Object>>) termData.get("suggestions");
                if (suggestions != null) {
                    List<Map<String, Object>> deduped = new ArrayList<>();
                    Map<String, Boolean> seen = new HashMap<>();
                    for (Map<String, Object> s : suggestions) {
                        String term = String.valueOf(s.get("term"));
                        String formatted = formatValue(dictionary, term);
                        if (!seen.containsKey(formatted)) {
                            seen.put(formatted, true);
                            // Replace term with formatted value
                            Map<String, Object> copy = new LinkedHashMap<>(s);
                            copy.put("term", formatted);
                            deduped.add(copy);
                        }
                    }
                    termData.put("suggestions", deduped);
                    termData.put("numFound", deduped.size());
                }
            }
        } catch (ClassCastException e) {
            log.warn("Unexpected structure in suggest response during deduplication", e);
        }
        return response;
    }

    /**
     * Build a response in the Solr suggest response format.
     *
     * @param dictionary  the dictionary name
     * @param query       the original query
     * @param suggestions the list of suggestion entries
     * @return a Map mimicking Solr suggest response
     */
    private Map<String, Object> buildSuggestResponse(String dictionary, String query,
                                                      List<Map<String, Object>> suggestions) {
        Map<String, Object> termData = new LinkedHashMap<>();
        termData.put("numFound", suggestions.size());
        termData.put("suggestions", suggestions);

        Map<String, Object> dictData = new LinkedHashMap<>();
        dictData.put(query, termData);

        // Strip prefix from dictionary name for consistent response format
        String dictName = dictionary.startsWith(JSON_STATIC_PREFIX)
                ? dictionary.substring(JSON_STATIC_PREFIX.length()) : dictionary;

        Map<String, Object> suggestData = new LinkedHashMap<>();
        suggestData.put(dictName, dictData);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("suggest", suggestData);
        return response;
    }

    /**
     * Build an empty suggest response.
     *
     * @param dictionary the dictionary name
     * @param query      the query
     * @return an empty response in Solr suggest format
     */
    private Map<String, Object> buildEmptySuggestResponse(String dictionary, String query) {
        return buildSuggestResponse(dictionary, query, new ArrayList<>());
    }
}
