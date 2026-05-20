/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.script;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * Script configuration for {@link OrcidAuthorityAssign}.
 *
 * This script assigns ORCID-based authority values to dc.contributor.author metadata
 * by matching author names found in dc.identifier.orcid metadata entries.
 *
 * @param  <T> the OrcidAuthorityAssign type
 */
public class OrcidAuthorityAssignScriptConfiguration<T extends OrcidAuthorityAssign>
        extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            super.options = new Options();
        }
        return options;
    }
}
