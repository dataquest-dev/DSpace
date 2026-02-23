/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.healthreport;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * This class represents a HealthReport that is used in the CLI.
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class HealthReportScriptConfiguration<T extends HealthReport> extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableclass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableclass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableclass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("h", "help", false,
                    "Show help information.");
            options.addOption("e", "email", true,
                    "Send report to this email address.");
            options.getOption("e").setType(String.class);
            options.addOption("c", "check", true,
                    String.format("Perform specific check(s) by index (0 to %d). " +
                            "Can be used multiple times, e.g. -c 0 -c 3 -c 4.",
                            HealthReport.getNumberOfChecks() - 1));
            options.getOption("c").setType(String.class);
            options.getOption("c").setArgs(Integer.MAX_VALUE);
            options.addOption("f", "for", true,
                    "Report for last N days (positive integer). Used only in general information for now.");
            options.getOption("f").setType(String.class);
            options.addOption("r", "report", true,
                    "Specify the report file to store the output.");

            super.options =  options;
        }
        return options;
    }
}
