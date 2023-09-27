/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.util.Util;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.*;
import org.dspace.content.*;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.core.Constants;
import org.dspace.util.FileTreeViewGenerator;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

public class MetadataBitstreamRestRepositoryIT extends AbstractControllerIntegrationTest {

    private static final String HANDLE_ID = "123456789/36";
    private static final String METADATABITSTREAM_ENDPOINT = "/api/core/metadatabitstream/";
    private static final String METADATABITSTREAM_SEARCH_BY_HANDLE_ENDPOINT = METADATABITSTREAM_ENDPOINT + "search/byHandle";
    private static final String FILE_GRP_TYPE = "ORIGINAL";
    private static final String AUTHOR = "Test author name";
    private Collection col;

    private Item publicItem;
    private Bitstream bts;
    private Bundle bundle;
    private Boolean canPreview = false;

    @Autowired
    ClarinLicenseResourceMappingService licenseService;

    @Autowired
    AuthorizeService authorizeService;


    @Test
    public void findByHandleNullHandle() throws Exception {
        getClient().perform(get(METADATABITSTREAM_SEARCH_BY_HANDLE_ENDPOINT))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void findByHandle() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        publicItem = ItemBuilder.createItem(context, col)
                .withAuthor(AUTHOR)
                .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bts = BitstreamBuilder.
                    createBitstream(context, publicItem, is)
                    .withName("Bitstream")
                    .withDescription("Description")
                    .withMimeType("application/x-gzip")
                    .build();
        }

        String identifier = null;
        if (publicItem != null && publicItem.getHandle() != null)
        {
            identifier = "handle/" + publicItem.getHandle();
        }
        else if (publicItem != null)
        {
            identifier = "item/" + publicItem.getID();
        }
        else
        {
            identifier = "id/" + bts.getID();
        }
        String url = "/bitstream/"+identifier+"/";
        try
        {
            if (bts.getName() != null)
            {
                url += Util.encodeBitstreamName(bts.getName(), "UTF-8");
            }
        }
        catch (UnsupportedEncodingException uee)
        {

        }

        url += "?sequence=" + bts.getSequenceID();

        String isAllowed = "n";
        try {
            if (authorizeService.authorizeActionBoolean(context, bts, Constants.READ)) {
                isAllowed = "y";
            }
        } catch (SQLException e) {/* Do nothing */}

        url += "&isAllowed=" + isAllowed;

        context.restoreAuthSystemState();
        List<Bundle> bundles = publicItem.getBundles(FILE_GRP_TYPE);
        for (Bundle bundle : bundles) {
            bundle.getBitstreams().stream().forEach(bitstream -> {
                List<ClarinLicenseResourceMapping> clarinLicenseResourceMappings = null;
                try {
                    clarinLicenseResourceMappings = licenseService.findByBitstreamUUID(context, bitstream.getID());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                if ( clarinLicenseResourceMappings != null && clarinLicenseResourceMappings.size() > 0) {
                    ClarinLicenseResourceMapping licenseResourceMapping = clarinLicenseResourceMappings.get(0);
                    ClarinLicense clarinLicense = licenseResourceMapping.getLicense();
                    canPreview = clarinLicense.getClarinLicenseLabels().stream()
                            .anyMatch(clarinLicenseLabel -> clarinLicenseLabel.getLabel().equals("PUB"));
                }
            });
        }
        getClient().perform(get(METADATABITSTREAM_SEARCH_BY_HANDLE_ENDPOINT)
                        .param("handle", publicItem.getHandle())
                        .param("fileGrpType", FILE_GRP_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._embedded.metadatabitstreams").exists())
                .andExpect(jsonPath("$._embedded.metadatabitstreams").isArray())
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].name")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString("Bitstream"))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].description")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString(bts.getFormatDescription(context)))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].format")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString(bts.getFormat(context).getMIMEType()))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].fileSize")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString(FileTreeViewGenerator.humanReadableFileSize(bts.getSizeBytes())))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].canPreview")
                        .value(Matchers.containsInAnyOrder(Matchers.is(canPreview))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].fileInfo").exists())
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].checksum")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString(bts.getChecksum()))))
                .andExpect(jsonPath("$._embedded.metadatabitstreams[*].href")
                        .value(Matchers.containsInAnyOrder(Matchers.containsString(url))));


    }

    @Test
    public void findByHandleEmptyFileGrpType() throws Exception {
        getClient().perform(get(METADATABITSTREAM_SEARCH_BY_HANDLE_ENDPOINT)
                .param("handle", HANDLE_ID)
                .param("fileGrpType", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(0)))
                .andExpect(jsonPath("$.page.totalPages", is(0)))
                .andExpect(jsonPath("$.page.size", is(20)))
                .andExpect(jsonPath("$.page.number", is(0)))
                .andExpect(jsonPath("$._links.self.href", Matchers.containsString(METADATABITSTREAM_SEARCH_BY_HANDLE_ENDPOINT + "?handle=" + HANDLE_ID + "&fileGrpType=")));
    }

    @Test
    public void searchMethodsExist() throws Exception {

        getClient().perform(get("/api/core/metadatabitstreams"))
                .andExpect(status().is5xxServerError());

        getClient().perform(get("/api/core/metadatabitstreams/search"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._links.byHandle", notNullValue()));
    }
}
