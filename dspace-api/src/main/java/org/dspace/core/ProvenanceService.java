package org.dspace.core;

import org.dspace.app.bulkaccesscontrol.model.BulkAccessControlInput;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;

import java.sql.SQLException;

public interface ProvenanceService {
    public void setBitstreaPolicies(Context context, Bitstream bitstream, Item item, BulkAccessControlInput accessControl) throws SQLException, AuthorizeException;

    public void setItemPolicies(Context context, Item item, BulkAccessControlInput accessControl) throws SQLException, AuthorizeException;

    public String removedReadPolicies(Context context, DSpaceObject dso, String type) throws SQLException, AuthorizeException;
}
