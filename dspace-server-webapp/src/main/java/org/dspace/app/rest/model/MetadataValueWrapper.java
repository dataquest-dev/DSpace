/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
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
