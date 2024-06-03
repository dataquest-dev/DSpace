/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.clarin;

/**
 * Class that represents the version (commit) information of the DSpace instance.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class VersionInfo {

    /**
     * The hash of the commit.
     */
    private String commitHash;
    /**
     * The date of the commit.
     */
    private String date;
    /**
     * The URL of the build run.
     */
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
}
