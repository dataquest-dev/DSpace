/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

public class OAIPMHCheck extends Check {
    protected static final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    protected String run(ReportInfo ri) {
        String output = "";
        String dspace_dir = configurationService.getProperty("dspace.dir");
        System.out.println("Dspace dir " + dspace_dir);
        String dspace_url = configurationService.getProperty("dspace.server.url");
        System.out.println("Dspace url " + dspace_url);
        String oaiurl = dspace_url + "/oai/request";
        System.out.println("Dspace oai " + oaiurl);
        output += String.format("Trying [%s]\n", oaiurl);
        //output += IOUtils.run(new File(dspace_dir + "/bin/"), new String[]{
          //      "python", "./validators/oai_pmh/validate.py", oaiurl});
        return output;
    }
}
