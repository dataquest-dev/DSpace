package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.ClarinLicenseNotFoundException;
import org.dspace.app.rest.model.patch.JsonValueEvaluator;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.repository.WorkspaceItemRestRepository;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.LicenseUtils;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.dspace.app.rest.repository.ClarinLicenseRestRepository.OPERATION_PATH_LICENSE_ATTACH;

public class ClarinLicenseUtils {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinLicenseUtils.class);

    private static final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private static final ClarinLicenseResourceMappingService clarinLicenseResourceMappingService =
            ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();
    private static final ClarinLicenseService clarinLicenseService = ClarinServiceFactory.getInstance()
            .getClarinLicenseService();


    /**
     * Detach the clarin license from the bitstreams and if the clarin license is not null or the operation path
     * contains `attach` value, attach the new clarin license to the bitstream.
     * @param context DSpace context object
     * @param source WorkspaceItem object
     * @param op should be ReplaceOperation, if it is not - do nothing
     */
    public static void maintainLicensesForItem(Context context, Object source, Operation op)
            throws SQLException, AuthorizeException {
        // Get item
        Item item = null;
        if (source instanceof WorkspaceItem) {
            item = ((WorkspaceItem) source).getItem();
        } else if (source instanceof Item) {
            item = (Item) source;
        }

        if (Objects.isNull(item)) {
            log.error("Cannot load the item from source, maybe the Object is not instance of WorkspaceItem or Item.");
            return;
        }
        // Get value from operation
        if (!(op instanceof ReplaceOperation)) {
            log.error("The patch operation is not ReplaceOperation, for maintaining the license for the item must " +
                    "be used ReplaceOperation.");
            return;
        }

        String clarinLicenseName;
        if (op.getValue() instanceof String) {
            clarinLicenseName = (String) op.getValue();
        } else {
            JsonValueEvaluator jsonValEvaluator = (JsonValueEvaluator) op.getValue();
            // replace operation has value wrapped in the ObjectNode
            JsonNode jsonNodeValue = jsonValEvaluator.getValueNode().get("value");
            if (ObjectUtils.isEmpty(jsonNodeValue)) {
                log.info("Cannot get clarin license name value from the ReplaceOperation.");
                return;
            }
            clarinLicenseName = jsonNodeValue.asText();
        }

        // Get clarin license by definition
        ClarinLicense clarinLicense = clarinLicenseService.findByName(context, clarinLicenseName);
        if (StringUtils.isNotBlank(clarinLicenseName) && Objects.isNull(clarinLicense)) {
            throw new ClarinLicenseNotFoundException("Cannot patch item with id because the " +
                    "clarin license with name: " + clarinLicenseName + " isn't supported in the CLARIN/DSpace");
        }

        // Clear the license metadata from the item
        clarinLicenseService.clearLicenseMetadataFromItem(context, item);

        // Detach the clarin licenses from the uploaded bitstreams
        List<Bundle> bundles = item.getBundles(Constants.CONTENT_BUNDLE_NAME);
        for (Bundle bundle : bundles) {
            List<Bitstream> bitstreamList = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreamList) {
                // in case bitstream ID exists in license table for some reason .. just remove it
                clarinLicenseResourceMappingService.detachLicenses(context, bitstream);
            }
        }

        // Save changes to database
        itemService.update(context, item);

        // Do not continue if the ClarinLicense is null or the operation do not contain `attach` message in the path
        if (Objects.isNull(clarinLicense)) {
            log.info("The clarin license is null so all item metadata for license was cleared and the" +
                    "licenses was detached.");
            return;
        } else {
            // Get path from the operation
            String[] path = op.getPath().substring(1).split("/");
            // Path should be in the format: `/license/detach or license/attach`
            if (ArrayUtils.getLength(path) == 0 || ArrayUtils.getLength(path) != 2) {
                log.error("Wrong operation path for maintaining license for item, path: " + Arrays.toString(path));
                return;
            }

            // Continue only if path is `/license/attach`
            if (!StringUtils.equals(path[1], OPERATION_PATH_LICENSE_ATTACH)) {
                return;
            }
        }

        // If the clarin license is not null that means some clarin license was updated and accepted
        // Attach the new clarin license to every bitstream and add clarin license values to the item metadata.

        // update item metadata with license data
        clarinLicenseService.addLicenseMetadataToItem(context, clarinLicense, item);

        // Attach the clarin license to the bitstreams
        for (Bundle bundle : bundles) {
            List<Bitstream> bitstreamList = bundle.getBitstreams();
            for (Bitstream bitstream : bitstreamList) {
                // in case bitstream ID exists in license table for some reason .. just remove it
                clarinLicenseResourceMappingService.attachLicense(context, clarinLicense, bitstream);
            }
        }

        // Save changes to database
        itemService.update(context, item);

        // For Default License between user and repo
        EPerson submitter = context.getCurrentUser();

        try {
            // remove any existing DSpace license (just in case the user
            // accepted it previously)
            itemService.removeDSpaceLicense(context, item);
            String license = LicenseUtils.getLicenseText(context.getCurrentLocale(),
                    item.getOwningCollection(), item, submitter);

            LicenseUtils.grantLicense(context, item, license, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
