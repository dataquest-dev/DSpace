/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.statistics.clarin;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.matomo.java.tracking.CustomVariable;
import org.matomo.java.tracking.MatomoException;
import org.matomo.java.tracking.MatomoRequest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Customized implementation of the ClarinMatomoTracker for the tracking the Item's bitstream downloading events
 *
 * The class is copied from UFAL/CLARIN-DSPACE (https://github.com/ufal/clarin-dspace) and modified by
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class ClarinMatomoBitstreamTracker extends ClarinMatomoTracker {
    /** log4j category */
    private static Logger log = Logger.getLogger(ClarinMatomoBitstreamTracker.class);

    private final ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();

    @Autowired
    ItemService itemService;

    private int siteId;

    public ClarinMatomoBitstreamTracker() {
        super();
        siteId = configurationService.getIntProperty("matomo.tracker.bitstream.site_id");
    }

    /**
     * Customize the matomo request parameters
     *
     * @param matomoRequest with the default parameters
     * @param request current request
     */
    @Override
    protected void preTrack(Context context, MatomoRequest matomoRequest, Item item, HttpServletRequest request) {
        super.preTrack(context, matomoRequest, item, request);

        matomoRequest.setSiteId(siteId);
        log.debug("Logging to site " + matomoRequest.getSiteId());
        String itemIdentifier = getItemIdentifier(item);
        if (StringUtils.isBlank(itemIdentifier)) {
            log.error("Cannot track the item without Identifier URI.");
        } else {
            // Set PageURL to handle identifier
            matomoRequest.setDownloadUrl(getFullURL(request));
            matomoRequest.setActionUrl(itemIdentifier);
        }
        try {
            matomoRequest.setPageCustomVariable(new CustomVariable("source", "bitstream"), 1);
        } catch (MatomoException e) {
            log.error(e);
        }
    }

    /**
     * Get the Item's Handle URI from where the bitstream is downloaded
     *
     * @param item from where the bitstream is downloaded
     * @return handle uri
     */
    private String getItemIdentifier(Item item) {
        List<MetadataValue> mv = itemService.getMetadata(item, "dc", "identifier", "uri", Item.ANY, false);
        if (CollectionUtils.isEmpty(mv)) {
            log.error("The item doesn't have the metadata `dc.identifier.uri` - something went wrong.");
            return "";
        }
        return mv.get(0).getValue();
    }
}
