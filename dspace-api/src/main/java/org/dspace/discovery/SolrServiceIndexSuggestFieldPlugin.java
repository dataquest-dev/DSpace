/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Index metadata fields into {@code *_suggest} dynamic Solr fields to be
 * used by the Solr suggest handler ({@code /suggest}).
 *
 * Fields to index are configured via {@code discovery.suggest.field.*}
 * properties in {@code discovery.cfg}. For example:
 * <pre>
 *   discovery.suggest.field.subject = dc.subject
 *   discovery.suggest.field.authors = dc.contributor.author
 * </pre>
 *
 * @author Kim Shepherd
 * @author Milan Majchrak (CLARIN extensions)
 */
public class SolrServiceIndexSuggestFieldPlugin implements SolrServiceIndexPlugin {

    private static final Logger log = LogManager.getLogger();

    private static final String SUGGEST_FIELD_SUFFIX = "_suggest";

    @Autowired(required = true)
    protected ItemService itemService;

    @Autowired
    protected ConfigurationService configurationService;

    @Override
    public void additionalIndex(Context context, IndexableObject indexableObject,
                                SolrInputDocument document) {
        if (!(indexableObject instanceof IndexableItem)) {
            return;
        }
        Item item = ((IndexableItem) indexableObject).getIndexedObject();

        String[] suggestFields = configurationService.getArrayProperty("discovery.suggest.field");
        if (suggestFields == null || suggestFields.length == 0) {
            return;
        }

        for (String configEntry : suggestFields) {
            // Expected format: "dictName = dc.some.field" or just "dc.some.field"
            String dictName;
            String metadataField;
            if (configEntry.contains("=")) {
                String[] parts = configEntry.split("=", 2);
                dictName = parts[0].trim();
                metadataField = parts[1].trim();
            } else {
                metadataField = configEntry.trim();
                dictName = metadataField.replace(".", "_");
            }

            String solrField = dictName + SUGGEST_FIELD_SUFFIX;

            // Parse metadata field schema (dc.contributor.author -> dc, contributor, author)
            String[] mdParts = metadataField.split("\\.");
            String schema = mdParts.length > 0 ? mdParts[0] : Item.ANY;
            String element = mdParts.length > 1 ? mdParts[1] : Item.ANY;
            String qualifier = mdParts.length > 2 ? mdParts[2] : Item.ANY;

            List<MetadataValue> values = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
            for (MetadataValue mv : values) {
                if (mv.getValue() != null) {
                    document.addField(solrField, mv.getValue());
                    if (log.isTraceEnabled()) {
                        log.trace("Indexed {} -> {} = {}", metadataField, solrField, mv.getValue());
                    }
                }
            }
        }
    }
}
