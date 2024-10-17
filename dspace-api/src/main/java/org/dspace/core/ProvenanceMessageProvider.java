/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.sql.SQLException;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;

/**
 * Interface for providing provenance messages.
 *
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
public interface ProvenanceMessageProvider {
    public String getMessage(Context context, String templateKey, Object... args)
            throws SQLException, AuthorizeException;
    public String getMessage(Context context, String templateKey, Item item, Object... args)
            throws SQLException, AuthorizeException;
    public String getMessage(String templateKey, Object... args);
    public String addCollectionsToMessage(Item item);
    public String getBitstreamMessage(Bitstream bitstream);
    public String getResourcePoliciesMessage(List<ResourcePolicy> resPolicies);
    public String getMetadata(String oldMtdKey, String oldMtdValue);
    public String getMetadataField(MetadataField metadataField);
}
