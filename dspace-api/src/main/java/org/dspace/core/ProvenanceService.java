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

import org.dspace.app.bulkaccesscontrol.model.BulkAccessControlInput;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;

public interface ProvenanceService {
    public void setBitstreamPolicies(Context context, Bitstream bitstream, Item item,
                                     BulkAccessControlInput accessControl) throws SQLException, AuthorizeException;

    public void setItemPolicies(Context context, Item item, BulkAccessControlInput accessControl)
            throws SQLException, AuthorizeException;

    public String removedReadPolicies(Context context, DSpaceObject dso, String type)
            throws SQLException, AuthorizeException;
    public void uploadBitstream(Context context, Bundle bundle) throws SQLException, AuthorizeException;
    public void editLicense(Context context, Item item, boolean newLicense) throws SQLException, AuthorizeException;

    public void moveItem(Context context, Item item, Collection collection) throws SQLException, AuthorizeException;
    public void mappedItem(Context context, Item item, Collection collection) throws SQLException, AuthorizeException;
    public void deletedItemFromMapped(Context context, Item item, Collection collection)
            throws SQLException, AuthorizeException;
    public void deleteBitstream(Context context,Bitstream bitstream) throws SQLException, AuthorizeException;
    public void addMetadata(Context context, DSpaceObject dso, MetadataField metadataField)
            throws SQLException, AuthorizeException;
    public void removeMetadata(Context context, DSpaceObject dso, MetadataField metadataField)
            throws SQLException, AuthorizeException;
    public void removeMetadataAtIndex(Context context, DSpaceObject dso, List<MetadataValue> metadataValues,
                                      int indexInt) throws SQLException, AuthorizeException;
    public void replaceMetadata(Context context, DSpaceObject dso, MetadataField metadataField, String oldMtdVal)
            throws SQLException, AuthorizeException;
    public void replaceMetadataSingle(Context context, DSpaceObject dso, MetadataField metadataField, String oldMtdVal)
            throws SQLException, AuthorizeException;
    public void makeDiscoverable(Context context, Item item, boolean discoverable)
            throws SQLException, AuthorizeException;
}
