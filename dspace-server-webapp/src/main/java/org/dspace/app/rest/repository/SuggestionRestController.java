package org.dspace.app.rest.repository;

import org.dspace.app.rest.model.VocabularyEntryRest;
import org.dspace.app.rest.model.hateoas.VocabularyEntryResource;
import org.dspace.app.rest.utils.Utils;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
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

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Returns VocabularyEntries
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionRestController extends AbstractDSpaceRestRepository {

    @Autowired
    private SearchService searchService;


    @PreAuthorize("permitAll()")
    @RequestMapping(method = RequestMethod.GET)
    public PagedModel<VocabularyEntryResource> filter(@Nullable HttpServletRequest request,
                                            @Nullable Pageable optionalPageable,
                                            @RequestParam(name = "autocompleteCustom", required = false) String autocompleteCustom,
                                            @RequestParam(name = "searchValue", required = false) String searchValue,
                                            PagedResourcesAssembler assembler) throws SearchServiceException {
        Context context = obtainContext();
        Pageable pageable = utils.getPageable(optionalPageable);

        // Create a DiscoverQuery object that will be used to search for the results.
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setQuery(autocompleteCustom + ":" + "*" + searchValue + "*");
        discoverQuery.setStart(Math.toIntExact(pageable.getOffset()));
        discoverQuery.setMaxResults(pageable.getPageSize());
        // return only metadata field values
        discoverQuery.addSearchField(autocompleteCustom);

        Utils.normalizeDiscoverQuery(discoverQuery, searchValue, autocompleteCustom);

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
                List<String> docValues = searchDocument.getSearchFieldValues(autocompleteCustom);

                // Filter values that contain searchValue
                List<String> filteredValues = docValues.stream()
                        .filter(value -> value.toLowerCase().contains(searchValue.toLowerCase()))
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

        // Create a page with the final results. The page is needed for the better processing in the frontend.
        Page<VocabularyEntryRest> resultsPage = new PageImpl<>(finalResults, pageable, finalResults.size());
        PagedModel<VocabularyEntryResource> response = assembler.toModel(resultsPage);
        return response;
    }
}
