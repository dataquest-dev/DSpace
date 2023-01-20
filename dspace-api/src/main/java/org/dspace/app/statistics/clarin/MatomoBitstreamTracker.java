package org.dspace.app.statistics.clarin;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.web.ContextUtil;
import org.matomo.java.tracking.CustomVariable;
import org.matomo.java.tracking.MatomoException;
import org.matomo.java.tracking.MatomoRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MatomoBitstreamTracker extends AbstractMatomoTracker {
    /** log4j category */
    private static Logger log = Logger.getLogger(MatomoBitstreamTracker.class);

    private final ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();
    private final ClarinItemService clarinItemService = ClarinServiceFactory.getInstance().getClarinItemService();

    @Autowired
    ItemService itemService;

    private int siteId;

    public MatomoBitstreamTracker() {
        super();
        siteId = configurationService.getIntProperty("matomo.tracker.bitstream.site_id");
    }

    /**
     * Add the more parameters to the Matomo request
     * @param matomoRequest
     * @param request
     */
    @Override
    protected void preTrack(Context context, MatomoRequest matomoRequest, Item item, HttpServletRequest request) {
        super.preTrack(context, matomoRequest, item, request);
        log.debug("Logging to site " + matomoRequest.getSiteId());

        matomoRequest.setSiteId(siteId);
        String itemIdentifier = getItemIdentifier(item);
        if (StringUtils.isBlank(itemIdentifier)) {
            log.error("Cannot track the item without Identifier URI.");
        } else {
            // Set PageURL to handle identifier
            matomoRequest.setDownloadUrl(itemIdentifier);
            matomoRequest.setActionUrl(itemIdentifier);
        }
        try {
            matomoRequest.setPageCustomVariable(new CustomVariable("source", "bitstream"), 1);
        } catch (MatomoException e) {
            log.error(e);
        }
    }

    private String getItemIdentifier(Item item) {
//        if (Objects.isNull(context)) {
//            log.error("The context is null.");
//            return "";
//        }
//
//        String requestPath = request.getServletPath();
//        if (StringUtils.isBlank(requestPath)) {
//            log.error("Request Context Path is null");
//            return "";
//        }
//
//        // /api/core/bitstreams/cc5e7724-b0b3-4b22-ab05-b5d296791a39/content
//        requestPath = StringUtils.substringBeforeLast(requestPath, "/");
//        String bitstreamUUID = StringUtils.substringAfterLast(requestPath, "/");
//
//        Item item = null;
//        try {
//            List<Item> itemList = clarinItemService.findByBitstreamUUID(context, UUID.fromString(bitstreamUUID));
//            if (CollectionUtils.isEmpty(itemList)) {
//                log.error("Cannot find the Item by the bitstream UUID: " + bitstreamUUID);
//                return "";
//            }
//            item = itemList.get(0);
//        } catch (SQLException e) {
//            log.error("Cannot find the Item by the bitstream UUID: " + bitstreamUUID + ", because: " + e.getSQLState());
//            return "";
//        }
//
//        if (Objects.isNull(item)) {
//            return "";
//        }

        List<MetadataValue> mv = itemService.getMetadata(item, "dc", "identifier", "uri", Item.ANY, false);
        if (CollectionUtils.isEmpty(mv)) {
            log.error("The item doesn't have the metadata `dc.identifier.uri` - something went wrong.");
            return "";
        }
        return mv.get(0).getValue();
    }
}
