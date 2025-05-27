package org.dspace.app.rest;

public class InitiateResponse {
    public String uploadId;
    public String key;
    public InitiateResponse(String uploadId, String key) { this.uploadId = uploadId; this.key = key; }
}
