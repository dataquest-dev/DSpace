/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.eperson.EPerson;
import org.springframework.beans.factory.annotation.Autowired;

public class ProvenanceMessageProviderImpl implements ProvenanceMessageProvider {
    private static final String PROVENANCE_MSG_JSON = "provenance_messages.json";
    private Map<String, String> messageTemplates;
    private InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();

    public ProvenanceMessageProviderImpl() {
        loadMessageTemplates();
    }

    private void loadMessageTemplates() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = getClass().getResourceAsStream(PROVENANCE_MSG_JSON)) {
            if (inputStream == null) {
                throw new RuntimeException("Failed to find message templates file");
            }
            messageTemplates = mapper.readValue(inputStream, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load message templates", e);
        }
    }

    @Override
    public String getMessage(Context context, String templateKey, Item item, Object... args)
            throws SQLException, AuthorizeException {
        String msg = getMessage(context, templateKey, args);
        msg = msg + "\n" + installItemService.getBitstreamProvenanceMessage(context, item);
        return msg;
    }

    @Override
    public String getMessage(String templateKey, Object... args) {
        String template = messageTemplates.get(templateKey);
        if (template == null) {
            throw new IllegalArgumentException("No message template found for key: " + templateKey);
        }
        return String.format(template, args);
    }

    @Override
    public String getMessage(Context context, String templateKey, Object... args) {
        EPerson currentUser = context.getCurrentUser();
        String timestamp = DCDate.getCurrent().toString();
        String details = getMessage(templateKey, args);
        return String.format("%s by %s (%s) on %s",
                details,
                currentUser.getFullName(),
                currentUser.getEmail(),
                timestamp);
    }

    @Override
    public String addCollectionsToMessage(Item item) {
        String msg = "Item was in collections:\n";
        List<Collection> collsList = item.getCollections();
        for (Collection coll : collsList) {
            msg = msg + coll.getName() + " (ID: " + coll.getID() + ")\n";
        }
        return msg;
    }

    @Override
    public String getBitstreamMessage(Bitstream bitstream) {
        // values of deleted bitstream
        String msg = bitstream.getName() + ": " +
                bitstream.getSizeBytes() + " bytes, checksum: " +
                bitstream.getChecksum() + " (" +
                bitstream.getChecksumAlgorithm() + ")\n";
        return msg;
    }

    @Override
    public String getResourcePoliciesMessage(List<ResourcePolicy> resPolicies) {
        return resPolicies.stream()
                .filter(rp -> rp.getAction() == Constants.READ)
                .map(rp -> String.format("[%s, %s, %d, %s, %s, %s, %s]",
                        rp.getRpName(), rp.getRpType(), rp.getAction(),
                        rp.getEPerson() != null ? rp.getEPerson().getEmail() : null,
                        rp.getGroup() != null ? rp.getGroup().getName() : null,
                        rp.getStartDate() != null ? rp.getStartDate().toString() : null,
                        rp.getEndDate() != null ? rp.getEndDate().toString() : null))
                .collect(Collectors.joining(";"));
    }

    @Override
    public String getMetadata(String oldMtdKey, String oldMtdValue) {
        return oldMtdKey + ": " + oldMtdValue;
    }

    @Override
    public String getMetadataField(MetadataField metadataField) {
        return metadataField.toString()
                .replace('_', '.');
    }
}
