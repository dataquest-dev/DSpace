/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.dspace.app.util.CollectionDropDown;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author LINDAT/CLARIN dev team
 */
public class ItemCheck extends Check {

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    private BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private MetadataValueService metadataValueService = ContentServiceFactory.getInstance().getMetadataValueService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    private XmlWorkflowItemService workflowItemService =
            XmlWorkflowServiceFactory.getInstance().getXmlWorkflowItemService();
    private HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();


    @Override
    public String run(ReportInfo ri) {
        String ret = "";
        JSONObject root = new JSONObject();
        int tot_cnt = 0;
        Context context = new Context();
        try {
            JSONArray communitiesArray = new JSONArray();
            for (Map.Entry<String, Integer> name_count : getCommunities(context)) {
                String comName = name_count.getKey();
                int comSize = name_count.getValue();
                ret += String.format("Community [%s]: %d\n",
                        comName, comSize);
                tot_cnt += name_count.getValue();
                JSONObject oneCommunity = new JSONObject();
                oneCommunity.put("name", comName);
                oneCommunity.put("size", comSize);
                communitiesArray.put(oneCommunity);
            }
            root.put("communities", communitiesArray);
        } catch (SQLException e) {
            error(e);
        } finally {
            if (context.isValid()) {
                context.abort();
            }
        }

        try {
            JSONObject colSizesInfo = new JSONObject();
            ret += "\nCollection sizes:\n";
            ret += getCollectionSizesInfo(context, colSizesInfo);
            root.put("collectionsSizesInfo", colSizesInfo);
        } catch (SQLException e) {
            error(e);
        }

        ret += String.format(
            "\nPublished items (archived, not withdrawn): %d\n", tot_cnt);
        root.put("publishedItems", tot_cnt);
        try {
            int withdrawnItems = itemService.countWithdrawnItems(context);
            ret += String.format(
                "Withdrawn items: %d\n", withdrawnItems);
            root.put("withdrawnItems", withdrawnItems);
            int notPublishedItems = itemService.countNotArchivedItems(context);
            ret += String.format(
                "Not published items (in workspace or workflow mode): %d\n",
                notPublishedItems);
            root.put("notPublishedItems", notPublishedItems);

            JSONArray stagesCountArray = new JSONArray();
            for (Map.Entry<Integer, Long> row : workspaceItemService.getStageReachedCounts(context)) {
                ret += String.format("\tIn Stage %s: %s\n",
                                     row.getKey(), //"stage_reached"
                                     row.getValue() //"cnt"
                );
                JSONObject oneStage = new JSONObject();
                oneStage.put("stage", row.getKey());
                oneStage.put("count", row.getValue());
                stagesCountArray.put(oneStage);
            }
            root.put("stagesCounts", stagesCountArray);

            int waitingForApprovalCount = workflowItemService.countAll(context);
            ret += String.format(
                "\tWaiting for approval (workflow items): %d\n",
                waitingForApprovalCount);
            root.put("waitingForApproval", waitingForApprovalCount);
        } catch (SQLException e) {
            error(e);
        }

        try {
            ret += getObjectSizesInfo(context, root);
            context.complete();
        } catch (SQLException e) {
            error(e);
        }

        this.setReportJson(root);
        return ret;
    }


    public String getObjectSizesInfo(Context context, JSONObject jo) throws SQLException {
        StringBuilder sb = new StringBuilder();

        String bitstreamsCount = String.valueOf(bitstreamService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Bitstream", bitstreamsCount));
        jo.put("bitstreamsCount", bitstreamsCount);

        String bundlesCount = String.valueOf(bundleService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Bundle", bundlesCount));
        jo.put("bundlesCount", bundlesCount);

        String collectionsCount = String.valueOf(collectionService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Collection", collectionsCount));
        jo.put("collectionsCount", collectionsCount);

        String communitiesCount = String.valueOf(communityService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Community", communitiesCount));
        jo.put("communitiesCount", communitiesCount);

        String metadataValuesCount = String.valueOf(metadataValueService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "MetadataValue", metadataValuesCount));
        jo.put("metadataValuesCount", metadataValuesCount);

        String ePersonsCount = String.valueOf(ePersonService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "EPerson", ePersonsCount));
        jo.put("ePersonsCount", ePersonsCount);

        String itemsCount = String.valueOf(itemService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Item", itemsCount));
        jo.put("itemsCount", itemsCount);

        String handlesCount = String.valueOf(handleService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Handle", handlesCount));
        jo.put("handlesCount", handlesCount);

        String groupsCount = String.valueOf(groupService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "Group", groupsCount));
        jo.put("groupsCount", groupsCount);

        String basicWorkflowItemsCount = String.valueOf(workflowItemService.countAll(context));
        sb.append(String.format("Count %-14s: %s\n", "BasicWorkflowItem", basicWorkflowItemsCount));
        jo.put("basicWorkflowItemsCount", basicWorkflowItemsCount);

        String workspaceItemsCount = String.valueOf(workspaceItemService.countTotal(context));
        sb.append(String.format("Count %-14s: %s\n", "WorkspaceItem", workspaceItemsCount));
        jo.put("workspaceItemsCount", workspaceItemsCount);

        return sb.toString();
    }

    public String getCollectionSizesInfo(final Context context, JSONObject jo) throws SQLException {
        final StringBuffer ret = new StringBuffer();
        List<Map.Entry<Collection, Long>> colBitSizes = collectionService
                .getCollectionsWithBitstreamSizesTotal(context);
        long total_size = 0;

        Collections.sort(colBitSizes, new Comparator<Map.Entry<Collection, Long>>() {
            @Override
            public int compare(Map.Entry<Collection, Long> o1, Map.Entry<Collection, Long> o2) {
                try {
                    String p1 = CollectionDropDown.collectionPath(context, o1.getKey());
                    String p2 = CollectionDropDown.collectionPath(context, o2.getKey());
                    return p1.compareTo(p2);
                } catch (Exception e) {
                    ret.append(e.getMessage());
                }
                return 0;
            }
        });

        JSONArray collectionsSizesArray = new JSONArray();
        for (Map.Entry<Collection, Long> row : colBitSizes) {
            Long size = row.getValue();
            total_size += size;
            Collection col = row.getKey();
            String colPath = CollectionDropDown.collectionPath(context, col);
            String colSize = FileUtils.byteCountToDisplaySize((long) size);
            ret.append(String.format(
                    "\t%s:  %s\n", colPath, colSize));
            JSONObject oneColSize = new JSONObject();
            oneColSize.put("path", colPath);
            oneColSize.put("size", colSize);
            collectionsSizesArray.put(oneColSize);
        }
        jo.put("collectionSizes", collectionsSizesArray);

        String totalSizeToDisplay = FileUtils.byteCountToDisplaySize(total_size);
        ret.append(String.format(
                "Total size:              %s\n", totalSizeToDisplay));
        jo.put("totalSize", totalSizeToDisplay);


        int resourceWOPolicyCount = bitstreamService.countBitstreamsWithoutPolicy(context);
        ret.append(String.format(
                "Resource without policy: %d\n", resourceWOPolicyCount));
        jo.put("resourceWOPolicy", resourceWOPolicyCount);

        int deletedBitstreamsCount = bitstreamService.countDeletedBitstreams(context);
        ret.append(String.format(
                "Deleted bitstreams:      %d\n", deletedBitstreamsCount));
        jo.put("deletedBitstreams", deletedBitstreamsCount);

        String list_str = "";
        JSONArray orphanBitstreamsArray = new JSONArray();
        List<Bitstream> bitstreamOrphans = bitstreamService.getNotReferencedBitstreams(context);
        for (Bitstream orphan : bitstreamOrphans) {
            UUID id = orphan.getID();
            JSONObject oneOrphanBitstream = new JSONObject();
            oneOrphanBitstream.put("uuid", id.toString());
            orphanBitstreamsArray.put(oneOrphanBitstream);
            list_str += String.format("%s, ", id);
        }
        ret.append(String.format(
                "Orphan bitstreams:       %d [%s]\n", bitstreamOrphans.size(), list_str));
        jo.put("orphanBitstreamsCount", bitstreamOrphans.size());
        jo.put("orphanBitstreams", orphanBitstreamsArray);

        return ret.toString();
    }

    public List<Map.Entry<String, Integer>> getCommunities(Context context)
        throws SQLException {

        List<Map.Entry<String, Integer>> cl = new java.util.ArrayList<>();
        List<Community> top_communities = communityService.findAllTop(context);
        for (Community c : top_communities) {
            cl.add(
                new java.util.AbstractMap.SimpleEntry<>(c.getName(), itemService.countItems(context, c))
            );
        }
        return cl;
    }
}
