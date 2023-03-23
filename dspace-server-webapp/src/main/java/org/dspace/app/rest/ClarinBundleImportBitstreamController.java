package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.BundleRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinBitstreamService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.criteria.CriteriaBuilder;
import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;

@RestController
@RequestMapping("/api/clarin/import/" + BundleRest.CATEGORY + "/" + BundleRest.PLURAL_NAME + "/" +
        REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID + "/" + BitstreamRest.PLURAL_NAME)
public class ClarinBundleImportBitstreamController {
    private static final Logger log = LogManager.getLogger();
    @Autowired
    private BundleService bundleService;

    @Autowired
    private ClarinBitstreamService clarinBitstreamService;

    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    private MetadataConverter metadataConverter;

    @Autowired
    private ItemService itemService;

    @Autowired
    private BitstreamFormatService bitstreamFormatService;
    @Autowired
    private ConverterService converter;
    @Autowired
    private Utils utils;

    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method =  RequestMethod.POST)
    public BitstreamRest importBitstreamForExistingFile(HttpServletRequest request,
             @PathVariable UUID uuid) {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Contex is null!");
        }
        Bundle bundle = null;
        try {
            bundle = bundleService.find(context, uuid);
        } catch (SQLException e) {
            log.error("Something went wrong trying to find the Bundle with uuid: " + uuid, e);
        }
        if (bundle == null) {
            throw new ResourceNotFoundException("The given uuid did not resolve to a Bundle on the server: " + uuid);
        }
        BitstreamRest bitstreamRest = null;
        Bitstream bitstream = null;
        Item item = null;
        try {
            List<Item> items = bundle.getItems();
            if (!items.isEmpty()) {
                item = items.get(0);
            }
            if (item != null && !(authorizeService.authorizeActionBoolean(context, item, Constants.WRITE)
                    && authorizeService.authorizeActionBoolean(context, item, Constants.ADD))) {
                throw new AccessDeniedException("You do not have write rights to update the Bundle's item");
            }
            //process bitstream creation
            ObjectMapper mapper = new ObjectMapper();
            bitstreamRest = mapper.readValue(request.getInputStream(), BitstreamRest.class);
            //create empty bitstream
            bitstream = clarinBitstreamService.create(context, bundle);
            //internal_id contains path to file
            String internalId = request.getParameter("internal_id");
            bitstream.setInternalId(internalId);
            String storeNumberString = request.getParameter("storeNumber");
            bitstream.setStoreNumber(getIntegerFromString(storeNumberString));
            String sequenceIdString = request.getParameter("sequenceId");
            Integer sequenceId = getIntegerFromString(sequenceIdString);
            bitstream.setSequenceID(sequenceId);
            bitstream.setName(context, bitstreamRest.getName());
            //add bitstream Format
            String bitstreamFormatIdString = request.getParameter("bitstreamFormat");
            Integer bitstreamFormatId = getIntegerFromString(bitstreamFormatIdString);
            BitstreamFormat bitstreamFormat = bitstreamFormatService.find(context, bitstreamFormatId);
            if (Objects.isNull(bitstreamFormat)) {
                log.debug("Cannot add bitstream format with id: " + bitstreamFormatId +
                        " because the format doesn't exist. The bitstream with internal_id: " +
                        internalId + " is not imported!");
                bitstreamService.expunge(context, bitstream);
            } else {
                bitstream.setFormat(context, bitstreamFormat);
            }
            String deletedString = request.getParameter("deleted");
            boolean addedFile = true;
            if (!getBooleanFromString(deletedString)) {
                //add existed file
                //internal_id and store_number must be added to bitstream before
                if (clarinBitstreamService.addExistingFile(context, bitstream, bitstreamRest.getSizeBytes(),
                        bitstreamRest.getCheckSum().getValue(), bitstreamRest.getCheckSum().getCheckSumAlgorithm())) {
                    if (bitstreamRest.getMetadata() != null) {
                        metadataConverter.setMetadata(context, bitstream, bitstreamRest.getMetadata());
                    }
                    bitstreamService.update(context, bitstream);
                }
            } else {
                bitstream.setSizeBytes(bitstreamRest.getSizeBytes());
                bitstream.setChecksum(bitstreamRest.getCheckSum().getValue());
                bitstream.setChecksumAlgorithm(bitstreamRest.getCheckSum().getCheckSumAlgorithm());
                bitstreamService.delete(context, bitstream);
                bitstream = null;
            }
            if (item != null) {
                itemService.update(context, item);
            }
            bundleService.update(context, bundle);
            if (Objects.nonNull(bitstream)) {
                bitstreamRest = converter.toRest(bitstream, utils.obtainProjection());
            } else {
                bitstreamRest = null;
            }
            context.commit();
        } catch (AuthorizeException | SQLException | IOException e) {
            String message = "Something went wrong with trying to create the single bitstream for file with internal_id: "
                    + request.getParameter("internal_id")
                    + " for bundle with uuid: " + bundle.getID();
            log.error("message", e);
            throw new RuntimeException("message", e);
        }

        return bitstreamRest;
    }

    private boolean getBooleanFromString(String value) {
        boolean output = false;
        if (StringUtils.isNotBlank(value)) {
            output = Boolean.parseBoolean(value);
        }
        return output;
    }

    private Integer getIntegerFromString(String value) {
        Integer output = null;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        }
        return output;
    }
}
