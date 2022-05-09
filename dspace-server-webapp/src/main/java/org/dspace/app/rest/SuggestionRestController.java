package org.dspace.app.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.MetadataFieldRest;
import org.dspace.app.rest.repository.ItemTemplateItemOfLinkRepository;
import org.dspace.app.rest.repository.MetadataFieldRestRepository;
import org.dspace.app.rest.repository.TemplateItemRestRepository;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.TemplateVariable;
import org.springframework.hateoas.TemplateVariables;
import org.springframework.hateoas.UriTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/core/suggestions")
public class SuggestionRestController {

    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataFieldRestRepository.class);
    public static final String CATEGORY = "suggestions";
    public static final String PARAM_METADATA = "metadataField";
    public static final String PARAM_SEARCH_VALUE = "searchValue";


    @Autowired
    private Utils utils;

    @Autowired
    private ItemService itemService;

    @Autowired
    private TemplateItemRestRepository templateItemRestRepository;

    @Autowired
    private ConverterService converter;

    @Autowired
    private ItemTemplateItemOfLinkRepository itemTemplateItemOfLinkRepository;

    @Autowired
    private DiscoverableEndpointsService discoverableEndpointsService;

    @Autowired
    private SearchService searchService;

    @RequestMapping(method = RequestMethod.GET)
    public Page<MetadataFieldRest> getSuggestions(HttpServletRequest request,
                                                  @RequestParam(PARAM_METADATA) String metadataField,
                                                  @RequestParam(PARAM_SEARCH_VALUE) String searchValue,
                                                  Pageable pageable) {
        Context context = ContextUtil.obtainContext(request);
        List<MetadataValue> matchingMetadataValues = new ArrayList<>();

        List<String> metadata = List.of(metadataField.split("\\."));
        // metadataField validation
        if (StringUtils.isNotBlank(metadataField)) {
            if (metadata.size() > 3) {
                throw new IllegalArgumentException("Query param should not contain more than 2 dot (.) separators, " +
                        "forming schema.element.qualifier");
            }
        }

        // Find matches in Solr Search core
        DiscoverQuery discoverQuery =
                this.createDiscoverQuery(metadataField, searchValue, pageable);

        try {
            DiscoverResult searchResult = searchService.search(context, null, discoverQuery);
            for (IndexableObject object : searchResult.getIndexableObjects()) {
                if (object instanceof IndexableItem) {
                    matchingMetadataValues.addAll(itemService.getMetadataByMetadataString(
                            ((IndexableItem) object).getIndexedObject(), metadataField));
                }
            }
        } catch (SearchServiceException e) {
            log.error("Error while searching with Discovery", e);
            throw new IllegalArgumentException("Error while searching with Discovery: " + e.getMessage());
        }

        return converter.toRestPage(matchingMetadataValues, pageable, utils.obtainProjection());
    }

    private DiscoverQuery createDiscoverQuery(String metadataField, String searchValue, Pageable pageable) {
        DiscoverQuery discoverQuery = new DiscoverQuery();
        discoverQuery.setQuery(metadataField+":"+"*"+searchValue+"*");
        discoverQuery.setStart(Math.toIntExact(pageable.getOffset()));
        discoverQuery.setMaxResults(pageable.getPageSize());
        // return searching metadata field only
        discoverQuery.addSearchField(metadataField);

        return discoverQuery;
    }

//    @Override
//    public void afterPropertiesSet() throws Exception {
//        discoverableEndpointsService
//                .register(this,
//                        Arrays.asList(
//                                new Link(new UriTemplate("/api/" + CATEGORY,
//                                        new TemplateVariables(
//                                                new TemplateVariable(PARAM_METADATA,
//                                                        TemplateVariable.VariableType.REQUEST_PARAM),
//                                                new TemplateVariable(PARAM_SEARCH_VALUE,
//                                                        TemplateVariable.VariableType.REQUEST_PARAM))),
//                                        CATEGORY)));
//    }
}