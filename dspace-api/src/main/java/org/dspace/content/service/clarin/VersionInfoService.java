/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service.clarin;

import org.dspace.content.clarin.VersionInfo;

/**
 * This interface provides the methods for fetching the version information of the DSpace instance.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public interface VersionInfoService {

    /**
     * Fetch the version information from the file. The file path is set in the clarin-dspace.cfg file.
     * The property name is `version.info.file.path`.
     */
    VersionInfo fetchVersionInfoFromFile();
}
