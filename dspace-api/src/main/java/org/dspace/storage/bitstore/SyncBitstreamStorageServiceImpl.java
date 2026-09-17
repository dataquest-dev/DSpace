/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bitstream storage for the CLARIN synchronized assetstore: with
 * {@code sync.storage.service.enabled} on, every new bitstream is written to the incoming store and
 * mirrored into the local assetstore by {@link SyncS3BitStoreService}. Such a bitstream belongs to
 * two stores at once, so its row records {@link #SYNCHRONIZED_STORES_NUMBER} instead of a real store
 * key and reads are routed back to the incoming store.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class SyncBitstreamStorageServiceImpl extends BitstreamStorageServiceImpl {

    /**
     * Recorded on a bitstream that lives in the incoming store and in the mirror at the same time.
     * It is deliberately outside the range of assetstore keys configured in bitstore.xml.
     */
    public static final int SYNCHRONIZED_STORES_NUMBER = 77;

    /**
     * Returned by {@link #getSynchronizedStoreNumber(Bitstream)} when no mirror store is configured.
     */
    public static final int NO_SYNCHRONIZED_STORE = -1;

    private boolean syncEnabled = false;

    @Autowired(required = true)
    private ConfigurationService configurationService;

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.syncEnabled = configurationService.getBooleanProperty("sync.storage.service.enabled", false);
    }

    @Override
    protected int recordedStoreNumber(int writtenStoreNumber) {
        return syncEnabled ? SYNCHRONIZED_STORES_NUMBER : writtenStoreNumber;
    }

    @Override
    protected int whichStoreNumber(Bitstream bitstream) {
        return isBitstreamStoreSynchronized(bitstream) ? getIncoming() : bitstream.getStoreNumber();
    }

    /**
     * Is this bitstream held in both the incoming store and the mirror?
     *
     * @param bitstream bitstream to check
     * @return true when its row carries the synchronized sentinel
     */
    public boolean isBitstreamStoreSynchronized(Bitstream bitstream) {
        return bitstream.getStoreNumber() == SYNCHRONIZED_STORES_NUMBER;
    }

    /**
     * Store holding the mirrored copy of a synchronized bitstream, that is the local assetstore
     * {@link SyncS3BitStoreService} writes to alongside the incoming store. Picking the store by its
     * type rather than by "the one that is left over" keeps the answer unambiguous now that
     * bitstore.xml configures more than two stores.
     *
     * @param bitstream bitstream to locate the mirror for
     * @return the mirror's store number, or {@link #NO_SYNCHRONIZED_STORE} when there is none
     */
    public int getSynchronizedStoreNumber(Bitstream bitstream) {
        if (!isBitstreamStoreSynchronized(bitstream)) {
            return bitstream.getStoreNumber();
        }

        for (Map.Entry<Integer, BitStoreService> storeEntry : getStores().entrySet()) {
            if (storeEntry.getKey() == getIncoming()) {
                continue;
            }
            if (storeEntry.getValue() instanceof DSBitStoreService) {
                return storeEntry.getKey();
            }
        }
        return NO_SYNCHRONIZED_STORE;
    }

    /**
     * Checksum of a bitstream as held by one named store, rather than by the store its row points at.
     *
     * @param context     DSpace context
     * @param bitstream   bitstream to checksum
     * @param storeNumber store to read the bits from
     * @return map with the checksum and the checksum algorithm, empty when the store has no such object
     * @throws IOException if the store cannot be read
     */
    public Map<String, Object> computeChecksumSpecStore(Context context, Bitstream bitstream, int storeNumber)
            throws IOException {
        return this.getStore(storeNumber).about(bitstream, List.of("checksum", "checksum_algorithm"));
    }
}
