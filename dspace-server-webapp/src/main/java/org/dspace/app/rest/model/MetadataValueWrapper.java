package org.dspace.app.rest.model;

import org.dspace.content.MetadataValue;

public class MetadataValueWrapper {

    MetadataValue metadataValue;

    public MetadataValueWrapper() {}

    public MetadataValue getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(MetadataValue metadataValue) {
        this.metadataValue = metadataValue;
    }
}
