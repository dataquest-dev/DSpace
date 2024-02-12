/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.transfer.Upload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.core.Utils;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Override of the S3BitStoreService to store all the data also in the local assetstore.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class SyncS3BitStoreService extends S3BitStoreService {

    /**
     * log4j log
     */
    private static final Logger log = LogManager.getLogger(SyncS3BitStoreService.class);
    private boolean syncEnabled = false;

    @Autowired(required = true)
    DSBitStoreService dsBitStoreService;

    @Autowired(required = true)
    ConfigurationService configurationService;

    public SyncS3BitStoreService() {
        super();
    }

    public void init() throws IOException {
        super.init();
        syncEnabled = configurationService.getBooleanProperty("sync.storage.service.enabled", false);
    }

    @Override
    public void put(Bitstream bitstream, InputStream in) throws IOException {
        String key = getFullKey(bitstream.getInternalId());
        //Copy istream to temp file, and send the file, with some metadata
        File scratchFile = File.createTempFile(bitstream.getInternalId(), "s3bs");
        try (
                FileOutputStream fos = new FileOutputStream(scratchFile);
                // Read through a digest input stream that will work out the MD5
                DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance(CSA));
        ) {
            Utils.bufferedCopy(dis, fos);
            in.close();

            Upload upload = tm.upload(getBucketName(), key, scratchFile);

            upload.waitForUploadResult();

            bitstream.setSizeBytes(scratchFile.length());
            // we cannot use the S3 ETAG here as it could be not a MD5 in case of multipart upload (large files) or if
            // the bucket is encrypted
            bitstream.setChecksum(Utils.toHex(dis.getMessageDigest().digest()));
            bitstream.setChecksumAlgorithm(CSA);

            if (syncEnabled) {
                // Upload file into local assetstore - use buffered copy to avoid memory issues, because of large files
                File localFile = dsBitStoreService.getFile(bitstream);
                // Create a new file in the assetstore if it does not exist
                createFileIfNotExist(localFile);

                // Copy content from scratch file to local assetstore file
                FileInputStream fisScratchFile =  new FileInputStream(scratchFile);
                FileOutputStream fosLocalFile = new FileOutputStream(localFile);
                Utils.bufferedCopy(fisScratchFile, fosLocalFile);
                fisScratchFile.close();
            }
        } catch (AmazonClientException | IOException | InterruptedException e) {
            log.error("put(" + bitstream.getInternalId() + ", is)", e);
            throw new IOException(e);
        } catch (NoSuchAlgorithmException nsae) {
            // Should never happen
            log.warn("Caught NoSuchAlgorithmException", nsae);
        } finally {
            if (!scratchFile.delete()) {
                scratchFile.deleteOnExit();
            }
        }
    }

    @Override
    public void remove(Bitstream bitstream) throws IOException {
        String key = getFullKey(bitstream.getInternalId());
        try {
            // Remove file from S3
            s3Service.deleteObject(getBucketName(), key);
            if (syncEnabled) {
                // Remove file from local assetstore
                dsBitStoreService.remove(bitstream);
            }
        } catch (AmazonClientException e) {
            log.error("remove(" + key + ")", e);
            throw new IOException(e);
        }
    }

    /**
     * Create a new file in the assetstore if it does not exist
     * @param localFile
     * @throws IOException
     */
    private void createFileIfNotExist(File localFile) throws IOException {
        if (localFile.exists()) {
            return;
        }

        // Create the necessary parent directories if they do not yet exist
        if (!localFile.getParentFile().mkdirs()) {
            throw new IOException("Assetstore synchronization error: Directories in the assetstore for the file " +
                    "with path" + localFile.getParent() + " were not created");
        }
        if (!localFile.createNewFile()) {
            throw new IOException("Assetstore synchronization error: File " + localFile.getPath() +
                    " was not created");
        }
    }
}
