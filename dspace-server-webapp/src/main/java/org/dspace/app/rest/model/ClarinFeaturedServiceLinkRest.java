package org.dspace.app.rest.model;

import org.dspace.app.rest.ClarinRefBoxController;
import org.dspace.app.rest.RestResourceController;

public class ClarinFeaturedServiceLinkRest extends BaseObjectRest<Integer> {

    public static final String NAME = "featuredservicelink";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public Class getController() {
        return RestResourceController.class;
    }

    @Override
    public String getType() {
        return NAME;
    }
}
