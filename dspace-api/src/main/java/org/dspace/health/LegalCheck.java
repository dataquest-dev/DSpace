/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class LegalCheck extends Check {
    private ClarinLicenseLabelService clarinLicenseLabelService = ClarinServiceFactory.getInstance().getClarinLicenseLabelService();

    @Override
    protected String run(ReportInfo ri) {
        Context context = new Context();
        StringBuilder sb = new StringBuilder();
        List<ClarinLicenseLabel> labels;
        try {
            labels = clarinLicenseLabelService.findAll(context); //finds all license labels
        } catch (SQLException | AuthorizeException e) {
            System.out.println("Exception occurs hereee");
            throw new RuntimeException(e);
        }

        for (ClarinLicenseLabel label : labels) {
            String l = label.getLabel();
            sb.append(" label: ").append(l);
        }

        Iterator<Item> items;
        ItemService itemService = ContentServiceFactory.getInstance().getItemService();
        try {
            items = itemService.findAll(context);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        for (Iterator<Item> it = items; it.hasNext(); ) {
            Item item = it.next();

//            item.getItemService().
            ClarinLicenseResourceMapping clarinLicenseResourceMapping = new ClarinLicenseResourceMapping();
//            List<ClarinLicenseResourceMapping>



//            List<Bundle> bundles = item.getBundles(Constants.CONTENT_BUNDLE_NAME);
//            for (Bundle clarinBundle : bundles) {
//                List<Bitstream> bitstreamList = clarinBundle.getBitstreams();
//                for (Bitstream bundleBitstream : bitstreamList) {
//                    bundleBitstream.
//                }
//            }

            System.out.println("Item " + " is " + item.getName());
        }

        context.close();
        return sb.toString();
    }
}
