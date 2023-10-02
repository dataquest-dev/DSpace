/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;


import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import  org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.converter.BitstreamConverter;
import org.dspace.app.rest.converter.MetadataBitstreamWrapperConverter;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.MetadataBitstreamWrapper;
import org.dspace.app.rest.model.MetadataBitstreamWrapperRest;
import org.dspace.app.rest.model.MetadataValueWrapper;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizationBitstreamUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.MissingLicenseAgreementException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.*;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Event;
import org.dspace.handle.service.HandleService;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.dspace.util.FileInfo;
import org.dspace.util.FileTreeViewGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component(MetadataBitstreamWrapperRest.CATEGORY + "." + MetadataBitstreamWrapperRest.NAME)
public class MetadataBitstreamRestRepository extends DSpaceRestRepository<MetadataBitstreamWrapperRest, Integer>{
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataBitstreamRestRepository.class);
    private final static int MAX_FILE_PREVIEW_COUNT = 1000;

    @Autowired
    HandleService handleService;

    @Autowired
    BitstreamConverter bitstreamConverter;

    @Autowired
    MetadataBitstreamWrapperConverter metadataBitstreamWrapperConverter;
    @Autowired
    ItemService itemService;
    @Autowired
    ClarinLicenseResourceMappingService licenseService;

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    BitstreamStorageService bitstreamStorageService;

    @Autowired
    AuthorizationBitstreamUtils authorizationBitstreamUtils;

    @SearchRestMethod(name = "byHandle")
    public Page<MetadataBitstreamWrapperRest> findByHandle(@Parameter(value = "handle", required = true) String handle,
                                                           @Parameter(value = "fileGrpType", required = false) String fileGrpType,
                                                           Pageable pageable)
            throws SQLException, ParserConfigurationException, IOException, SAXException, AuthorizeException, ArchiveException {
        if (StringUtils.isBlank(handle)) {
            throw new DSpaceBadRequestException("handle cannot be null!");
        }
        List<MetadataBitstreamWrapper> metadataValueWrappers = new ArrayList<>();
        Context context = obtainContext();
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot obtain the context from the request.");
        }
        HttpServletRequest request = getRequestService().getCurrentRequest().getHttpServletRequest();
        String contextPath = request.getContextPath();
        List<MetadataBitstreamWrapperRest> rs = new ArrayList<>();
        DSpaceObject dso = null;

        try{
            dso = handleService.resolveToObject(context, handle);
        } catch (Exception e) {
            throw new RuntimeException("Cannot resolve handle: " + handle);
        }

        if (!(dso instanceof Item)) {
            throw new UnprocessableEntityException("Cannot fetch bitstreams from different object than Item.");
        }

        Item item = (Item) dso;
        List<String> fileGrpTypes = Arrays.asList(fileGrpType.split(","));
        List<Bundle> bundles = findEnabledBundles(fileGrpTypes, item);
        for (Bundle bundle : bundles) {
            List<Bitstream> bitstreams = bundle.getBitstreams();
            String use = bundle.getName();
            if (StringUtils.equals("THUMBNAIL", use)) {
                Thumbnail thumbnail = itemService.getThumbnail(context, item, false);
                if (Objects.nonNull(thumbnail)) {
                    bitstreams = new ArrayList<>();
                    bitstreams.add(thumbnail.getThumb());
                }
            }

            for (Bitstream bitstream : bitstreams) {
                String url = composePreviewURL(context, item, bitstream, contextPath);
                List<FileInfo> fileInfos = new ArrayList<>();
                boolean canPreview = findOutCanPreview(context, bitstream);
                if (canPreview) {
                    fileInfos = getFilePreviewContent(context, bitstream, fileInfos);
                }
                MetadataBitstreamWrapper bts = new MetadataBitstreamWrapper(bitstream, fileInfos, bitstream.getFormat(context).getMIMEType(), bitstream.getFormatDescription(context), url, canPreview);
                metadataValueWrappers.add(bts);
                rs.add(metadataBitstreamWrapperConverter.convert(bts, utils.obtainProjection()));
            }
        }

        return new PageImpl<>(rs, pageable, rs.size());
    }

    protected List<Bundle> findEnabledBundles(List<String> fileGrpTypes, Item item) throws SQLException
    {
        // Check if the user is requested a specific bundle or
        // the all bundles.
        List<Bundle> bundles;
        if (fileGrpTypes.size() == 0) {
            bundles = item.getBundles();
        } else {
            bundles = new ArrayList<Bundle>();
            for (String fileGrpType : fileGrpTypes) {
                for (Bundle newBundle : item.getBundles(fileGrpType)) {
                    bundles.add(newBundle);
                }
            }
        }

        return bundles;
    }

    private List<FileInfo> getFilePreviewContent(Context context, Bitstream bitstream, List<FileInfo> fileInfos)
            throws SQLException, AuthorizeException, IOException, ParserConfigurationException,
            ArchiveException, SAXException {
        InputStream inputStream = null;
        try {
            inputStream = bitstreamService.retrieve(context, bitstream);
        } catch (MissingLicenseAgreementException e) {
            // Allow  the content of the file
//                            inputStream = bitstreamStorageService.retrieve(context, bitstream);
        }

        if (Objects.nonNull(inputStream)) {
            fileInfos = processInputStreamToFilePreview(context, bitstream, fileInfos, inputStream);
        }
        return fileInfos;
    }

    private List<FileInfo> processInputStreamToFilePreview(Context context, Bitstream bitstream,
                                                           List<FileInfo> fileInfos, InputStream inputStream)
            throws IOException, SQLException, ParserConfigurationException, SAXException, ArchiveException {
        if (bitstream.getFormat(context).getMIMEType().equals("text/plain")) {
            String data = getFileContent(inputStream);
            fileInfos.add(new FileInfo(data, false));
        } else {
            String data = "";
            if (bitstream.getFormat(context).getExtensions().contains("zip")) {
                data = extractFile(inputStream, "zip");
                fileInfos = FileTreeViewGenerator.parse(data);
            } else if (bitstream.getFormat(context).getExtensions().contains("tar")) {
                ArchiveInputStream is = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, inputStream);
                data = extractFile(is, "tar");
                fileInfos = FileTreeViewGenerator.parse(data);
            }
        }
        return fileInfos;
    }

    private String composePreviewURL(Context context, Item item, Bitstream bitstream, String contextPath) {
        String identifier = null;
        if (Objects.nonNull(item) && Objects.nonNull(item.getHandle())) {
            identifier = "handle/" + item.getHandle();
        }
        else if (Objects.nonNull(item)) {
            identifier = "item/" + item.getID();
        }
        else {
            identifier = "id/" + bitstream.getID();
        }
        String url = contextPath + "/bitstream/"+identifier;
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

    private boolean match(String schema, String element, String qualifier, MetadataField field)
    {
        if (!element.equals(Item.ANY) && !element.equals(field.getElement()))
        {
            return false;
        }

        if (qualifier == null)
        {
            if (field.getQualifier() != null)
            {
                return false;
            }
        }
        else if (!qualifier.equals(Item.ANY))
        {
            if (!qualifier.equals(field.getQualifier()))
            {
                return false;
            }
        }

        if (!schema.equals(Item.ANY))
        {
            if (field.getMetadataSchema() != null && !field.getMetadataSchema().getName().equals(schema))
            {
                return false;
            }
        }
        return true;
    }


    public String extractFile(InputStream inputStream, String fileType) {
        List<String> filePaths = new ArrayList<>();
        Path tempFile = null;
        FileSystem zipFileSystem = null;

        try {
            switch (fileType) {
                case "tar":
                    tempFile = Files.createTempFile("temp", ".tar");
                    break;
                default:
                    tempFile = Files.createTempFile("temp", ".zip");

            }

            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            zipFileSystem = FileSystems.newFileSystem(tempFile, (ClassLoader) null);
            Path root = zipFileSystem.getPath("/");
            Files.walk(root)
                    .forEach(path -> {
                        try {
                            long fileSize = Files.size(path);
                            if (Files.isDirectory(path)) {
                                filePaths.add(path.toString().substring(1) + "/|" + fileSize );
                            } else {
                                filePaths.add(path.toString().substring(1) + "|" + fileSize );
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipFileSystem != null) {
                try {
                    zipFileSystem.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (tempFile != null) {
                try {
                    Files.delete(tempFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(("<root>"));
        List<String> allFiles = filePaths;
        int fileCounter = 0;
        for (String filePath : allFiles) {
            if (!filePath.isEmpty() && filePath.length() > 3) {
                if (filePath.contains(".")) {
                    fileCounter++;
                }
                sb.append("<element>");
                sb.append(filePath);
                sb.append("</element>");

                if (fileCounter > MAX_FILE_PREVIEW_COUNT) {
                    sb.append("<element>");
                    sb.append("/|0");
                    sb.append("</element>");
                    sb.append("<element>");
                    sb.append("...too many files...|0");
                    sb.append("</element>");
                    break;
                }
            }
        }
        sb.append(("</root>"));
        return sb.toString();
    }

    private static long calculateUncompressedSize(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        long uncompressedSize = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            uncompressedSize += bytesRead;
        }
        return uncompressedSize;
    }

    public String getFileContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        reader.close();
        return content.toString();
    }

//    private void setFileCounter(int newFileCounter){
//        this.fileCounter = newFileCounter;
//    }
//
//    private int getFileCounter() {
//        return this.fileCounter;
//    }
//
//    private void resetFileCounter() {
//        setFileCounter(0);
//    }
//
//    private void increaseFileCounter(int valueToIncrease) {
//        fileCounter += valueToIncrease;
//    }

    private boolean findOutCanPreview(Context context, Bitstream bitstream) throws SQLException, AuthorizeException {
        try {
            return authorizationBitstreamUtils.authorizeBitstream(context, bitstream);
        } catch (MissingLicenseAgreementException e) {
            return false;
        }

        // Do not preview Items with HamleDT license
//        if (StringUtils.equals(HAMLEDT_LICENSE_NAME, clarinLicense.getName())) {
//            return false;
//        }
//        for (ClarinLicenseLabel clarinLicenseLabel : clarinLicense.getLicenseLabels()) {
//            if (StringUtils.equals(PUB_LABEL_NAME, clarinLicenseLabel.getLabel())) {
//                return true;
//            }
//        }
//        return false;
    }

    @Override
    public MetadataBitstreamWrapperRest findOne(Context context, Integer integer) {
        return null;
    }

    @Override
    public Page<MetadataBitstreamWrapperRest> findAll(Context context, Pageable pageable) {
        return null;
    }

    @Override
    public Class<MetadataBitstreamWrapperRest> getDomainClass() {
        return MetadataBitstreamWrapperRest.class;
    }
}
