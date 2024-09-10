/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository.patch.operation;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class for PATCH REMOVE operations on Dspace Objects' metadata
 * Usage: (can be done on other dso than Item also):
 * - REMOVE metadata (with schema.identifier.qualifier) value of a dso (here: Item)
 * > Without index: removes all md values of that schema.identifier.qualifier type
 * > With index: removes only that select md value
 * <code>
 * curl -X PATCH http://${dspace.server.url}/api/core/items/<:id-item> -H "
 * Content-Type: application/json" -d '[{ "op": "remove",
 * "path": "/metadata/schema.identifier.qualifier(/indexOfSpecificMdToRemove)"}]'
 * </code>
 *
 * @author Maria Verdonck (Atmire) on 18/11/2019
 */
@Component
public class DSpaceObjectMetadataRemoveOperation<R extends DSpaceObject> extends PatchOperation<R> {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    DSpaceObjectMetadataPatchUtils metadataPatchUtils;

    @Autowired
    InstallItemService installItemService;

    @Autowired
    private ClarinItemService clarinItemService;

    @Autowired
    private ItemService itemService;

    @Override
    public R perform(Context context, R resource, Operation operation) throws SQLException {
        DSpaceObjectService dsoService = ContentServiceFactory.getInstance().getDSpaceObjectService(resource);
        String indexInPath = metadataPatchUtils.getIndexFromPath(operation.getPath());
        MetadataField metadataField = metadataPatchUtils.getMetadataField(context, operation);

        remove(context, resource, dsoService, metadataField, indexInPath);
        return resource;
    }

    /**
     * Removes a metadata from the dso at a given index (or all of that type if no index was given)
     *
     * @param context       context patch is being performed in
     * @param dso           dso being patched
     * @param dsoService    service doing the patch in db
     * @param metadataField md field being patched
     * @param index         index at where we want to delete metadata
     */
    private void remove(Context context, DSpaceObject dso, DSpaceObjectService dsoService, MetadataField metadataField,
                        String index) {
        metadataPatchUtils.checkMetadataFieldNotNull(metadataField);
        try {
            if (index == null) {
                String oldMtdKey = null;
                String oldMtdValue = null;
                if (dso.getType() == Constants.BITSTREAM) {
                    List<MetadataValue> mtd = dsoService.getMetadata(dso, metadataField.getMetadataSchema().getName(),
                            metadataField.getElement(), metadataField.getQualifier(), Item.ANY);
                    if (!CollectionUtils.isEmpty(mtd)) {
                        oldMtdKey = mtd.get(0).getMetadataField().getElement();
                        oldMtdValue = mtd.get(0).getValue();
                    }
                }
                // remove all metadata of this type
                dsoService.clearMetadata(context, dso, metadataField.getMetadataSchema().getName(),
                        metadataField.getElement(), metadataField.getQualifier(), Item.ANY);
                if (dso.getType() != Constants.BITSTREAM) {
                    return;
                }
                // Add suitable provenance
                Bitstream bitstream = (Bitstream) dso;
                List<Item> items = clarinItemService.findByBitstreamUUID(context, dso.getID());
                // The bitstream is assigned only into one Item.
                Item item = null;
                if (CollectionUtils.isEmpty(items)) {
                    log.warn("Bitstream (" + dso.getID() + ") is not assigned to any item.");
                    return;
                }
                item = items.get(0);
                String msg = " metadata (" + oldMtdKey + ": " + oldMtdValue + ") was deleted from bitstream (" +
                        bitstream.getName() + ": " + bitstream.getSizeBytes() + " bytes, checksum: " +
                        bitstream.getChecksum() + " (" + bitstream.getChecksumAlgorithm() + "))";
                addProvenanceMetadata(context, item, msg);
            } else {
                // remove metadata at index
                List<MetadataValue> metadataValues = dsoService.getMetadata(dso,
                        metadataField.getMetadataSchema().getName(), metadataField.getElement(),
                        metadataField.getQualifier(), Item.ANY);
                int indexInt = Integer.parseInt(index);
                if (indexInt >= 0 && metadataValues.size() > indexInt
                        && metadataValues.get(indexInt) != null) {
                    // Remember removed mtd
                    String oldMtdKey = null;
                    String oldMtdValue = null;
                    if (dso.getType() == Constants.ITEM) {
                        oldMtdKey = metadataValues.get(indexInt).getMetadataField().toString()
                                .replace('_', '.');
                        oldMtdValue = metadataValues.get(indexInt).getValue();
                    }
                    // remove that metadata
                    dsoService.removeMetadataValues(context, dso,
                            Arrays.asList(metadataValues.get(indexInt)));

                    if (dso.getType() != Constants.ITEM) {
                        return;
                    }

                    // Add suitable provenance
                    Item item = (Item) dso;
                    String msg = "metadata (" + oldMtdKey + ": " + oldMtdValue + ") was deleted";
                    addProvenanceMetadata(context, item, msg);
                } else {
                    throw new UnprocessableEntityException("UnprocessableEntityException - There is no metadata of " +
                            "this type at that index");
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("This index (" + index + ") is not valid number.", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new UnprocessableEntityException("There is no metadata of this type at that index");
        } catch (SQLException ex) {
            throw new DSpaceBadRequestException(
                    "SQLException in DspaceObjectMetadataRemoveOperation.remove " +
                            "trying to remove metadata from dso.", ex);
        } catch (AuthorizeException ex) {
            throw new DSpaceBadRequestException(
                    "AuthorizeException in DspaceObjectMetadataRemoveOperation.remove " +
                            "trying to replace metadata from dso.", ex);
        }
    }

    @Override
    public boolean supports(Object objectToMatch, Operation operation) {
        return (operation.getPath().startsWith(metadataPatchUtils.OPERATION_METADATA_PATH)
                && operation.getOp().trim().equalsIgnoreCase(OPERATION_REMOVE)
                && objectToMatch instanceof DSpaceObject);
    }
}
