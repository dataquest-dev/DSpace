package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.model.MetadataValueWithFieldRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.MetadataValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converter to translate between domain {@link MetadataValue}s and {@link MetadataValueWithFieldRest} representations.
 */
@Component
public class MetadataValueRestConverter implements DSpaceConverter<MetadataValueRest, MetadataValueWithFieldRest> {
    @Autowired
    private ConverterService converter;

    @Override
    public MetadataValueWithFieldRest convert(MetadataValueRest metadataValueWithFieldRest, Projection projection) {
        MetadataValueWithFieldRest metadataValueRest = new MetadataValueWithFieldRest();
        metadataValueRest.setValue(metadataValueWithFieldRest.getValue());
        metadataValueRest.setLanguage(metadataValueWithFieldRest.getLanguage());
        metadataValueRest.setAuthority(metadataValueWithFieldRest.getAuthority());
        metadataValueRest.setConfidence(metadataValueWithFieldRest.getConfidence());
        metadataValueRest.setPlace(metadataValueWithFieldRest.getPlace());
        return metadataValueRest;
    }

    @Override
    public Class<MetadataValueRest> getModelClass() {
        return MetadataValueRest.class;
    }
}
