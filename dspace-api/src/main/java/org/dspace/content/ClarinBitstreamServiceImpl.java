package org.dspace.content;

import com.github.jsonldjava.utils.Obj;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.dao.BitstreamDAO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.clarin.ClarinBitstreamService;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.handle.HandleClarinServiceImpl;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.DSBitStoreService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

//If this class wants to catch the Bitstream protected constructor, it must be in this package!
public class ClarinBitstreamServiceImpl implements ClarinBitstreamService{
    /**
     * log4j logger
     */
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinBitstreamServiceImpl.class);

    @Autowired
    private DSBitStoreService storeService;
    protected ClarinBitstreamServiceImpl() {
    }
    @Autowired(required = true)
    protected BitstreamDAO bitstreamDAO;

    @Autowired(required = true)
    protected AuthorizeService authorizeService;

    @Autowired(required = true)
    protected BundleService bundleService;

    @Autowired(required = true)
    protected BitstreamService bitstreamService;

    @Override
    public Bitstream create(Context context, Bundle bundle) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to create an empty bitstream");
        }
        //create empty bundle
        Bitstream bitstream = bitstreamDAO.create(context, new Bitstream());
        bundleService.addBitstream(context, bundle, bitstream);
        bitstreamService.update(context, bitstream);
        log.debug("Created new empty Bitstream with id: " + bitstream.getID());
        return bitstream;
    }

    @Override
    public boolean addExistingFile(Context context, Bitstream bitstream, Long expectedSizeBytes, String expectedCheckSum, String expectedChecksumAlgorithm) throws IOException, SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to add existing file to bitstream");
        }
        if (Objects.isNull(bitstream) || StringUtils.isBlank(bitstream.getInternalId())) {
            throw new IllegalStateException(
                    "Cannot add file to bitstream because it is entered incorrectly.");
        }
        storeService.put(bitstream, storeService.get(bitstream));
        if (!valid(bitstream, expectedSizeBytes, expectedCheckSum, expectedChecksumAlgorithm)) {
            bitstreamService.delete(context, bitstream);
            bitstreamService.expunge(context, bitstream);
            log.debug("Cannot add file with internal id: " +
                    bitstream.getInternalId() + " to bitstream with id: " + bitstream.getID() + " because the validation is incorrectly.");
            return false;
        }
        bitstreamService.update(context, bitstream);
        return true;
    }

    private boolean valid(Bitstream bitstream, long expectedSizeBytes,
                                     String expectedCheckSum, String expectedChecksumAlgorithm) {
        return bitstream.getSizeBytes() == expectedSizeBytes && bitstream.getChecksum().equals(expectedCheckSum) &&
                bitstream.getChecksumAlgorithm().equals(expectedChecksumAlgorithm);
    }
}
