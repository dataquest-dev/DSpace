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

    // TODO: modify options and their features/properties
    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("i", "info", false,
                    "Show help information.");
            options.addOption("e", "email", true,
                    "Send report to this email address.");
            options.getOption("e").setType(String.class);
            options.addOption("c", "check", true,
                    "Perform only specific check (use index starting from 0).");
            options.getOption("c").setType(String.class);
            options.addOption("f", "for", true,
                    "Report for last N days.");
            options.getOption("f").setType(String.class);
            options.addOption("v", "verbose", false,
                    "Verbose report.");
            options.addOption("o", "output", true,
                    "Save report to the file.");
// short report, max of..,
            super.options =  options;
        }
        return options;
    }
}
