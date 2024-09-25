package org.dspace.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.bulkaccesscontrol.model.AccessCondition;
import org.dspace.app.bulkaccesscontrol.model.BulkAccessControlInput;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.eperson.EPerson;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProvenanceServiceImpl implements ProvenanceService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private ResourcePolicyService resourcePolicyService;

    protected ProvenanceServiceImpl() {
    }

    private String extractAccessConditions(List<AccessCondition> accessConditions) {
        return accessConditions.stream()
                .map(AccessCondition::getName)
                .collect(Collectors.joining(";"));
    }

    @Override
    public String removedReadPolicies(Context context, DSpaceObject dso, String type) throws SQLException, AuthorizeException {
        List<ResourcePolicy> resPolicies = resourcePolicyService.find(context, dso, type);
        if (resPolicies.isEmpty()) {
            return null;
        }
        String resPoliciesStr = extractResourcePolicies(resPolicies);
        if (dso.getType() == Constants.ITEM) {
            addProvenanceForItem(context, (Item) dso, resPoliciesStr);
        } else if (dso.getType() == Constants.BITSTREAM) {
            addProvenanceForBitstream(context, (Bitstream) dso, resPoliciesStr);
        }
        return resPoliciesStr;
    }

    private String extractResourcePolicies(List<ResourcePolicy> resPolicies) {
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
    public void setItemPolicies(Context context, Item item, BulkAccessControlInput accessControl) throws SQLException, AuthorizeException {
        String resPoliciesStr = extractAccessConditions(accessControl.getItem().getAccessConditions());
        if (!resPoliciesStr.isEmpty()) {
            String msg = "Access condition (" + resPoliciesStr + ") was added to item";
            addProvenanceMetadata(context, item, msg);
        }
    }

    @Override
    public void setBitstreaPolicies(Context context, Bitstream bitstream, Item item, BulkAccessControlInput accessControl) throws SQLException, AuthorizeException {
        String accConditionsStr = extractAccessConditions(accessControl.getBitstream().getAccessConditions());
        if (!accConditionsStr.isEmpty()) {
            // Add suitable provenance
            String msg = "Access condition (" + accConditionsStr + ") was added to bitstream (" +
                    bitstream.getID() + ") of item";
            addProvenanceMetadata(context, item, msg);
        }
    }

    /**
     * Adds provenance metadata to an item, documenting changes made to its resource policies
     * and bitstream. This method records the user who performed the action, the action taken,
     * and the timestamp of the action. It also appends a bitstream provenance message generated
     * by the InstallItemService.
     *
     * @param context the current DSpace context, which provides details about the current user
     *                and authorization information.
     * @param item    the DSpace item to which the provenance metadata should be added.
     * @param msg     a custom message describing the action taken on the resource policies.
     * @throws SQLException         if there is a database access error while updating the item.
     * @throws AuthorizeException   if the current user is not authorized to add metadata to the item.
     */
    private void addProvenanceMetadata(Context context, Item item, String msg)
            throws SQLException, AuthorizeException {
        InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();
        String timestamp = DCDate.getCurrent().toString();
        EPerson e = context.getCurrentUser();
        StringBuilder prov = new StringBuilder();
        prov.append(msg).append(" by ").append(e.getFullName()).append(" (").append(e.getEmail()).append(") on ")
                .append(timestamp).append("\n");
        prov.append(installItemService.getBitstreamProvenanceMessage(context, item));
        itemService.addMetadata(context, item, MetadataSchemaEnum.DC.getName(),
                "description", "provenance", "en", prov.toString());
        //Update item in DB
        itemService.update(context, item);
    }
    private void addProvenanceForItem(Context context, Item item, String resPoliciesStr) throws SQLException, AuthorizeException {
        String msg = "Resource policies (" + (resPoliciesStr.isEmpty() ? "empty" : resPoliciesStr)
                + ") of item (" + item.getID() + ") were removed";
        addProvenanceMetadata(context, item, msg);
    }

    private void addProvenanceForBitstream(Context context, Bitstream bitstream, String resPoliciesStr) throws SQLException, AuthorizeException {
        ClarinItemService clarinItemService = ClarinServiceFactory.getInstance().getClarinItemService();
        List<Item> items = clarinItemService.findByBitstreamUUID(context, bitstream.getID());
        if (!items.isEmpty()) {
            Item item = items.get(0);
            String msg = "Resource policies (" + (resPoliciesStr.isEmpty() ? "empty" : resPoliciesStr)
                    + ") of bitstream (" + bitstream.getID() + ") were removed";
            addProvenanceMetadata(context, item, msg);
        }
    }
}
