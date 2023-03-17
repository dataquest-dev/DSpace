package org.dspace.content.service.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.service.DSpaceObjectLegacySupportService;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.core.Context;

import java.io.IOException;
import java.sql.SQLException;

public interface ClarinBitstreamService {
    public Bitstream create(Context context, Bundle bundle) throws SQLException, AuthorizeException;
}
