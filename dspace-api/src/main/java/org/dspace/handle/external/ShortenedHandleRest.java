/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle.external;

/**
 * The shortener's own view of an external handle: what POST and PUT `/api/services/handles` return and what PUT
 * receives. It is the only place where the token leaves the server.
 */
public class ShortenedHandleRest extends HandleRest {

    private String datasetName;
    private String datasetVersion;
    private String query;
    private String token;

    public ShortenedHandleRest() {
    }

    /**
     * Copy the parsed magic URL attributes and add the token.
     * @param handle parsed magic URL
     * @param token the token of that magic URL
     */
    public ShortenedHandleRest(Handle handle, String token) {
        setHandle(handle.getHandle());
        setUrl(handle.url);
        setTitle(handle.title);
        setRepository(handle.repository);
        setSubmitdate(handle.submitdate);
        setReportemail(handle.reportemail);
        setSubprefix(handle.subprefix);
        this.datasetName = handle.datasetName;
        this.datasetVersion = handle.datasetVersion;
        this.query = handle.query;
        this.token = token;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(String datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
