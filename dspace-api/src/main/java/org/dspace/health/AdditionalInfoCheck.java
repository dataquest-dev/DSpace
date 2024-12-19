/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import org.dspace.health.additionalUtilities.Info;

public class AdditionalInfoCheck extends Check {

    @Override
    protected String run(ReportInfo ri) {
        String output = "";

        output += String.format(
                "Server uptime: %s\n", Info.get_proc_uptime());
        output += String.format(
                "JVM uptime: %s\n", Info.get_jvm_uptime());
        output += String.format(
                "Testing build time: %s\n", Info.get_build_time());

        output += "\n\n";

        output += "Example url 1:       https://dev-5.pc:8443/repository/home\n";
        output += "Example url 2:       https://dev-5.pc:85/repository/home\n";
        return output;
    }
}
