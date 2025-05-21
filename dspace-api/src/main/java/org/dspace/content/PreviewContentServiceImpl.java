/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.MissingLicenseAgreementException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.dao.PreviewContentDAO;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.PreviewContentService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.util.FileInfo;
import org.dspace.util.FileTreeViewGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Service implementation for the PreviewContent object.
 *
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
public class PreviewContentServiceImpl implements PreviewContentService {

    /**
     * logger
     */
    private static final Logger log = LoggerFactory.getLogger(PreviewContentServiceImpl.class);

    private final String ARCHIVE_TYPE_ZIP = "zip";
    private final String ARCHIVE_TYPE_TAR = "tar";

    // This constant is used to limit the length of the preview content stored in the database to prevent
    // the database from being overloaded with large amounts of data.
    private static final int MAX_PREVIEW_COUNT_LENGTH = 2000;

    // Configured ZIP file preview limit (default: 1000) - if the ZIP file contains more files, it will be truncated
    @Value("${file.preview.zip.limit.length:1000}")
    private int maxPreviewCount;


    @Autowired
    PreviewContentDAO previewContentDAO;
    @Autowired(required = true)
    AuthorizeService authorizeService;
    @Autowired
    ConfigurationService configurationService;
    @Autowired
    BitstreamService bitstreamService;

    private static class EOCDRecord {
        long  totalEntries;
        long  centralDirectoryOffset;

        EOCDRecord(long  totalEntries, long  centralDirectoryOffset) {
            this.totalEntries = totalEntries;
            this.centralDirectoryOffset = centralDirectoryOffset;
        }
    }

    private static class TarHeader {
        final String fileName;
        final long fileSize;

        TarHeader(String fileName, long fileSize) {
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }

    @Override
    public PreviewContent create(Context context, Bitstream bitstream, String name, String content,
                                 boolean isDirectory, String size, Map<String, PreviewContent> subPreviewContents)
            throws SQLException {
        // no authorization required!
        // Create a table row
        PreviewContent previewContent = previewContentDAO.create(context, new PreviewContent(bitstream, name, content,
                isDirectory, size, subPreviewContents));
        log.info("Created new preview content of ID = {}", previewContent.getID());
        return previewContent;
    }

    @Override
    public PreviewContent create(Context context, PreviewContent previewContent) throws SQLException {
        //no authorization required!
        PreviewContent newPreviewContent = previewContentDAO.create(context, new PreviewContent(previewContent));
        log.info("Created new preview content of ID = {}", newPreviewContent.getID());
        return newPreviewContent;
    }

    @Override
    public void delete(Context context, PreviewContent previewContent) throws SQLException, AuthorizeException {
        if (!authorizeService.isAdmin(context)) {
            throw new AuthorizeException(
                    "You must be an admin to delete an CLARIN Content Preview");
        }
        previewContentDAO.delete(context, previewContent);
    }

    @Override
    public PreviewContent find(Context context, int valueId) throws SQLException {
        return previewContentDAO.findByID(context, PreviewContent.class, valueId);
    }

    @Override
    public List<PreviewContent> findByBitstream(Context context, UUID bitstreamId) throws SQLException {
        return previewContentDAO.findByBitstream(context, bitstreamId);
    }

    @Override
    public List<PreviewContent> hasPreview(Context context, Bitstream bitstream) throws SQLException {
        return previewContentDAO.hasPreview(context, bitstream);
    }

    @Override
    public List<PreviewContent> findAll(Context context) throws SQLException {
        return previewContentDAO.findAll(context, PreviewContent.class);
    }

    @Override
    public boolean canPreview(Context context, Bitstream bitstream) throws SQLException, AuthorizeException {
        try {
            // Check it is allowed by configuration
            boolean isAllowedByCfg = configurationService.getBooleanProperty("file.preview.enabled", true);
            if (!isAllowedByCfg) {
                return false;
            }

            // Check it is allowed by license
            authorizeService.authorizeAction(context, bitstream, Constants.READ);
            return true;
        } catch (MissingLicenseAgreementException e) {
            return false;
        }
    }

    @Override
    public List<FileInfo> getFilePreviewContent(Context context, Bitstream bitstream) throws Exception {
        List<FileInfo> fileInfos = null;
        File file = null;

        try {
            file = bitstreamService.retrieveFile(context, bitstream); // Retrieve the file

            if (Objects.nonNull(file)) {
                fileInfos = processFileToFilePreview(context, bitstream, file);
            }
        } catch (MissingLicenseAgreementException e) {
            log.error("Missing license agreement: ", e);
            throw e;
        } catch (IOException e) {
            log.error("IOException during file processing: ", e);
            throw e;
        } finally {
            // Ensure the file is deleted
            if (file != null && file.exists()) {
                boolean deleted = file.delete(); // Delete the file to avoid leaks
                if (!deleted) {
                    log.warn("Failed to delete temporary file: " + file.getAbsolutePath());
                }
            }
        }
        return fileInfos;
    }

    @Override
    public PreviewContent createPreviewContent(Context context, Bitstream bitstream, FileInfo fi) throws SQLException {
        Hashtable<String, PreviewContent> sub = createSubMap(fi.sub, value -> {
            try {
                return createPreviewContent(context, bitstream, value);
            } catch (SQLException e) {
                String msg = "Database error occurred while creating new preview content " +
                        "for bitstream with ID = " + bitstream.getID() + " Error msg: " + e.getMessage();
                log.error(msg, e);
                throw new RuntimeException(msg, e);
            }
        });
        return create(context, bitstream, fi.name, fi.content, fi.isDirectory, fi.size, sub);
    }

    @Override
    public FileInfo createFileInfo(PreviewContent pc) {
        Hashtable<String, FileInfo> sub = createSubMap(pc.sub, this::createFileInfo);
        return new FileInfo(pc.name, pc.content, pc.size, pc.isDirectory, sub);
    }

    @Override
    public List<FileInfo> processFileToFilePreview(Context context, Bitstream bitstream,
                                                          File file)
            throws Exception {
        List<FileInfo> fileInfos = new ArrayList<>();
        String bitstreamMimeType = bitstream.getFormat(context).getMIMEType();
        if (bitstreamMimeType.equals("text/plain")) {
            if (!validateBitstreamNameWithType(bitstream, "zip,tar,gz,tar.gz,tar.bz2")) {
                throw new IOException("The file has an incorrect type according to the MIME type stored in the " +
                        "database. This could cause the ZIP file to be previewed as a text file, potentially leading" +
                        " to a database error.");
            }
            String data = getFileContent(file, true);
            fileInfos.add(new FileInfo(data, false));
        } else if (bitstreamMimeType.equals("text/html")) {
            String data = getFileContent(file, false);
            fileInfos.add(new FileInfo(data, false));
        } else {
            String data = "";
            Map<String, String> archiveTypes = Map.of(
                    "application/zip", ARCHIVE_TYPE_ZIP,
                    "application/x-tar", ARCHIVE_TYPE_TAR
            );

            String mimeType = bitstream.getFormat(context).getMIMEType();
            if (archiveTypes.containsKey(mimeType)) {
                data = extractFile(file, archiveTypes.get(mimeType));
                fileInfos = FileTreeViewGenerator.parse(data);
            }
        }
        return fileInfos;
    }

    public String composePreviewURL(Context context, Item item, Bitstream bitstream, String contextPath) {
        String identifier = null;
        if (Objects.nonNull(item) && Objects.nonNull(item.getHandle())) {
            identifier = "handle/" + item.getHandle();
        } else if (Objects.nonNull(item)) {
            identifier = "item/" + item.getID();
        } else {
            identifier = "id/" + bitstream.getID();
        }
        String url = contextPath + "/api/core/bitstreams/" + identifier;
        try {
            if (bitstream.getName() != null) {
                url += "/" + Util.encodeBitstreamName(bitstream.getName(), "UTF-8");
            }
        } catch (UnsupportedEncodingException uee) {
            log.error("UnsupportedEncodingException", uee);
        }
        url += "?sequence=" + bitstream.getSequenceID();

        String isAllowed = "n";
        try {
            if (authorizeService.authorizeActionBoolean(context, bitstream, Constants.READ)) {
                isAllowed = "y";
            }
        } catch (SQLException e) {
            log.error("Cannot authorize bitstream action because: " + e.getMessage());
        }

        url += "&isAllowed=" + isAllowed;
        return url;
    }

    /**
     * Validate the bitstream name with the specified type. Check if the ZIP file is not previewed as a text file.
     * @param bitstream
     * @param forbiddenTypes "in the form of 'type1,type2,type3'"
     * @return
     */
    private boolean validateBitstreamNameWithType(Bitstream bitstream, String forbiddenTypes) {
        ArrayList<String> forbiddenTypesList = new ArrayList(Arrays.asList(forbiddenTypes.split(",")));
        for (String forbiddenType : forbiddenTypesList) {
            if (bitstream.getName().endsWith(forbiddenType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Define the hierarchy organization for preview content and file info.
     * The hierarchy is established by the sub map.
     * If a content item contains a sub map, it is considered a directory; if not, it is a file.
     * @param sourceMap  sub map that is used as a pattern
     * @param creator    creator function
     * @return           created sub map
     */
    private <T, U> Hashtable<String, T> createSubMap(Map<String, U> sourceMap, Function<U, T> creator) {
        if (sourceMap == null) {
            return null;
        }

        Hashtable<String, T> sub = new Hashtable<>();
        for (Map.Entry<String, U> entry : sourceMap.entrySet()) {
            sub.put(entry.getKey(), creator.apply(entry.getValue()));
        }
        return sub;
    }

    /**
     * Adds a file path and its size to the list of file paths.
     * If the path represents a directory, appends a "/" to the path.
     * @param filePaths the list of file paths to add to
     * @param path the file or directory path
     * @param size the size of the file or directory
     */
    private void addFilePath(List<String> filePaths, String path, long size) {
        String fileInfo = "";
        try {
            Path filePath = Paths.get(path);
            boolean isDir = Files.isDirectory(filePath);
            fileInfo = (isDir ? path + "/|" : path + "|") + size;
        } catch (NullPointerException | InvalidPathException | SecurityException e) {
            log.error(String.format("Failed to add file path. Path: '%s', Size: %d", path, size), e);
        }
        filePaths.add(fileInfo);
    }

    /**
     * Processes a TAR file, extracting its entries and adding their paths to the provided list.
     * @param filePaths the list to populate with the extracted file paths
     * @param file the TAR file data
     * @throws IOException if an I/O error occurs while reading the TAR file
     */
    private void processTarFile(List<String> filePaths, File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileSize = raf.length();
            byte[] buffer = new byte[512];  // TAR header size is always 512 bytes
            long currentPos = 0;
            while (currentPos < fileSize) {
                // Read the next 512-byte header
                raf.seek(currentPos);
                raf.readFully(buffer);
                // Parse the header to extract file metadata
                TarHeader header = parseTarHeader(buffer);
                if (header == null || header.fileName.isEmpty()) {
                    break;  // End of archive (empty header)
                }
                // Handle the file metadata
                long fileContentSize = header.fileSize;
                String fileName = header.fileName;
                // Move to the file content position
                currentPos += 512; // Move past the header
                // Add the file to the list (only metadata needed)
                addFilePath(filePaths, fileName, fileContentSize);
                // Skip payload and align to next header
                currentPos += fileContentSize;                  // skip payload
                currentPos = ((currentPos + 511) / 512) * 512;   // align to 512-byte boundary
            }
        }
    }

    /**
     * Parse the 512-byte TAR header.
     *
     * @param headerBytes the header block (512 bytes)
     * @return a TarHeader object containing file metadata
     */
    private TarHeader parseTarHeader(byte[] headerBytes) {
        // Extract the file name (first 100 bytes)
        String fileName = new String(headerBytes, 0, 100, StandardCharsets.US_ASCII).trim();

        // If the file name is empty, we've reached the end of the archive
        if (fileName.isEmpty()) {
            return null;
        }

        // Extract the file size (octal value in bytes 124-135)
        String sizeStr = new String(headerBytes, 124, 12, StandardCharsets.US_ASCII).trim();
        long fileSize = Long.parseLong(sizeStr, 8); // TAR file sizes are stored in octal

        return new TarHeader(fileName, fileSize);
    }

    /**
     * Parses a ZIP file and extracts the names and sizes of its entries.
     * Handles standard ZIP and ZIP64 formats for large files or archives with many entries.
     *
     * @param filePaths the list to populate with entry names
     * @param file      the ZIP file to read
     * @throws IOException if the file is invalid or cannot be read
     */
    public void processZipFile(List<String> filePaths, File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            EOCDRecord eocd = findEOCD(raf);
            if (eocd == null) {
                throw new IOException("End of Central Directory not found. Not a valid ZIP file: " + file.getName());
            }

            // Seek to the Central Directory offset
            raf.seek(eocd.centralDirectoryOffset);

            // Loop through all entries in the Central Directory
            for (long i = 0; i < eocd.totalEntries; i++) {
                long currentEntryStart = raf.getFilePointer(); // Track entry position

                int signature = readIntLE(raf);
                if (signature != 0x02014b50) { // Central directory file header
                    throw new IOException("Invalid central directory signature at entry " + i +
                            " (offset: " + currentEntryStart + ")");
                }

                raf.skipBytes(2); // Version made by
                raf.skipBytes(2); // Version needed to extract
                int generalPurposeBitFlag = readShortLE(raf);

                raf.skipBytes(2); // Compression method
                raf.skipBytes(2); // File modification time
                raf.skipBytes(2); // File modification date
                raf.skipBytes(4); // CRC-32

                // Read compressed/uncompressed sizes (can be -1 if ZIP64 is used)
                int compressedSize32 = readIntLE(raf);
                int uncompressedSize32 = readIntLE(raf);

                int fileNameLength = readShortLE(raf);
                int extraFieldLength = readShortLE(raf);
                int fileCommentLength = readShortLE(raf);

                raf.skipBytes(2); // Disk number start
                raf.skipBytes(2); // Internal file attributes
                raf.skipBytes(4); // External file attributes

                int relativeOffset32 = readIntLE(raf); // Relative offset of local header

                // Read file name
                byte[] nameBytes = new byte[fileNameLength];
                raf.readFully(nameBytes);

                // Determine character set (bit 11 = UTF-8)
                Charset charset = (generalPurposeBitFlag & (1 << 11)) != 0
                        ? StandardCharsets.UTF_8
                        : Charset.forName("IBM437");
                String name = new String(nameBytes, charset);

                // Default values for final sizes and offset (use 64-bit to avoid overflow)
                long finalCompressedSize = compressedSize32 & 0xFFFFFFFFL;
                long finalUncompressedSize = uncompressedSize32 & 0xFFFFFFFFL;
                long finalRelativeOffset = relativeOffset32 & 0xFFFFFFFFL;

                // Read extra fields (e.g. ZIP64 extended information)
                long afterFileNamePos = raf.getFilePointer();
                byte[] extraFieldBytes = new byte[extraFieldLength];
                if (extraFieldLength > 0) {
                    raf.readFully(extraFieldBytes);
                }

                // ZIP64 is used when sizes or offsets are too large for 32-bit integers
                if (compressedSize32 == -1 || uncompressedSize32 == -1 || relativeOffset32 == -1) {
                    int pointer = 0;
                    while (pointer + 4 <= extraFieldLength) {
                        int headerId = (extraFieldBytes[pointer] & 0xFF) | ((extraFieldBytes[pointer + 1] & 0xFF) << 8);
                        int dataSize = (extraFieldBytes[pointer + 2] & 0xFF) |
                                ((extraFieldBytes[pointer + 3] & 0xFF) << 8);

                        if (pointer + 4 + dataSize > extraFieldLength) {
                            System.err.println("Warning: Malformed extra field with ID 0x"
                                    + Integer.toHexString(headerId));
                            break;
                        }

                        if (headerId == 0x0001) { // ZIP64 Extended Information
                            int offset = pointer + 4;
                            int bytesRead = 0;

                            if (uncompressedSize32 == -1 && bytesRead + 8 <= dataSize) {
                                finalUncompressedSize = parseLongLE(extraFieldBytes, offset + bytesRead);
                                bytesRead += 8;
                            }
                            if (compressedSize32 == -1 && bytesRead + 8 <= dataSize) {
                                finalCompressedSize = parseLongLE(extraFieldBytes, offset + bytesRead);
                                bytesRead += 8;
                            }
                            if (relativeOffset32 == -1 && bytesRead + 8 <= dataSize) {
                                finalRelativeOffset = parseLongLE(extraFieldBytes, offset + bytesRead);
                                bytesRead += 8;
                            }
                            break;
                        }

                        pointer += (4 + dataSize);
                    }
                }

                // Skip comment field
                raf.seek(afterFileNamePos + extraFieldLength);
                raf.skipBytes(fileCommentLength);

                // Pass extracted file entry to callback
                addFilePath(filePaths, name, finalUncompressedSize);
            }
        }
    }

    /**
     * Reads a 4-byte little-endian integer from a stream.
     *
     * @param raf the RandomAccessFile to read from
     * @return the 32-bit integer value read (interpreted in little-endian order)
     * @throws IOException if the stream ends unexpectedly before reading 4 bytes
     */
    private int readIntLE(RandomAccessFile raf) throws IOException {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);  // Zabezpečí načítanie všetkých 4 bajtov alebo hodí EOFException

        return ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    /**
     * Reads a 2-byte little-endian short from a stream.
     *
     * @param raf the RandomAccessFile to read from
     * @return the 16-bit integer value read (interpreted in little-endian order)
     * @throws IOException if the stream ends unexpectedly before reading 2 bytes
     */
    private short readShortLE(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[2];
        if (raf.read(buffer) != 2) {
            throw new IOException("Unexpected EOF while reading 2-byte little-endian short");
        }

        return ByteBuffer.wrap(buffer)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort();
    }

    /**
     * Reads an 8-byte little-endian long from a stream.
     *
     * @param raf the RandomAccessFile to read from
     * @return the 64-bit long value read (interpreted in little-endian order)
     * @throws IOException if the stream ends unexpectedly before reading 8 bytes
     */
    private long readLongLE(RandomAccessFile raf) throws IOException {
        byte[] buffer = new byte[8];
        if (raf.read(buffer) != 8) {
            throw new IOException("Unexpected EOF while reading 8-byte little-endian long");
        }

        return ByteBuffer.wrap(buffer)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();
    }

    /**
     * Reads an 8-byte little-endian long from a byte array.
     *
     * @param bytes  the byte array containing the long value
     * @param offset the starting index in the array
     * @return the 64-bit long value parsed (interpreted in little-endian order)
     * @throws IndexOutOfBoundsException if there are not enough bytes from offset
     */
    private long parseLongLE(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();
    }

    /**
     * Finds the End Of Central Directory (EOCD) record by scanning backward in the file.
     * Supports standard and ZIP64 formats.
     *
     * @param raf the RandomAccessFile positioned at the start of the ZIP file
     * @return an EOCDRecord containing the total number of entries and central directory offset, or null if not found
     * @throws IOException if an I/O error occurs or if the EOCD or ZIP64 EOCD structure is invalid
     */
    private EOCDRecord findEOCD(RandomAccessFile raf) throws IOException {
        long fileLength = raf.length();
        // Scan up to 64KB + 20 bytes (ZIP64 EOCD Locator) + 56 bytes (ZIP64 EOCD) for safety
        long scanRange = Math.min(fileLength, 65536L + 20L + 56L);
        byte[] buffer = new byte[(int) scanRange]; // Cast to int is safe because scanRange is capped

        raf.seek(fileLength - scanRange);
        raf.readFully(buffer);

        // First, search for the standard EOCD signature (0x06054b50) backwards
        for (int i = buffer.length - 4; i >= 0; i--) {
            if ((buffer[i] & 0xFF) == 0x50 &&
                    (buffer[i + 1] & 0xFF) == 0x4b &&
                    (buffer[i + 2] & 0xFF) == 0x05 &&
                    (buffer[i + 3] & 0xFF) == 0x06) {

                if (i + 22 > buffer.length) {
                    continue; // Avoid out-of-bounds read
                }

                int totalEntriesOnDisk16 = (buffer[i + 8] & 0xFF) | ((buffer[i + 9] & 0xFF) << 8);
                int totalEntries16 = (buffer[i + 10] & 0xFF) | ((buffer[i + 11] & 0xFF) << 8);
                int cdOffset32 = (buffer[i + 16] & 0xFF) |
                        ((buffer[i + 17] & 0xFF) << 8) |
                        ((buffer[i + 18] & 0xFF) << 16) |
                        ((buffer[i + 19] & 0xFF) << 24);

                boolean isZip64 = totalEntries16 == 0xFFFF || cdOffset32 == -1 || totalEntriesOnDisk16 == 0xFFFF;

                if (isZip64) {
                    int zip64LocatorStart = i - 20;
                    if (zip64LocatorStart >= 0 &&
                            (buffer[zip64LocatorStart] & 0xFF) == 0x50 &&
                            (buffer[zip64LocatorStart + 1] & 0xFF) == 0x4b &&
                            (buffer[zip64LocatorStart + 2] & 0xFF) == 0x06 &&
                            (buffer[zip64LocatorStart + 3] & 0xFF) == 0x07) {

                        long zip64EOCDOffset = parseLongLE(buffer, zip64LocatorStart + 8);
                        raf.seek(zip64EOCDOffset);

                        if (readIntLE(raf) != 0x06064b50) {
                            throw new IOException("Invalid ZIP64 EOCD signature.");
                        }

                        raf.skipBytes(8 + 2 + 2 + 4 + 4 + 8); // Skip ahead
                        long totalEntries64 = readLongLE(raf);
                        raf.skipBytes(8);
                        long cdOffset64 = readLongLE(raf);

                        return new EOCDRecord(totalEntries64, cdOffset64);
                    }
                } else {
                    return new EOCDRecord(totalEntries16, cdOffset32);
                }
            }
        }
        return null; // Standard EOCD signature not found in the scanned range
    }

    /**
     * Builds an XML response string based on the provided list of file paths.
     * @param filePaths the list of file paths to include in the XML response
     * @return an XML string representation of the file paths
     */
    private String buildXmlResponse(List<String> filePaths) {
        // Is a folder regex
        String folderRegex = "/|\\d+";
        Pattern pattern = Pattern.compile(folderRegex);

        StringBuilder sb = new StringBuilder();
        sb.append("<root>");
        Iterator<String> iterator = filePaths.iterator();
        int fileCounter = 0;
        while (iterator.hasNext() && fileCounter < maxPreviewCount) {
            String filePath = iterator.next();
            // Check if the file is a folder
            Matcher matcher = pattern.matcher(filePath);
            if (!matcher.matches()) {
                // It is a file
                fileCounter++;
            }
            sb.append("<element>").append(filePath).append("</element>");
        }

        if (fileCounter > maxPreviewCount) {
            sb.append("<element>...too many files...|0</element>");
        }
        sb.append("</root>");
        return sb.toString();
    }

    /**
     * Processes  file data based on the specified file type (tar or zip),
     * and returns an XML representation of the file paths.
     * @param file the InputStream containing the file data
     * @param fileType    the type of file to extract ("tar" or "zip")
     * @return an XML string representing the extracted file paths
     */
    private String extractFile(File file, String fileType) throws Exception {
        List<String> filePaths = new ArrayList<>();
        // Process the file based on its type
        if (ARCHIVE_TYPE_TAR.equals(fileType)) {
            processTarFile(filePaths, file);
        } else {
            processZipFile(filePaths, file);
        }
        return buildXmlResponse(filePaths);
    }

    /**
     * Read input stream and return content as String
     * @param file to read
     * @return content of the inputStream as a String
     * @throws IOException
     */
    private String getFileContent(File file, boolean cutResult) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (cutResult && content.length() > MAX_PREVIEW_COUNT_LENGTH) {
                    content.append(" . . .");
                    break;
                }
                content.append(line).append("\n");
            }

        } catch (IOException e) {
            log.error("IOException during creating the preview content because: ", e);
            throw e; // Optional: rethrow if you want the exception to propagate
        }

        return cutResult ? ensureMaxLength(content.toString()) : content.toString();
    }

    /**
     * Trims the input string to ensure it does not exceed the maximum length for the database column.
     * @param input The original string to be trimmed.
     * @return A string that is truncated to the maximum length if necessary.
     */
    private static String ensureMaxLength(String input) {
        if (input == null) {
            return null;
        }

        // Check if the input string exceeds the maximum preview length
        if (input.length() > MAX_PREVIEW_COUNT_LENGTH) {
            // Truncate the string and append " . . ."
            int previewLength = MAX_PREVIEW_COUNT_LENGTH - 6; // Subtract length of " . . ."
            return input.substring(0, previewLength) + " . . .";
        } else {
            // Return the input string as is if it's within the preview length
            return input;
        }
    }
}

