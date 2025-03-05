/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts.filepreview;

import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.MissingLicenseAgreementException;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.PreviewContent;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.PreviewContentService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.FileInfo;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FilePreview extends DSpaceRunnable<FilePreviewConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(FilePreview.class);
    /**
     * `-i`: Info, show help information.
     */
    private boolean info = false;

    private String specificItemUUID = null;

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    private PreviewContentService previewContentService = ContentServiceFactory.getInstance().getPreviewContentService();

    @Override
    public FilePreviewConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager()
                .getServiceByName("file-preview", FilePreviewConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        // `-i`: Info, show help information.
        if (commandLine.hasOption('i')) {
            info = true;
            return;
        }

        // `-u`: UUID of the Item for which to create a preview of its bitstreams.
        if (commandLine.hasOption('u')) {
            specificItemUUID = commandLine.getOptionValue('u');
            // Generate the file previews for the specified item with the given UUID.
            handler.logInfo("\nGenerate the file previews for the specified item with the given UUID: " +
                    specificItemUUID);
        }
    }

    @Override
    public void internalRun() throws Exception {
        if (info) {
            printHelp();
            return;
        }

        Context context = new Context();
        if (StringUtils.isNotBlank(specificItemUUID)) {
            // Generate the preview only for a specific item
            generateItemFilePreviews(context, UUID.fromString(specificItemUUID));
        } else {
            // Generate the preview for all items
            context.turnOffAuthorisationSystem();
            Iterator<Item> items = itemService.findAll(context);

            int count = 0;
            while (items.hasNext()) {
                count++;
                Item item = items.next();
                try {
                    generateItemFilePreviews(context, item.getID());
                } catch (SQLException | AuthorizeException | IOException | ParserConfigurationException |
                        ArchiveException | SAXException e) {
                    handler.logError("Error while generating preview for item with UUID: " + item.getID());
                    handler.logError(e.getMessage());
                }

                if (count % 100 == 0) {
                    handler.logInfo("Processed " + count + " items.");
                }
            }
        }
        context.restoreAuthSystemState();
        context.commit();
        context.complete();
    }

    private void generateItemFilePreviews(Context context, UUID itemUUID) throws SQLException, AuthorizeException, IOException, ParserConfigurationException, ArchiveException, SAXException {
        Item item = itemService.find(context, itemUUID);
        if (Objects.isNull(item)) {
            handler.logError("Item with UUID: " + itemUUID + " not found.");
            return;
        }

        List<Bundle> bundles = item.getBundles();
        for (Bundle bundle : bundles) {
            List<Bitstream> bitstreams = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreams) {
                boolean canPreview = previewContentService.findOutCanPreview(context, bitstream);
                if (!canPreview) {
                    return;
                }
                List<FileInfo> fileInfos = new ArrayList<>();
                List<PreviewContent> prContents = previewContentService.findRootByBitstream(context,
                        bitstream.getID());
                // Generate new content if we didn't find any
                if (!prContents.isEmpty()) {
                    return;
                }

                fileInfos = previewContentService.getFilePreviewContent(context, bitstream, fileInfos);
                // Do not store HTML content in the database because it could be longer than the limit
                // of the database column
                if (StringUtils.equals("text/html", bitstream.getFormat(context).getMIMEType())) {
                    return;
                }

                for (FileInfo fi : fileInfos) {
                    previewContentService.createPreviewContent(context, bitstream, fi);
                }
            }
        }
    }

    @Override
    public void printHelp() {
        handler.logInfo("\n\nINFORMATION\nThis process generates a preview for every file in DSpace that should " +
                "have a preview.\n" +
                "You can choose from these available options:\n" +
                "  -i, --info            Show help information\n" +
                "  -u, --uuid            The UUID of the ITEM for which to create a preview of its bitstreams\n");
    }
}
