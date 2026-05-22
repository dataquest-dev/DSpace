/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model.step;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

/**
 * DTO of the CLARIN resource license selected for an in-progress submission.
 * Backed by the item metadata {@code dc.rights}, {@code dc.rights.uri} and
 * {@code dc.rights.label}. Distinct from {@link DataLicense}, which represents
 * the deposit {@code LICENSE/license.txt} bitstream.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinDataLicenseRest implements SectionData {

    /**
     * Display name of the CLARIN license (value of {@code dc.rights}).
     */
    private String name;

    /**
     * URI / definition of the CLARIN license (value of {@code dc.rights.uri}).
     */
    @JsonProperty(access = Access.READ_ONLY)
    private String definition;

    /**
     * Short label of the CLARIN license (value of {@code dc.rights.label}).
     */
    @JsonProperty(access = Access.READ_ONLY)
    private String label;

    /**
     * Whether the CLARIN license is granted, i.e. all three metadata fields
     * ({@code dc.rights}, {@code dc.rights.uri}, {@code dc.rights.label}) are
     * present on the item.
     */
    private boolean granted = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isGranted() {
        return granted;
    }

    public void setGranted(boolean granted) {
        this.granted = granted;
    }
}
