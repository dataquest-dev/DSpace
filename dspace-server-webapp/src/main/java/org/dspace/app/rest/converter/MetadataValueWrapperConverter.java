package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.MetadataValueWrapper;
import org.dspace.app.rest.model.MetadataValueWrapperRest;
import org.dspace.app.rest.projection.Projection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This is the converter from/to the MetadataField in the DSpace API data model and
 * the REST data model
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@Component
public class MetadataValueWrapperConverter implements DSpaceConverter<MetadataValueWrapper, MetadataValueWrapperRest>{

    @Autowired
    private ConverterService converter;

    @Override
    public MetadataValueWrapperRest convert(MetadataValueWrapper metadataValueWrapper, Projection projection) {
        MetadataValueWrapperRest metadataValueWithFieldRest = new MetadataValueWrapperRest();
        metadataValueWithFieldRest.setProjection(projection);
        metadataValueWithFieldRest.setId(metadataValueWrapper.getMetadataValue().getID());
        metadataValueWithFieldRest.setValue(metadataValueWrapper.getMetadataValue().getValue());
        metadataValueWithFieldRest.setLanguage(metadataValueWrapper.getMetadataValue().getLanguage());
        metadataValueWithFieldRest.setAuthority(metadataValueWrapper.getMetadataValue().getAuthority());
        metadataValueWithFieldRest.setConfidence(metadataValueWrapper.getMetadataValue().getConfidence());
        metadataValueWithFieldRest.setPlace(metadataValueWrapper.getMetadataValue().getPlace());
        metadataValueWithFieldRest.setField(converter.toRest(metadataValueWrapper.getMetadataValue().getMetadataField(), projection));
        return metadataValueWithFieldRest;
    }

    @Override
    public Class<MetadataValueWrapper> getModelClass() {
        return MetadataValueWrapper.class;
    }
}
