/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

/**
 * This check provides information about the number of items categorized by license type.
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class LegalCheck extends Check {
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService =
            ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();

    private Map<String, Integer> licensesCount = new HashMap<>();

    @Override
    protected String run(ReportInfo ri) {
        Context context = new Context();
        StringBuilder sb = new StringBuilder();

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
                licensesCount.put("no license", licensesCount.getOrDefault("no license", 0) + 1);
                continue;
            }

            List<Bitstream> bitstreams = bundles.get(0).getBitstreams();
            if (bitstreams.isEmpty()) {
                licensesCount.put("no license", licensesCount.getOrDefault("no license", 0) + 1);
                continue;
            }

            // one bitstream is enough as there is only one license for all bitstreams in item
            Bitstream firstBitstream = bitstreams.get(0);
            UUID uuid = firstBitstream.getID();
            try {
                List<ClarinLicenseResourceMapping> clarinLicenseResourceMappingList =
                        clarinLicenseResourceMappingService.findByBitstreamUUID(context, uuid);

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

        for (Map.Entry<String, Integer> result : licensesCount.entrySet()) {
            sb.append(result.getKey()).append(": ").append(result.getValue()).append("\n");
        }

        context.close();
        return sb.toString();
    }
}
