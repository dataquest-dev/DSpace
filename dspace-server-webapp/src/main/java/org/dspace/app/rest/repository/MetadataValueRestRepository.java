package org.dspace.app.rest.repository;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.model.MetadataValueWithFieldRest;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.indexobject.IndexableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component(MetadataValueWithFieldRest.CATEGORY + "." + MetadataValueWithFieldRest.NAME)
public class MetadataValueRestRepository extends DSpaceRestRepository<MetadataValueWithFieldRest, Integer> {

    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataValueRestRepository.class);

    @Autowired
    MetadataValueService metadataValueService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ItemService itemService;

    @Autowired
    MetadataFieldService metadataFieldService;

    @Autowired
    MetadataSchemaService metadataSchemaService;

//    @Override
//    @PreAuthorize("permitAll()")
//    public MetadataValueWithFieldRest findOne(Context context, Integer id) {
//        MetadataField metadataField = null;
//        try {
//            metadataField = metadataFieldService.find(context, id);
//        } catch (SQLException e) {
//            throw new RuntimeException(e.getMessage(), e);
//        }
//        if (metadataField == null) {
//            return null;
//        }
//        return converter.toRest(metadataField, utils.obtainProjection());
//    }

    @SearchRestMethod(name = "byValue")
    public Page<MetadataValueWithFieldRest> findByFieldName(@Parameter(value = "schema", required = false) String schemaName,
                                                   @Parameter(value = "element", required = false) String elementName,
                                                   @Parameter(value = "qualifier", required = false) String qualifierName,
                                                   @Parameter(value = "searchValue", required = false) String searchValue,
                                                   Pageable pageable) throws SQLException {
        Context context = obtainContext();

        List<MetadataValue> matchingMetadataValues = new ArrayList<>();

        String separator = ".";
        String metadataField = StringUtils.isNotBlank(schemaName) ? schemaName + separator: "";
        metadataField += StringUtils.isNotBlank(elementName) ? elementName + separator : "";
        metadataField += StringUtils.isNotBlank(qualifierName) ? qualifierName : "";


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

        Page<MetadataValueWithFieldRest> resp = converter.toRestPage(matchingMetadataValues, pageable, utils.obtainProjection());
        return resp;
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

    @Override
    @PreAuthorize("permitAll()")
    public MetadataValueWithFieldRest findOne(Context context, Integer id) {
        MetadataValue metadataValue = null;
        try {
            metadataValue = metadataValueService.find(context, id);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (metadataValue == null) {
            return null;
        }
        return converter.toRest(metadataValue, utils.obtainProjection());
    }

    @Override
    public Page<MetadataValueWithFieldRest> findAll(Context context, Pageable pageable) {
        return converter.toRest(new ArrayList<MetadataValue>(), utils.obtainProjection());
    }

    @Override
    public Class<MetadataValueWithFieldRest> getDomainClass() {
        return MetadataValueWithFieldRest.class;
    }
}
