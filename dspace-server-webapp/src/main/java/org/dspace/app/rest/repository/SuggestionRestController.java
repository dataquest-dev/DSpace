/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
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

    private static final String AUTOCOMPLETE_CUSTOM_CFG_FORMAT_PREFIX = "autocomplete.custom.separator.";
    private static final String AUTOCOMPLETE_CUSTOM_SOLR_PREFIX = "solr-";

    @Autowired
    private SearchService searchService;

    @Autowired
    private ConfigurationService configurationService;


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
        Context context = obtainContext();
        Pageable pageable = utils.getPageable(optionalPageable);

        if (!autocompleteCustom.startsWith(AUTOCOMPLETE_CUSTOM_SOLR_PREFIX)) {
            return null;
        }
        String normalizedAutocompleteCustom = autocompleteCustom.replace(AUTOCOMPLETE_CUSTOM_SOLR_PREFIX, "");
        String normalizedSearchValue = searchValue.trim();

        // Create a DiscoverQuery object that will be used to search for the results.
        DiscoverQuery discoverQuery = new DiscoverQuery();
        // TODO - search facets and process facet results instead of indexable objects
        discoverQuery.setMaxResults(500);
        // return only metadata field values
        discoverQuery.addSearchField(normalizedAutocompleteCustom);

        Utils.normalizeDiscoverQuery(discoverQuery, normalizedSearchValue, normalizedAutocompleteCustom);

        // Search for the results
        DiscoverResult searchResult = searchService.search(context, discoverQuery);

        // Create a list of VocabularyEntryRest objects that will be filtered from duplicate values and returned
        // as a response.
        List<VocabularyEntryRest> results = new ArrayList<>();

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
                List<String> docValues = searchDocument.getSearchFieldValues(normalizedAutocompleteCustom);

                // Filter values that contain searchValue
                List<String> filteredValues = docValues.stream()
                        .filter(value -> value.contains(normalizedSearchValue))
                        .collect(Collectors.toList());

                // Add filtered values to the results. It contains only values that contain searchValue.
                filteredValues.forEach(value -> {
                    vocabularyEntryRest.setDisplay(value);
                    vocabularyEntryRest.setValue(value);
                    results.add(vocabularyEntryRest);
                });
            });
        });

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
}
