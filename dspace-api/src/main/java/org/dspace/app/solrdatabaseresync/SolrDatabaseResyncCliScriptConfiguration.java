/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.solrdatabaseresync;

import org.apache.commons.cli.Options;
<<<<<<< HEAD
import org.dspace.core.Context;
=======
>>>>>>> dspace-7.6.1
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * The {@link ScriptConfiguration} for the {@link SolrDatabaseResyncCli} script.
 */
public class SolrDatabaseResyncCliScriptConfiguration extends ScriptConfiguration<SolrDatabaseResyncCli> {
    private Class<SolrDatabaseResyncCli> dspaceRunnableClass;

    @Override
    public Class<SolrDatabaseResyncCli> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<SolrDatabaseResyncCli> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
<<<<<<< HEAD
    public boolean isAllowedToExecute(Context context) {
        return true;
    }

    @Override
=======
>>>>>>> dspace-7.6.1
    public Options getOptions() {
        if (options == null) {
            options = new Options();
        }
        return options;
    }
}
