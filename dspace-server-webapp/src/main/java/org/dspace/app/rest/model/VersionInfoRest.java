/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

/**
 * The VersionInfo REST Resource. This class wraps the VersionInfo object for the REST API.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class VersionInfoRest extends RestAddressableModel implements RestModel {
    private static final long serialVersionUID = -3415049466402327251L;
    public static final String NAME = "versioninfo";

    private String commitHash;
    private String date;
    private String buildRunUrl;

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBuildRunUrl() {
        return buildRunUrl;
    }

    public void setBuildRunUrl(String buildRunUrl) {
        this.buildRunUrl = buildRunUrl;
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getCategory() {
        return null;
    }

    @Override
    public Class getController() {
        return null;
    }
}
