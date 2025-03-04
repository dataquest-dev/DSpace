package org.dspace.app.rest.model;

public class S3DirectDownloadDTO {

    private String url;

    private String bitstreamName;

    public S3DirectDownloadDTO() {
    }

    public S3DirectDownloadDTO(String url, String bitstreamName) {
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
}
