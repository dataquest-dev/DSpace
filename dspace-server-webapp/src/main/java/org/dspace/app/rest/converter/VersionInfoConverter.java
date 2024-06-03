/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import org.dspace.app.rest.model.VersionInfoRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.content.clarin.VersionInfo;
import org.springframework.stereotype.Component;

/**
 * This class' purpose is to convert the VersionInfo object to its REST representation.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
@Component
public class VersionInfoConverter implements DSpaceConverter<VersionInfo, VersionInfoRest> {
    @Override
    public VersionInfoRest convert(VersionInfo modelObject, Projection projection) {
        VersionInfoRest versionInfoRest = new VersionInfoRest();
        versionInfoRest.setProjection(projection);
        versionInfoRest.setCommitHash(modelObject.getCommitHash());
        versionInfoRest.setDate(modelObject.getDate());
        versionInfoRest.setBuildRunUrl(modelObject.getBuildRunUrl());
        return versionInfoRest;
    }

    @Override
    public Class<VersionInfo> getModelClass() {
        return VersionInfo.class;
    }
}
