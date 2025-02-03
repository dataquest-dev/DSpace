/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import java.sql.SQLException;
import java.util.*;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.clarin.ClarinLicenseResourceMappingServiceImpl;
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
    private ClarinLicenseLabelService clarinLicenseLabelService = ClarinServiceFactory.getInstance().getClarinLicenseLabelService();
    private ClarinLicenseService clarinLicenseService = ClarinServiceFactory.getInstance().getClarinLicenseService();
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService = ClarinServiceFactory.getInstance().getClarinLicenseResourceMappingService();

    private HashMap<String, Integer> licensesCount = new HashMap<>();
    private List<ClarinLicense> licenseIDs = new ArrayList<>();

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
//            sb.append(item.toString()).append("---").append(item.getBundles().toString()).append("---").append(item.getBundles().size());
//            sb.append('\n');
            //some items do not have a bitstream
            // TODO bitstreams can be empty
            List<Bitstream> bitstreams = item.getBundles(Constants.DEFAULT_BUNDLE_NAME).get(0).getBitstreams();
            if (!bitstreams.isEmpty()) {
                // one bitstream is enough as there is only one license for all bitstreams in item
                Bitstream firstBitstream = bitstreams.get(0);
                System.out.println("First bitstream of item is " + firstBitstream.getName() + " " + firstBitstream.getID());
                UUID uuid = firstBitstream.getID(); // compare

                for (ClarinLicenseResourceMapping licenseResourceMapping : licenseResourceMappings) {
                    if (licenseResourceMapping.getBitstream().getID() == uuid) {
                        // pridat do listu skor license id
                        System.out.println(" PRIDAJ DO HASH MAP " + uuid);
                        System.out.println("License id " + licenseResourceMapping.getLicense());
                        licenseIDs.add(licenseResourceMapping.getLicense());
                    }
                }

            } else {
                System.out.println("Item does not have an bitstream.");
            }

//            ClarinLicense clarinLicense = new ClarinLicense();
//            ClarinLicenseResourceMappingServiceImpl clarinLicenseResourceMappingService = new ClarinLicenseResourceMappingServiceImpl();
//            try {
//                List<ClarinLicenseResourceMapping> findbyLicenseID = clarinLicenseResourceMappingService.findAllByLicenseId(context, 5);
//                sb.append("-------->").append(findbyLicenseID.toString());
//            } catch (SQLException e) {
//                System.out.println("EXCEPTION OCCURRED");
//                throw new RuntimeException(e);
//            }

            System.out.println("Item " + " is " + item.getName());
        }

        sb.append(licenseIDs.toString());
        // prejst cez license id a zistit ake typy licencie (label ) to je

        context.close();
        return sb.toString();
    }
}
