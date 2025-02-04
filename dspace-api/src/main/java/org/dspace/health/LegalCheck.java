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
import java.util.UUID;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.dspace.core.Context;

/**
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class LegalCheck extends Check {
    private ClarinLicenseLabelService clarinLicenseLabelService =
            ClarinServiceFactory.getInstance().getClarinLicenseLabelService();
    private ClarinLicenseService clarinLicenseService =
            ClarinServiceFactory.getInstance().getClarinLicenseService();
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService =
            ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();

    private Map<String, Integer> licensesCount = new HashMap<>();
    private List<ClarinLicense> clarinLicenses = new ArrayList<>();

    @Override
    protected String run(ReportInfo ri) {
        Context context = new Context();
        StringBuilder sb = new StringBuilder();

        List<ClarinLicenseLabel> labels;
        List<ClarinLicense> licenses;
        List<ClarinLicenseResourceMapping> licenseResourceMappings;
        try {
            labels = clarinLicenseLabelService.findAll(context); //finds all license labels
            licenses = clarinLicenseService.findAll(context); //finds all licenses
            licenseResourceMappings = clarinLicenseResourceMappingService.findAll(context);
        } catch (SQLException | AuthorizeException e) {
            System.out.println("Exception occurs hereee");
            throw new RuntimeException(e);
        }

        sb.append('\n');
        for (ClarinLicenseLabel label : labels) {
            String l = label.getLabel();
//            label.isExtended();//false
            sb.append(" label: ").append(l);
        }
        sb.append('\n');
        for (ClarinLicense license : licenses) {
            String l = license.getName();
            sb.append(" license: ").append(l);
        }
        sb.append('\n');
        for (ClarinLicenseResourceMapping licenseResourceMapping : licenseResourceMappings) {
            sb.append(" licenseResourceMapping: ").append(licenseResourceMapping.getLicense().getName());
        }

        Iterator<Item> items;
        ItemService itemService = ContentServiceFactory.getInstance().getItemService();
        try {
            items = itemService.findAll(context); // find all items
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        for (Iterator<Item> it = items; it.hasNext(); ) {
            Item item = it.next();

            sb.append('\n');
//            sb.append(item.toString()).append("---").append(item.getBundles()
//            .toString()).append("---").append(item.getBundles().size());
//            sb.append('\n');
            //some items do not have a bitstream
            // TODO bitstreams can be empty
            List<Bitstream> bitstreams = item.getBundles(Constants.DEFAULT_BUNDLE_NAME).get(0).getBitstreams();
            if (!bitstreams.isEmpty()) {
                // one bitstream is enough as there is only one license for all bitstreams in item
                Bitstream firstBitstream = bitstreams.get(0);
                System.out.println("First bitstream of item is "
                        + firstBitstream.getName() + " " + firstBitstream.getID());
                UUID uuid = firstBitstream.getID(); // compare

                try {
                    List<ClarinLicenseResourceMapping> clarinLicenseResourceMappingList =
                            clarinLicenseResourceMappingService.findByBitstreamUUID(context, uuid);
                    for (ClarinLicenseResourceMapping clarinLicenseResourceMapping :
                            clarinLicenseResourceMappingList) {
                        System.out.println("size : " +
                                clarinLicenseResourceMapping.getLicense().getLicenseLabels().size());
                        System.out.println("--- " +
                                clarinLicenseResourceMapping.getLicense().getID() + " " +
                                clarinLicenseResourceMapping.getLicense().getName() + " ---");
                        System.out.println("Get license labels " +
                                clarinLicenseResourceMapping.getLicense().getLicenseLabels());
                        System.out.println("get(0).getLabel() " +
                                clarinLicenseResourceMapping.getLicense().getLicenseLabels().get(0).getLabel());
                        System.out.println("is extended " +
                                clarinLicenseResourceMapping.getLicense().getLicenseLabels().get(0).isExtended());
                        if (!clarinLicenseResourceMapping.getLicense().getLicenseLabels().get(0).isExtended()) {
                            licensesCount.put(
                                    clarinLicenseResourceMapping.getLicense().getLicenseLabels().get(0).getLabel(),
                                    licensesCount.getOrDefault(clarinLicenseResourceMapping.getLicense()
                                            .getLicenseLabels().get(0).getLabel(), 0) + 1);
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

//                for (ClarinLicenseResourceMapping licenseResourceMapping : licenseResourceMappings) {
//                    if (licenseResourceMapping.getBitstream().getID() == uuid) {
//                        // pridat do listu skor license id
//                        System.out.println(" PRIDAJ DO HASH MAP " + uuid);
//                        System.out.println("License id " + licenseResourceMapping.getLicense().getID());
//
//                        clarinLicenses.add(licenseResourceMapping.getLicense());
//
//                    } else {
//                        System.out.println("NEPRIDAJ DO HASHMAP " + uuid);
//                    }
//                }
                System.out.println();


            } else {
                System.out.println("Item does not have an bitstream.");
            }

            System.out.println("Item " + " is " + item.getName());
        }

        sb.append("RESULT:\n");
        for (Map.Entry<String, Integer> result : licensesCount.entrySet()) {
            sb.append(result.getKey()).append(": ").append(result.getValue());
        }
        // sb.append(clarinLicenses.toString());

        context.close();
        return sb.toString();
    }
}
