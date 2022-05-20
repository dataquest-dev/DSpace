package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.MetadataValueWithFieldRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.MetadataValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

///**
// * Converter to translate between domain {@link MetadataValue}s and {@link MetadataValueWithFieldRest} representations.
// */
//@Component
//public class MetadataValueWithFieldConverter implements DSpaceConverter<MetadataValue, MetadataValueWithFieldRest> {
//    @Autowired
//    private ConverterService converter;
//
//    @Override
//    public MetadataValueWithFieldRest convert(MetadataValue metadataValue, Projection projection) {
//        MetadataValueWithFieldRest metadataValueWithFieldRest = new MetadataValueWithFieldRest();
//        metadataValueWithFieldRest.setValue(metadataValue.getValue());
//        metadataValueWithFieldRest.setLanguage(metadataValue.getLanguage());
//        metadataValueWithFieldRest.setAuthority(metadataValue.getAuthority());
//        metadataValueWithFieldRest.setConfidence(metadataValue.getConfidence());
//        metadataValueWithFieldRest.setPlace(metadataValue.getPlace());
//        metadataValueWithFieldRest.setField(converter.toRest(metadataValue.getMetadataField(), projection));
//        return metadataValueWithFieldRest;
//    }
//
//    @Override
//    public Class<MetadataValue> getModelClass() {
//        return MetadataValue.class;
//    }
//}
