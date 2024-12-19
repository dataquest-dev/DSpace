/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import org.dspace.health.additionalUtilities.IOUtils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

import java.io.File;

public class OAIPMHCheck extends Check {
    protected static final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    protected String run(ReportInfo ri) {
        String output = "";
        String dspace_dir = configurationService.getProperty("dspace.dir");
        String dspace_url = configurationService.getProperty("dspace.server.url");
        String oaiurl = dspace_url + "/oai/request";
        output += String.format("Trying [%s]\n", oaiurl);

        File scriptDir = new File(
        "C:\\WorkSpace\\DSpace\\dspace-api\\src\\main\\java\\org\\dspace\\health\\additionalUtilities\\");

        output += IOUtils.runScript(scriptDir, new String[]{
                "python", "validate.py", oaiurl});
        return output;
    }
}
