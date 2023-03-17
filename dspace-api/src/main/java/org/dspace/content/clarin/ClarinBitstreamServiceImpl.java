package org.dspace.content.clarin;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.dao.BitstreamDAO;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.clarin.ClarinBitstreamService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;

public class ClarinBitstreamServiceImpl implements ClarinBitstreamService{
    protected ClarinBitstreamServiceImpl() {
    }
    @Autowired(required = true)
    protected BitstreamDAO bitstreamDAO;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;

    @Autowired(required = true)
    protected BundleService bundleService;

    @Override
    public Bitstream create(Context context, Bundle bundle) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an CLARIN License");
        }
        //Bitstream bitstream = bitstreamDAO.create(context, new Bitstream());
        //bundleService.addBitstream(context, bundle, bitstream);
        //return bitstream;
        return null;
    }
}
