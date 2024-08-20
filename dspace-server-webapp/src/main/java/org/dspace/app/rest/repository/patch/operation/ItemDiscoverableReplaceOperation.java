/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository.patch.operation;

import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.content.Collection;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

/**
 * This is the implementation for Item 'discoverable' patches.
 *
 * Example: <code>
 * curl -X PATCH http://${dspace.server.url}/api/core/items/<:id-item> -H "
 * Content-Type: application/json" -d '[{ "op": "replace", "path": "
 * /discoverable", "value": true|false]'
 * </code>
 */
@Component
public class ItemDiscoverableReplaceOperation<R> extends PatchOperation<R> {

    /**
     * Path in json body of patch that uses this operation
     */
    private static final String OPERATION_PATH_DISCOVERABLE = "/discoverable";

    @Override
    public R perform(Context context, R object, Operation operation) {
        checkOperationValue(operation.getValue());
        Boolean discoverable = getBooleanOperationValue(operation.getValue());
        if (supports(object, operation)) {
            Item item = (Item) object;
            if (discoverable && item.getTemplateItemOf() != null) {
                throw new UnprocessableEntityException("A template item cannot be discoverable.");
            }

            String timestamp = DCDate.getCurrent().toString();

            // Add suitable provenance - includes user, date, collections +
            // bitstream checksums
            EPerson e = context.getCurrentUser();
            InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();
            ItemService itemService = ContentServiceFactory.getInstance().getItemService();
            // Build some provenance data while we're at it.
            StringBuilder prov = new StringBuilder();

            prov.append("Item made ").append( discoverable ? "" : "non-").append("discoverable by ").append(e.getFullName()).append(" (")
                    .append(e.getEmail()).append(") on ").append(timestamp).append("\n")
                    .append("Item was in collections:\n");

            List<Collection> colls = item.getCollections();

            for (Collection coll : colls) {
                prov.append(coll.getName()).append(" (ID: ").append(coll.getID()).append(")\n");
            }
            try {
                prov.append(installItemService.getBitstreamProvenanceMessage(context, item));

                itemService.addMetadata(context, item, MetadataSchemaEnum.DC.getName(), "description", "provenance", "en", prov.toString());
                context.commit();
            } catch (SQLException ex) {
                throw new RuntimeException("SQLException occured when item making " + (discoverable ? "" : "non-") + "discoverable.", ex);
            }
            item.setDiscoverable(discoverable);
            return object;
        } else {
            throw new DSpaceBadRequestException("ItemDiscoverableReplaceOperation does not support this operation");
        }
    }

    @Override
    public boolean supports(Object objectToMatch, Operation operation) {
        return (objectToMatch instanceof Item && operation.getOp().trim().equalsIgnoreCase(OPERATION_REPLACE)
                && operation.getPath().trim().equalsIgnoreCase(OPERATION_PATH_DISCOVERABLE));
    }

}
