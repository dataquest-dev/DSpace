/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.harvest.ore;

/**
 * Implemented by crosswalks that fetch remote content and therefore need the egress rules of the collection
 * being harvested. A crosswalk that is never given a policy must fall back to
 * {@link OreEgressPolicy#strictest(org.dspace.services.ConfigurationService)}.
 */
public interface HarvestPolicyAware {

    /**
     * @param policy the rules to apply to remote fetches during this harvest
     */
    void setOreEgressPolicy(OreEgressPolicy policy);
}
