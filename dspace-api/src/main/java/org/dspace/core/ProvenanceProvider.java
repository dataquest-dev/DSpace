/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.bulkaccesscontrol.model.AccessCondition;
import org.dspace.app.bulkaccesscontrol.model.BulkAccessControlInput;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.MetadataValue;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;

/**
 * ProvenanceProviderrest is responsible for creating provenance metadata for items based on the actions performed.
 *
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
public class ProvenanceProvider {
    private static final Logger log = LogManager.getLogger(ProvenanceProvider.class);

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();

    private ClarinItemService clarinItemService = ClarinServiceFactory.getInstance().getClarinItemService();
    private ClarinLicenseResourceMappingService clarinResourceMappingService =
            ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private final ProvenanceMessageProvider messageProvider = new ProvenanceMessageProvider();

    private void addProvenanceMetadata(Context context, Item item, String msg)
            throws SQLException, AuthorizeException {
        itemService.addMetadata(context, item, MetadataSchemaEnum.DC.getName(),
                "description", "provenance", "en", msg);
        itemService.update(context, item);
    }

    private String extractAccessConditions(List<AccessCondition> accessConditions) {
        return accessConditions.stream()
                .map(AccessCondition::getName)
                .collect(Collectors.joining(";"));
    }

    private Item getItem(Context context, Bitstream bitstream) throws SQLException {
        List<Item> items = clarinItemService.findByBitstreamUUID(context, bitstream.getID());
        if (items.isEmpty()) {
            log.warn("Bitstream (" + bitstream.getID() + ") is not assigned to any item.");
            return null;
        }
        return items.get(0);
    }

    private String findLicenseInBundles(Item item, String bundleName, String currentLicense, Context context)
            throws SQLException {
        List<Bundle> bundles = item.getBundles(bundleName);
        for (Bundle clarinBundle : bundles) {
            List<Bitstream> bitstreamList = clarinBundle.getBitstreams();
            for (Bitstream bundleBitstream : bitstreamList) {
                if (Objects.isNull(currentLicense)) {
                    List<ClarinLicenseResourceMapping> mappings =
                            this.clarinResourceMappingService.findByBitstreamUUID(context, bundleBitstream.getID());
                    if (CollectionUtils.isNotEmpty(mappings)) {
                        return mappings.get(0).getLicense().getName();
                    }
                }
            }
        }
        return currentLicense;
    }

    public void setItemPolicies(Context context, Item item, BulkAccessControlInput accessControl)
            throws SQLException, AuthorizeException {
        String resPoliciesStr = extractAccessConditions(accessControl.getItem().getAccessConditions());
        if (StringUtils.isNotBlank(resPoliciesStr)) {
            String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.ACCESS_CONDITION.getTemplate(),
                    resPoliciesStr, "item", item.getID());
            addProvenanceMetadata(context, item, msg);
        }
    }

    public void removeReadPolicies(Context context, DSpaceObject dso, List<ResourcePolicy> resPolicies)
            throws SQLException, AuthorizeException {
        if (resPolicies.isEmpty()) {
            return;
        }
        String resPoliciesStr = messageProvider.getResourcePoliciesMessage(resPolicies);
        if (dso.getType() == Constants.ITEM) {
            Item item = (Item) dso;
            String msg = messageProvider.getMessage(context,
                    ProvenanceMessageTemplates.RESOURCE_POLICIES_REMOVED.getTemplate(),
                    resPoliciesStr.isEmpty() ? "empty" : resPoliciesStr, "item", item.getID());
            addProvenanceMetadata(context, item, msg);
        } else if (dso.getType() == Constants.BITSTREAM) {
            Bitstream bitstream = (Bitstream) dso;
            Item item = getItem(context, bitstream);
            if (Objects.nonNull(item)) {
                String msg = messageProvider.getMessage(context,
                         ProvenanceMessageTemplates.RESOURCE_POLICIES_REMOVED.getTemplate(),
                        resPoliciesStr.isEmpty() ? "empty" : resPoliciesStr, "bitstream", bitstream.getID());
                addProvenanceMetadata(context, item, msg);
            }
        }
    }

    public void setBitstreamPolicies(Context context, Bitstream bitstream, Item item,
                                     BulkAccessControlInput accessControl) throws SQLException, AuthorizeException {
        String accConditionsStr = extractAccessConditions(accessControl.getBitstream().getAccessConditions());
        if (StringUtils.isNotBlank(accConditionsStr)) {
            String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.ACCESS_CONDITION.getTemplate(),
                    accConditionsStr, "bitstream", bitstream.getID());
            addProvenanceMetadata(context, item, msg);
        }
    }

    public void editLicense(Context context, Item item, boolean newLicense) throws SQLException, AuthorizeException {
        String oldLicense = null;
        oldLicense = findLicenseInBundles(item, Constants.LICENSE_BUNDLE_NAME, oldLicense, context);
        if (oldLicense == null) {
            oldLicense = findLicenseInBundles(item, Constants.CONTENT_BUNDLE_NAME, oldLicense, context);
        }

        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.EDIT_LICENSE.getTemplate(), item,
                Objects.isNull(oldLicense) ? "empty" : oldLicense,
                !newLicense ? "removed" : Objects.isNull(oldLicense) ? "added" : "updated");
        addProvenanceMetadata(context, item, msg);
    }

    public void moveItem(Context context, Item item, Collection collection) throws SQLException, AuthorizeException {
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.MOVE_ITEM.getTemplate(),
                item, collection.getID());
        // Update item in DB
        // Because a user can move an item without authorization turn off authorization
        context.turnOffAuthorisationSystem();
        addProvenanceMetadata(context, item, msg);
        context.restoreAuthSystemState();
    }

    public void mappedItem(Context context, Item item, Collection collection) throws SQLException, AuthorizeException {
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.MAPPED_ITEM.getTemplate(),
                collection.getID());
        addProvenanceMetadata(context, item, msg);
    }

    public void deletedItemFromMapped(Context context, Item item, Collection collection)
            throws SQLException, AuthorizeException {
        String msg = messageProvider.getMessage(context,
                ProvenanceMessageTemplates.DELETED_ITEM_FROM_MAPPED.getTemplate(), collection.getID());
        addProvenanceMetadata(context, item, msg);
    }

    public void deleteBitstream(Context context,Bitstream bitstream) throws SQLException, AuthorizeException {
        Item item = getItem(context, bitstream);
        if (Objects.nonNull(item)) {
            String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.EDIT_BITSTREAM.getTemplate(),
                    item, item.getID(), messageProvider.getBitstreamMessage(bitstream));
            addProvenanceMetadata(context, item, msg);
        }
    }

    public void addMetadata(Context context, DSpaceObject dso, MetadataField metadataField)
            throws SQLException, AuthorizeException {
        if (Constants.ITEM == dso.getType()) {
            String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.ITEM_METADATA.getTemplate(),
                    messageProvider.getMetadataField(metadataField), "added");
            addProvenanceMetadata(context, (Item) dso, msg);
        }

        if (dso.getType() == Constants.BITSTREAM) {
            Bitstream bitstream = (Bitstream) dso;
            Item item = getItem(context, bitstream);
            if (Objects.nonNull(item)) {
                String msg = messageProvider.getMessage(context,
                        ProvenanceMessageTemplates.BITSTREAM_METADATA.getTemplate(), item,
                        messageProvider.getMetadataField(metadataField), "added by",
                        messageProvider.getBitstreamMessage(bitstream));
                addProvenanceMetadata(context, item, msg);
            }
        }
    }

    public void removeMetadata(Context context, DSpaceObject dso, MetadataField metadataField)
            throws SQLException, AuthorizeException {
        if (dso.getType() != Constants.BITSTREAM) {
            return;
        }
        MetadataField oldMtdKey = null;
        String oldMtdValue = null;
        List<MetadataValue> mtd = bitstreamService.getMetadata((Bitstream) dso,
                metadataField.getMetadataSchema().getName(),
                metadataField.getElement(), metadataField.getQualifier(), Item.ANY);
        if (CollectionUtils.isNotEmpty(mtd)) {
            oldMtdKey = mtd.get(0).getMetadataField();
            oldMtdValue = mtd.get(0).getValue();
        }
        Bitstream bitstream = (Bitstream) dso;
        Item item = getItem(context, bitstream);
        if (Objects.nonNull(item)) {
            String msg = messageProvider.getMessage(context,
                    ProvenanceMessageTemplates.BITSTREAM_METADATA.getTemplate(), item,
                    messageProvider.getMetadata(messageProvider.getMetadataField(oldMtdKey), oldMtdValue),
                    "deleted from", messageProvider.getBitstreamMessage(bitstream));
            addProvenanceMetadata(context, item, msg);
        }
    }

    public void removeMetadataAtIndex(Context context, DSpaceObject dso, List<MetadataValue> metadataValues,
                                      int indexInt) throws SQLException, AuthorizeException {
        if (dso.getType() != Constants.ITEM) {
            return;
        }
        // Remember removed mtd
        String oldMtdKey = messageProvider.getMetadataField(metadataValues.get(indexInt).getMetadataField());
        String oldMtdValue = metadataValues.get(indexInt).getValue();
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.ITEM_METADATA.getTemplate(),
                (Item) dso, messageProvider.getMetadata(oldMtdKey, oldMtdValue), "deleted");
        addProvenanceMetadata(context, (Item) dso, msg);
    }

    public void replaceMetadata(Context context, DSpaceObject dso, MetadataField metadataField, String oldMtdVal)
            throws SQLException, AuthorizeException {
        if (dso.getType() != Constants.ITEM) {
            return;
        }
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.ITEM_METADATA.getTemplate(),
                (Item) dso,messageProvider.getMetadata(messageProvider.getMetadataField(metadataField),
                        oldMtdVal), "updated");
        addProvenanceMetadata(context, (Item) dso, msg);
    }

    public void replaceMetadataSingle(Context context, DSpaceObject dso, MetadataField metadataField, String oldMtdVal)
            throws SQLException, AuthorizeException {
        if (dso.getType() != Constants.BITSTREAM) {
            return;
        }

        Bitstream bitstream = (Bitstream) dso;
        Item item = getItem(context, bitstream);
        if (Objects.nonNull(item)) {
            String msg = messageProvider.getMessage(context,
                    ProvenanceMessageTemplates.ITEM_REPLACE_SINGLE_METADATA.getTemplate(), item,
                    messageProvider.getBitstreamMessage(bitstream),
                    messageProvider.getMetadata(messageProvider.getMetadataField(metadataField), oldMtdVal));
            addProvenanceMetadata(context, item, msg);;
        }
    }

    public void makeDiscoverable(Context context, Item item, boolean discoverable)
            throws SQLException, AuthorizeException {
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.DISCOVERABLE.getTemplate(),
                item, discoverable ? "" : "non-") + messageProvider.addCollectionsToMessage(item);
        addProvenanceMetadata(context, item, msg);
    }

    public void uploadBitstream(Context context, Bundle bundle) throws SQLException, AuthorizeException {
        Item item = bundle.getItems().get(0);
        String msg = messageProvider.getMessage(context, ProvenanceMessageTemplates.BUNDLE_ADDED.getTemplate(),
                item, bundle.getID());
        addProvenanceMetadata(context,item, msg);
        itemService.update(context, item);
    }
}
