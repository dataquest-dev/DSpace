/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository.patch.operation;

import java.sql.SQLException;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 Class for PATCH ADD operations on Dspace Objects' metadata
 * Usage: (can be done on other dso than Item also):
 * - ADD metadata (with schema.identifier.qualifier) value of a dso (here: Item) to end of list of md
 * <code>
 * curl -X PATCH http://${dspace.server.url}/api/core/items/<:id-item> -H "
 * Content-Type: application/json" -d '[{ "op": "add", "path": "
 * /metadata/schema.identifier.qualifier(/0|-)}", "value": "metadataValue"]'
 * </code>
 * @author Maria Verdonck (Atmire) on 18/11/2019
 */
@Component
public class DSpaceObjectMetadataAddOperation<R extends DSpaceObject> extends PatchOperation<R> {

    @Autowired
    DSpaceObjectMetadataPatchUtils metadataPatchUtils;

    @Autowired
    InstallItemService installItemService;

    @Autowired
    ItemService itemService;

    @Autowired
    ClarinItemService clarinItemService;

    @Override
    public R perform(Context context, R resource, Operation operation) throws SQLException {
        DSpaceObjectService dsoService = ContentServiceFactory.getInstance().getDSpaceObjectService(resource);
        MetadataValueRest metadataValueToAdd = metadataPatchUtils.extractMetadataValueFromOperation(operation);
        MetadataField metadataField = metadataPatchUtils.getMetadataField(context, operation);
        String indexInPath = metadataPatchUtils.getIndexFromPath(operation.getPath());

        add(context, resource, dsoService, metadataField, metadataValueToAdd, indexInPath);
        return resource;
    }

    /**
     * Adds metadata to the dso (appending if index is 0 or left out, prepending if -)
     *
     * @param context       context patch is being performed in
     * @param dso           dso being patched
     * @param dsoService    service doing the patch in db
     * @param metadataField md field being patched
     * @param metadataValue value of md element
     * @param index         determines whether we're prepending (-) or appending (0) md value
     */
    private void add(Context context, DSpaceObject dso, DSpaceObjectService dsoService, MetadataField metadataField,
                     MetadataValueRest metadataValue, String index) {
        metadataPatchUtils.checkMetadataFieldNotNull(metadataField);
        int indexInt = 0;
        if (index != null && index.equals("-")) {
            indexInt = -1;
        }
        try {
            dsoService.addAndShiftRightMetadata(context, dso, metadataField.getMetadataSchema().getName(),
                    metadataField.getElement(), metadataField.getQualifier(), metadataValue.getLanguage(),
                    metadataValue.getValue(), metadataValue.getAuthority(), metadataValue.getConfidence(), indexInt);

            if (dso.getType() != Constants.ITEM && dso.getType() != Constants.BITSTREAM) {
                return;
            }

            if (dso.getType() == Constants.ITEM) {
                // Add suitable provenance
                Item item = (Item) dso;
                String msg = "metadata (" + metadataField.toString()
                        .replace('_', '.') + ")";
                addProvenanceMetadata(context, item, msg);
            }

            if (dso.getType() == Constants.BITSTREAM) {
                // Add suitable provenance
                Bitstream bitstream = (Bitstream) dso;
                List<Item> items = clarinItemService.findByBitstreamUUID(context, dso.getID());
                // The bitstream is assigned only into one Item.
                Item item = null;
                if (CollectionUtils.isEmpty(items)) {
                    return;
                }
                item = items.get(0);
                String msg = "bitstream (" + bitstream.getName() + ": " +
                        bitstream.getSizeBytes() + " bytes, checksum: " +
                        bitstream.getChecksum() + " (" +
                        bitstream.getChecksumAlgorithm() + ")" + ") metadata (" +
                        metadataField.toString().replace('_', '.') + ")";
                addProvenanceMetadata(context, item, msg);
            }
        } catch (SQLException e) {
            throw new DSpaceBadRequestException("SQLException in DspaceObjectMetadataAddOperation.add trying to add " +
                    "metadata to dso.", e);
        } catch (AuthorizeException e) {
            throw new DSpaceBadRequestException(
                    "AuthorizeException in DspaceObjectMetadataAddOperation.add " +
                            "trying to add metadata to dso.", e);
        }
    }

    private void addProvenanceMetadata(Context context, Item item, String msg) throws SQLException, AuthorizeException {
        String timestamp = DCDate.getCurrent().toString();
        EPerson e = context.getCurrentUser();
        StringBuilder prov = new StringBuilder();

        prov.append("Item ").append(msg).append(" was added by ")
                .append(e.getFullName()).append(" (").append(e.getEmail()).append(") on ")
                .append(timestamp).append("\n");
        prov.append(installItemService.getBitstreamProvenanceMessage(context, item));
        itemService.addMetadata(context, item, MetadataSchemaEnum.DC.getName(),
                "description", "provenance", "en", prov.toString());
        //Update item in DB
        itemService.update(context, item);
    }

    @Override
    public boolean supports(Object objectToMatch, Operation operation) {
        return (operation.getPath().startsWith(metadataPatchUtils.OPERATION_METADATA_PATH)
                && operation.getOp().trim().equalsIgnoreCase(OPERATION_ADD)
                && objectToMatch instanceof DSpaceObject);
    }
}
