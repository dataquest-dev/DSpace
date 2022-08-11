/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service;

import java.sql.SQLException;

import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

public interface DspaceObjectClarinService<T extends DSpaceObject> {
    public Community getPrincipalCommunity(Context context, DSpaceObject dso) throws SQLException;
}
