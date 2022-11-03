/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.service.clarin;

import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserMetadata;
import org.dspace.core.Context;

public interface ClarinUserMetadataService {

    ClarinUserMetadata create(Context context) throws SQLException, AuthorizeException;

    ClarinUserMetadata find(Context context, int valueId) throws SQLException;
    void delete(Context context, ClarinUserMetadata clarinUserMetadata) throws SQLException, AuthorizeException;
}
