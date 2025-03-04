package org.dspace.app.rest.model;

import org.dspace.app.rest.RestResourceController;

public class S3DirectDownloadDTORest extends BaseObjectRest<Integer> {
    public static final String NAME = "s3directdownload";
    public static final String CATEGORY = RestAddressableModel.CORE;

    private String url;
    private String bitstreamName;

    public S3DirectDownloadDTORest() {
    }

    public S3DirectDownloadDTORest(String url, String bitstreamName) {
        this.url = url;
        this.bitstreamName = bitstreamName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBitstreamName() {
        return bitstreamName;
    }

    public void setBitstreamName(String bitstreamName) {
        this.bitstreamName = bitstreamName;
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
