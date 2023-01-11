package org.dspace.app.rest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.ClarinFeaturedServiceRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.clarin.ClarinFeaturedService;
import org.dspace.content.clarin.ClarinFeaturedServiceLink;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/refbox")
public class ClarinRefBoxController {
    private final Logger log = org.apache.logging.log4j.LogManager.getLogger(ClarinRefBoxController.class);

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    ItemService itemService;

    @Autowired
    private ConverterService converterService;

    @Autowired
    protected Utils utils;

    @RequestMapping(method = RequestMethod.GET, value = "/services")
    public Page<ClarinFeaturedServiceRest> getServices(@RequestParam(name = "id", required = false) UUID id,
                                                       HttpServletResponse response,
                                                       HttpServletRequest request, Pageable pageable)
            throws SQLException, AuthorizeException, IOException, IOException {
        // Get context
        Context context = ContextUtil.obtainCurrentRequestContext();
        if (Objects.isNull(context)) {
            throw new RuntimeException("Cannot obtain the context from the request.");
        }

        // Get item
        Item item = itemService.find(context, id);
        if (Objects.isNull(item)) {
            throw new NotFoundException("Cannot find the item with the uuid: " + id);
        }

        List<ClarinFeaturedService> featuredServiceList = new ArrayList<>();

        // Get services from configuration
        List<String> featuredServiceNames = Arrays.asList(configurationService.getArrayProperty("featured.services"));
        for (String featuredServiceName : featuredServiceNames) {
            // Get fullname, url and description of the featured service from the cfg
            String fullName = configurationService.getProperty("featured.service." + featuredServiceName + ".fullname");
            String url = configurationService.getProperty("featured.service." + featuredServiceName + ".url");
            String description = configurationService.getProperty("featured.service." + featuredServiceName + ".description");

            if (StringUtils.isBlank(url)) {
                throw new RuntimeException("The configuration property: `featured.service." + featuredServiceName +
                        ".url cannot be empty!");
            }

            // Check if the item has metadata for this featured service, if it doesn't have - do NOT return the
            // featured service.
            List<MetadataValue> itemMetadata = itemService.getMetadata(item, "local", "featuredService",
                    featuredServiceName, Item.ANY, false);
            if (CollectionUtils.isEmpty(itemMetadata)) {
                continue;
            }

            // Add the fullname, url, description, links to the REST object
            ClarinFeaturedService clarinFeaturedService = new ClarinFeaturedService();
            clarinFeaturedService.setName(fullName);
            clarinFeaturedService.setUrl(url);
            clarinFeaturedService.setDescription(description);
            clarinFeaturedService.setFeaturedServiceLinks(mapFeaturedServiceLinks(itemMetadata));

            featuredServiceList.add(clarinFeaturedService);
        }

        return converterService.toRestPage(featuredServiceList, pageable, utils.obtainProjection());
    }

    private List<ClarinFeaturedServiceLink> mapFeaturedServiceLinks(List<MetadataValue> itemMetadata) {
        List<ClarinFeaturedServiceLink> featuredServiceLinkList = new ArrayList<>();

        for (MetadataValue mv : itemMetadata) {
            if (Objects.isNull(mv)) {
                log.error("The metadata value object is null!");
                continue;
            }

            // The featured service key and value are stored like `<KEY>|<VALUE>`, must split it by `|`
            String metadataValue = mv.getValue();
            if (StringUtils.isBlank(metadataValue)) {
                log.error("The value of the metadata value object is null!");
                continue;
            }

            List<String> keyAndValue = List.of(metadataValue.split("\\|"));
            if (keyAndValue.size() < 2) {
                log.error("Cannot properly split the key and value from the metadata value!");
                continue;
            }

            // Create REST object with key and value
            ClarinFeaturedServiceLink clarinFeaturedServiceLink = new ClarinFeaturedServiceLink();
            clarinFeaturedServiceLink.setKey(keyAndValue.get(0));
            clarinFeaturedServiceLink.setValue(keyAndValue.get(1));

            featuredServiceLinkList.add(clarinFeaturedServiceLink);
        }

        return featuredServiceLinkList;
    }

//    @RequestMapping(method = RequestMethod.GET, value = "/citations/cmdi")
//    public ResponseEntity getCmdiCitation(@PathVariable String id, HttpServletResponse response,
//                                          HttpServletRequest request)
//            throws SQLException, AuthorizeException, IOException, IOException {
//    }
//
//    @RequestMapping(method = RequestMethod.GET, value = "/citations/bibtex")
//    public ResponseEntity getBibtexCitation(@PathVariable String id, HttpServletResponse response,
//                                            HttpServletRequest request)
//            throws SQLException, AuthorizeException, IOException, IOException {
//    }
}
