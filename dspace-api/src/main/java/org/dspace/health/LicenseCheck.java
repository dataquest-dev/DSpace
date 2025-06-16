/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.amazonaws.util.CollectionUtils;
import com.google.common.base.CaseFormat;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This check provides information about the number of items categorized by clarin license type (PUB/RES/ACA),
 * as well as details about items that are missing bundles, bitstreams, or license mappings.
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class LicenseCheck extends Check {
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService =
            ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();

    private Map<String, Integer> licensesCount = new HashMap<>();
    private Map<String, List<UUID>> problemItems = new HashMap<>();

    @Override
    protected String run(ReportInfo ri) {
        Context context = new Context();
        StringBuilder sb = new StringBuilder();
        JSONObject root = new JSONObject();

        Iterator<Item> items;
        ItemService itemService = ContentServiceFactory.getInstance().getItemService();
        try {
            items = itemService.findAll(context);
        } catch (SQLException e) {
            throw new RuntimeException("Error while fetching items. ", e);
        }

        for (Iterator<Item> it = items; it.hasNext(); ) {
            Item item = it.next();

            List<Bundle> bundles = item.getBundles(Constants.DEFAULT_BUNDLE_NAME);
            if (bundles.isEmpty()) {
                licensesCount.put("no bundle", licensesCount.getOrDefault("no bundle", 0) + 1);
                continue;
            }

            if (item.getBundles(Constants.LICENSE_BUNDLE_NAME).isEmpty()) {
                problemItems.computeIfAbsent(
                        "UUIDs of items without license bundle", k -> new ArrayList<>()).add(item.getID());
            }

            List<Bitstream> bitstreams = bundles.get(0).getBitstreams();
            if (bitstreams.isEmpty()) {
                problemItems.computeIfAbsent(
                        "UUIDs of items without bitstreams", k -> new ArrayList<>()).add(item.getID());
                continue;
            }

            // one bitstream is enough as there is only one license for all bitstreams in item
            Bitstream firstBitstream = bitstreams.get(0);
            UUID uuid = firstBitstream.getID();
            try {
                List<ClarinLicenseResourceMapping> clarinLicenseResourceMappingList =
                        clarinLicenseResourceMappingService.findByBitstreamUUID(context, uuid);

                if (CollectionUtils.isNullOrEmpty(clarinLicenseResourceMappingList)) {
                    log.error("No license mapping found for bitstream with uuid {}", uuid);
                    problemItems.computeIfAbsent(
                            "UUIDs of bitstreams without license mappings", k -> new ArrayList<>()).add(uuid);
                    continue;
                }

                // Every resource mapping between license and the bitstream has only one record,
                // because the bitstream has unique UUID, so get the first record from the List
                ClarinLicenseResourceMapping clarinLicenseResourceMapping = clarinLicenseResourceMappingList.get(0);

                ClarinLicenseLabel nonExtendedLabel =
                        clarinLicenseResourceMapping.getLicense().getNonExtendedClarinLicenseLabel();

                if (Objects.isNull(nonExtendedLabel)) {
                    log.error("Item {} with id {} does not have non extended license label.",
                            item.getName(), item.getID());
                } else {
                    licensesCount.put(nonExtendedLabel.getLabel(),
                            licensesCount.getOrDefault(nonExtendedLabel.getLabel(), 0) + 1);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error while fetching ClarinLicenseResourceMapping by Bitstream UUID: " +
                        uuid, e);
            }
        }

        JSONArray licensesArray = new JSONArray();
        for (Map.Entry<String, Integer> result : licensesCount.entrySet()) {
            JSONObject oneLicense = new JSONObject();

            sb.append(String.format("%-20s: %d\n", result.getKey(), result.getValue()));
            oneLicense.put("type", result.getKey());
            oneLicense.put("count", result.getValue());

            licensesArray.put(oneLicense);
        }
        root.put("licenses", licensesArray);

        if (!problemItems.isEmpty()) {
            JSONArray problemItemsArray = new JSONArray();
            for (Map.Entry<String, List<UUID>> entry: problemItems.entrySet()) {
                List<UUID> uuids = entry.getValue();
                JSONObject oneProblemItem = new JSONObject();
                JSONArray problemIdsArray = new JSONArray();

                sb.append(String.format("\n%s: %d\n", entry.getKey(), uuids.size()));
                oneProblemItem.put("type", entry.getKey());
                oneProblemItem.put("count", uuids.size());
                for (UUID uuid : uuids) {
                    JSONObject oneProblemId = new JSONObject();
                    sb.append(String.format("     %s\n", uuid));
                    oneProblemId.put("id", uuid.toString());
                    problemIdsArray.put(oneProblemId);
                }
                oneProblemItem.put("problemIds", problemIdsArray);
                problemItemsArray.put(oneProblemItem);
            }
            root.put("problemItems", problemItemsArray);
        }

        context.close();
        this.setReportJson(root);
        return sb.toString();
    }
}
