/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository.patch.operation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.collections4.CollectionUtils;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.exception.RESTBitstreamNotFoundException;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * A PATCH operation for removing bitstreams in bulk from the repository.
 *
 * Example: <code>
 * curl -X PATCH http://${dspace.server.url}/api/core/bitstreams -H "Content-Type: application/json"
 * -d '[
 *       {"op": "remove", "path": "/bitstreams/${bitstream1UUID}"},
 *       {"op": "remove", "path": "/bitstreams/${bitstream2UUID}"},
 *       {"op": "remove", "path": "/bitstreams/${bitstream3UUID}"}
 *     ]'
 * </code>
 *
 * @author Jens Vannerum (jens.vannerum@atmire.com)
 */
@Component
public class BitstreamRemoveOperation extends PatchOperation<Bitstream> {
    @Autowired
    BitstreamService bitstreamService;

    @Autowired
    ItemService itemService;

    @Autowired
    ClarinItemService clarinItemService;

    @Autowired
    InstallItemService installItemService;

    @Autowired
    AuthorizeService authorizeService;
    public static final String OPERATION_PATH_BITSTREAM_REMOVE = "/bitstreams/";

    @Override
    public Bitstream perform(Context context, Bitstream resource, Operation operation) throws SQLException {
        String bitstreamIDtoDelete = operation.getPath().replace(OPERATION_PATH_BITSTREAM_REMOVE, "");
        Bitstream bitstreamToDelete = bitstreamService.find(context, UUID.fromString(bitstreamIDtoDelete));
        if (bitstreamToDelete == null) {
            throw new RESTBitstreamNotFoundException(bitstreamIDtoDelete);
        }
        authorizeBitstreamRemoveAction(context, bitstreamToDelete, Constants.DELETE);

        try {
            List<Item> items = clarinItemService.findByBitstreamUUID(context, UUID.fromString(bitstreamIDtoDelete));
            // The bitstream is assigned only into one Item.
            Item item = null;
            if (!CollectionUtils.isEmpty(items)) {
                item = items.get(0);
            }

            // values of deleted bitstream
            StringBuilder bitstreamMsg = new StringBuilder();
            bitstreamMsg.append(bitstreamToDelete.getName()).append(": ")
                    .append(bitstreamToDelete.getSizeBytes()).append(" bytes, checksum: ")
                    .append(bitstreamToDelete.getChecksum()).append(" (")
                    .append(bitstreamToDelete.getChecksumAlgorithm()).append(")\n");

            //delete bitstream
            bitstreamService.delete(context, bitstreamToDelete);

            if (item == null) {
                return null;
            }

            // Add suitable provenance
            String timestamp = DCDate.getCurrent().toString();
            EPerson e = context.getCurrentUser();

            // Build some provenance data while we're at it.
            StringBuilder prov = new StringBuilder();
            prov.append("Item was deleted a bitstream (").append(bitstreamMsg).append(") by ")
                    .append(e.getFullName()).append(" (").append(e.getEmail()).append(") on ")
                    .append(timestamp).append("\n");
            prov.append(installItemService.getBitstreamProvenanceMessage(context, item));

            itemService.addMetadata(context, item, MetadataSchemaEnum.DC.getName(),
                    "description", "provenance", "en", prov.toString());
             //Update item in DB
            itemService.update(context, item);
        } catch (AuthorizeException e) {
            throw new DSpaceBadRequestException(
                    "AuthorizeException in BitstreamRemoveOperation.perform while adding provenance metadata " +
                            "about the deleted item's bitstream.", e);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public boolean supports(Object objectToMatch, Operation operation) {
        return objectToMatch == null && operation.getOp().trim().equalsIgnoreCase(OPERATION_REMOVE) &&
            operation.getPath().trim().startsWith(OPERATION_PATH_BITSTREAM_REMOVE);
    }

    public void authorizeBitstreamRemoveAction(Context context, Bitstream bitstream, int operation)
        throws SQLException {
        try {
            authorizeService.authorizeAction(context, bitstream, operation);
        } catch (AuthorizeException e) {
            throw new AccessDeniedException("The current user is not allowed to remove the bitstream", e);
        }
    }
}
