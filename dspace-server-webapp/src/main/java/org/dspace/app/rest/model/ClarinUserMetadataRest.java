package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.dspace.app.rest.RestResourceController;

public class ClarinUserMetadataRest extends BaseObjectRest<Integer> {

    public static final String NAME = "clarinusermetadata";
    public static final String CATEGORY = RestAddressableModel.CORE;

    public String metadataKey;
    public String metadataValue;

    public ClarinUserMetadataRest() {
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    public void setMetadataKey(String metadataKey) {
        this.metadataKey = metadataKey;
    }

    public String getMetadataValue() {
        return metadataValue;
    }

    public void setMetadataValue(String metadataValue) {
        this.metadataValue = metadataValue;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return NAME;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }
}
