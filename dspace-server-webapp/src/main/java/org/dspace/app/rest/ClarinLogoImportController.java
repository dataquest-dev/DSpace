package org.dspace.app.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.CollectionRest;
import org.dspace.app.rest.model.CommunityRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/api/clarin/import/logo/")
public class ClarinLogoImportController {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    private CommunityService communityService;
    @Autowired
    private CollectionService collectionService;
    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private ConverterService converter;
    @Autowired
    private Utils utils;

    @PreAuthorize("hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = POST, path = "/community")
    public CommunityRest addCommunityLogo(HttpServletRequest request) throws SQLException, AuthorizeException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        //get community and bitstream UUIDs
        String communityUUIDString = request.getParameter("community_id");
        UUID communityUUID = UUID.fromString(communityUUIDString);
        String bitstreamUUIDString = request.getParameter("bitstream_id");
        UUID bitstreamUUID = UUID.fromString(bitstreamUUIDString);

        //find community and bitstream
        Community community = communityService.find(context, communityUUID);
        Bitstream newLogo = bitstreamService.find(context, bitstreamUUID);

        //controls
        if (Objects.isNull(community) || Objects.isNull(newLogo)) {
            throw new UnprocessableEntityException(
                    "The input data are entered incorrectly!");
        }
        if (!newLogo.getFormat(context).getShortDescription().equals("Unknown")) {
            throw new UnprocessableEntityException(
                    "The bitstream format of bitstream with id: " + newLogo.getID() + " is not Unknown!");
        }
        if (community.getLogo() != null) {
            throw new UnprocessableEntityException(
                    "The community with the given uuid already has a logo: " + community.getID());
        }

        //add logo to community
        communityService.addLogo(context, community, newLogo);
        communityService.update(context, community);
        bitstreamService.update(context, newLogo);
        log.error("Logo with id: + " + newLogo.getID() + " was successfully added to community " +
                "with id: " + community.getID());

        CommunityRest communityRest = converter.toRest(community, utils.obtainProjection());
        context.commit();
        return communityRest;
    }

    @PreAuthorize("hasAuthority('AUTHENTICATED')")
    @RequestMapping(method = POST, path = "/collection")
    public CollectionRest addCollectionLogo(HttpServletRequest request) throws SQLException, AuthorizeException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        //get collection and bitstream UUIDs
        String collectionUUIDString = request.getParameter("collection_id");
        UUID collectionUUID = UUID.fromString(collectionUUIDString);
        String bitstreamUUIDString = request.getParameter("bitstream_id");
        UUID bitstreamUUID = UUID.fromString(bitstreamUUIDString);

        //find community and bitstream
        Collection collection = collectionService.find(context, collectionUUID);
        Bitstream newLogo = bitstreamService.find(context, bitstreamUUID);

        //controls
        if (Objects.isNull(collection) || Objects.isNull(newLogo)) {
            throw new UnprocessableEntityException(
                    "The input data are entered incorrectly!");
        }
        if (!newLogo.getFormat(context).getShortDescription().equals("Unknown")) {
            throw new UnprocessableEntityException(
                    "The bitstream format of bitstream with id: " + newLogo.getID() + " is not Unknown!");
        }
        if (collection.getLogo() != null) {
            throw new UnprocessableEntityException(
                    "The collection with the given uuid already has a logo: " + collection.getID());
        }

        //add logo to community
        collectionService.addLogo(context, collection, newLogo);
        collectionService.update(context, collection);
        bitstreamService.update(context, newLogo);
        log.error("Logo with id: + " + newLogo.getID() + " was successfully added to collection " +
                "with id: " + collection.getID());

        CollectionRest collectionRest = converter.toRest(collection, utils.obtainProjection());
        context.commit();
        return collectionRest;
    }
}
