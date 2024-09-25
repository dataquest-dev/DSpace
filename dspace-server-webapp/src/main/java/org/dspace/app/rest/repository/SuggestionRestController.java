/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.VocabularyEntryRest;
import org.dspace.app.rest.model.hateoas.VocabularyEntryResource;
import org.dspace.app.rest.utils.Utils;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns VocabularyEntries that contain searchValue. The search is performed on the specific index that is defined by
 * the `autocompleteCustom` parameter in the `submission-forms.xml`.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionRestController extends AbstractDSpaceRestRepository {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(SuggestionRestController.class);

    private static final String AUTOCOMPLETE_CUSTOM_CFG_FORMAT_PREFIX = "autocomplete.custom.separator.";
    private static final String AUTOCOMPLETE_CUSTOM_SOLR_PREFIX = "solr-";
    private static final String AUTOCOMPLETE_CUSTOM_JSON_PREFIX = "json_static-";
    private static final int JSON_SUGGESTIONS_LIMIT = 8;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ConfigurationService configurationService;

    Map<String, JsonNode> jsonSuggestions = new HashMap<>();

    private List<VocabularyEntryRest> getSuggestions(String autocompleteCustom, String searchValue, String prefix) {
        String normalizedAutocompleteCustom = this.removeAutocompleteCustomPrefix(prefix, autocompleteCustom);
        String normalizedSearchValue = searchValue.trim();
        // Create a list of VocabularyEntryRest objects that will be filtered from duplicate values and returned
        // as a response.
        List<VocabularyEntryRest> results = new ArrayList<>();

        if (prefix.equals(AUTOCOMPLETE_CUSTOM_SOLR_PREFIX)) {
            try {
                results = loadSuggestionsFromSolr(normalizedAutocompleteCustom, normalizedSearchValue, results);
            } catch (SearchServiceException e) {
                e.printStackTrace();
            }
        } else if (prefix.equals(AUTOCOMPLETE_CUSTOM_JSON_PREFIX)) {

            results = loadSuggestionsFromJson(normalizedAutocompleteCustom, normalizedSearchValue, results);
        }

        return results;

    }

    private List<VocabularyEntryRest> loadSuggestionsFromJson(String autocompleteCustom, String searchValue,
                                                   List<VocabularyEntryRest> results) {
        try {
            // Load the JSON data
            JsonNode jsonData;
            if (!jsonSuggestions.containsKey(autocompleteCustom)) {
                JsonNode loadedJsonSuggestions = loadJsonFromFile(autocompleteCustom);
                jsonData = loadedJsonSuggestions;
                jsonSuggestions.put(autocompleteCustom, loadedJsonSuggestions);
            } else {
                jsonData = jsonSuggestions.get(autocompleteCustom);
            }

            if (jsonData == null) {
                log.warn("Cannot load JSON suggestions from file: {}", autocompleteCustom);
                return results;
            }

            // Search for a specific key
            results = searchByKey(jsonData, searchValue, results);

        } catch (IOException e) {
            log.error("Error while loading JSON suggestions from file: {} because: {}", autocompleteCustom,
                    e.getMessage());
        }
        return results;
    }

    private List<VocabularyEntryRest> loadSuggestionsFromSolr(String autocompleteCustom, String searchValue,
                                                   List<VocabularyEntryRest> results)
            throws SearchServiceException {
        Context context = obtainContext();
        // Create a DiscoverQuery object that will be used to search for the results.
        DiscoverQuery discoverQuery = new DiscoverQuery();
        // TODO - search facets and process facet results instead of indexable objects
        discoverQuery.setMaxResults(500);
        // return only metadata field values
        discoverQuery.addSearchField(autocompleteCustom);

        Utils.normalizeDiscoverQuery(discoverQuery, searchValue, autocompleteCustom);

        // Search for the results
        DiscoverResult searchResult = searchService.search(context, discoverQuery);

        // Iterate over all indexable objects in the search result. We need indexable object to get search documents.
        // Each search document contains values from the specific index.
        searchResult.getIndexableObjects().forEach(object -> {
            if (!(object instanceof IndexableItem)) {
                return;
            }
            IndexableItem item = (IndexableItem) object;
            // Get all search documents for the item.
            searchResult.getSearchDocument(item).forEach((searchDocument) -> {
                VocabularyEntryRest vocabularyEntryRest = new VocabularyEntryRest();
                // All values from Item's specific index - it could contain values we are not looking for.
                // The must be filtered out.
                List<String> docValues = searchDocument.getSearchFieldValues(autocompleteCustom);

                // Filter values that contain searchValue
                List<String> filteredValues = docValues.stream()
                        .filter(value -> value.contains(searchValue))
                        .collect(Collectors.toList());

                // Add filtered values to the results. It contains only values that contain searchValue.
                filteredValues.forEach(value -> {
                    vocabularyEntryRest.setDisplay(value);
                    vocabularyEntryRest.setValue(value);
                    results.add(vocabularyEntryRest);
                });
            });
        });

        return results;
    }

    // Load JSON from resources and return as JsonNode
    public JsonNode loadJsonFromFile(String filePath) throws IOException {
        // Load the file from the resources folder
        ClassPathResource resource = new ClassPathResource(filePath);

        // Use Jackson ObjectMapper to read the JSON file
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readTree(resource.getInputStream());
    }

    // Search by key in the loaded JSON data
    public List<VocabularyEntryRest> searchByKey(JsonNode jsonNode, String searchKey, List<VocabularyEntryRest> results) {
//        List<String> matchingItems = new ArrayList<>();

        // Iterate over all fields (keys) in the JSON object
        Iterator<String> fieldNames = jsonNode.fieldNames();
        while (fieldNames.hasNext() && results.size() < JSON_SUGGESTIONS_LIMIT) {
            String key = fieldNames.next();

            // If the key matches or contains the search term (case-insensitive)
            if (key.toLowerCase().contains(searchKey.toLowerCase())) {
                // Add key-value pair to the result
                VocabularyEntryRest vocabularyEntryRest = new VocabularyEntryRest();
                vocabularyEntryRest.setDisplay(key);
                vocabularyEntryRest.setValue(jsonNode.get(key).asText());
                results.add(vocabularyEntryRest);
            }
        }
        return results;
    }

    /**
     * Returns a list of VocabularyEntryRest objects that contain values that contain searchValue.
     * The search is performed on the specific index that is defined by the autocompleteCustom parameter.
     */
    @PreAuthorize("permitAll()")
    @RequestMapping(method = RequestMethod.GET)
    public PagedModel<VocabularyEntryResource> filter(@Nullable HttpServletRequest request,
                                            @Nullable Pageable optionalPageable,
                                            @RequestParam(name = "autocompleteCustom", required = false)
                                                          String autocompleteCustom,
                                            @RequestParam(name = "searchValue", required = false) String searchValue,
                                            PagedResourcesAssembler assembler) throws SearchServiceException {
        Pageable pageable = utils.getPageable(optionalPageable);
        List<VocabularyEntryRest> results = null;
        if (autocompleteCustom.startsWith(AUTOCOMPLETE_CUSTOM_JSON_PREFIX)) {
            results = getSuggestions(autocompleteCustom, searchValue, AUTOCOMPLETE_CUSTOM_JSON_PREFIX);
        } else if (!autocompleteCustom.startsWith(AUTOCOMPLETE_CUSTOM_SOLR_PREFIX)) {
            results =  getSuggestions(autocompleteCustom, searchValue, AUTOCOMPLETE_CUSTOM_SOLR_PREFIX);
        } else {
            // TODO some log and return some response entity
            return null;
        }

        if (CollectionUtils.isEmpty(results)) {
            // TODO some log and return some response entity
            return null;
        }

        // Remove duplicates from the results
        List<VocabularyEntryRest> finalResults = results.stream()
                .filter(Utils.distinctByKey(VocabularyEntryRest::getValue))
                .collect(Collectors.toList());

        // Format the values according to the configuration
        finalResults = finalResults.stream()
                .map(ver -> formatValue(ver, autocompleteCustom))
                .collect(Collectors.toList());

        // Create a page with the final results. The page is needed for the better processing in the frontend.
        Page<VocabularyEntryRest> resultsPage = new PageImpl<>(finalResults, pageable, finalResults.size());
        PagedModel<VocabularyEntryResource> response = assembler.toModel(resultsPage);
        return response;
    }

    /**
     * Format the value according to the configuration.
     * The result value could consist of multiple parts separated by a separator. Keep the correct part separated by
     * the separator loaded from the configuration.
     */
    private VocabularyEntryRest formatValue(VocabularyEntryRest ver, String autocompleteCustom) {
        if (StringUtils.isEmpty(ver.getValue()) || StringUtils.isEmpty(autocompleteCustom)) {
            return ver;
        }

        // Load separator from the configuration `autocomplete.custom.separator.<autocompleteCustom>
        String separator = configurationService.getProperty(AUTOCOMPLETE_CUSTOM_CFG_FORMAT_PREFIX + autocompleteCustom);
        if (StringUtils.isEmpty(separator)) {
            return ver;
        }

        // Split the value by the separator and keep the correct - second part
        String[] parts = ver.getValue().split(separator);
        if (parts.length > 1) {
            String formattedValue = parts[1].trim(); // The correct value is the second part
            ver.setValue(formattedValue);
            ver.setDisplay(formattedValue);
        }

        return ver;
    }

    private String removeAutocompleteCustomPrefix(String prefix, String autocompleteCustom) {
        return autocompleteCustom.replace(prefix, "");
    }
}
