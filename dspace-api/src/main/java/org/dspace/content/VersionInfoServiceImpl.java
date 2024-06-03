/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.logging.log4j.Logger;
import org.dspace.content.clarin.VersionInfo;
import org.dspace.content.service.clarin.VersionInfoService;
import org.springframework.beans.factory.annotation.Value;

/**
 * This class provides the implementation of the VersionInfoService interface.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class VersionInfoServiceImpl implements VersionInfoService {

    /**
     * The content of the temporary file that is used when the version file cannot be loaded. It is only for
     * presentation purposes.
     */
    private static final String TEMP_FILE_CONTENT = "Git hash: 2718a9273cbb086e129ac720bca4e4ad9c08a451" +
            " Date of commit: 2024-05-24 10:00:12 +0200This info was generated on: 2024-05-24 08:06:45.269719\n" +
            " Build run: https://github.com/dataquest-dev/DSpace/actions/runs/9220644386";
    private static final String GIT_HASH_SEPARATOR = "Git hash:";
    private static final String DATE_SEPARATOR = "Date of commit:";
    private static final String BUILD_RUN_SEPARATOR = "Build run:";
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(VersionInfoServiceImpl.class);

    /**
     * The path to the file containing the version information. The path is set in the clarin-dspace.cfg file.
     */
    @Value("${version.info.file.path:''}")
    private String VERSION_FILE_PATH;

    @Override
    public VersionInfo fetchVersionInfoFromFile() {
        return this.parseContentIntoVersionInfo();
    }

    /**
     * Load the content of the version file into a string.
     */
    private String loadFileContent() {
        try {
            // Read the file content into a string
            return new String(Files.readAllBytes(Paths.get(VERSION_FILE_PATH)));
        } catch (IOException e) {
            log.error("Cannot load version info because of: {}. The temp file content is used instead",
                    e.getMessage());
        }
        return TEMP_FILE_CONTENT;
    }

    /**
     * Parse the content of the version file into a VersionInfo object.
     */
    private VersionInfo parseContentIntoVersionInfo() {
        // Load the file content
        String content = loadFileContent();
        if (content == null) {
            log.warn("Cannot parse version info because the content is null");
            return null;
        }

        // Create a VersionInfo object
        VersionInfo versionInfo = new VersionInfo();

        // Extract the hash
        int hashStartIndex = content.indexOf(GIT_HASH_SEPARATOR) + GIT_HASH_SEPARATOR.length();
        int hashEndIndex = content.indexOf(DATE_SEPARATOR, hashStartIndex);
        versionInfo.setCommitHash(content.substring(hashStartIndex, hashEndIndex).trim());

        // Extract the date
        int dateStartIndex = content.indexOf(DATE_SEPARATOR) + DATE_SEPARATOR.length();
        int dateEndIndex = content.indexOf(BUILD_RUN_SEPARATOR, dateStartIndex);
        versionInfo.setDate(content.substring(dateStartIndex, dateEndIndex).trim());

        // Extract the build run URL
        int buildRunStartIndex = content.indexOf(BUILD_RUN_SEPARATOR) + BUILD_RUN_SEPARATOR.length();
        versionInfo.setBuildRunUrl(content.substring(buildRunStartIndex).trim());

        return versionInfo;
    }
}
