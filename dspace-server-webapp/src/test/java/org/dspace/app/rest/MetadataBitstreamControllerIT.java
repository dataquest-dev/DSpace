/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.junit.Test;
import org.purl.sword.base.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class MetadataBitstreamControllerIT extends AbstractControllerIntegrationTest {
    private static final String METADATABITSTREAM_ENDPOINT = "/api/" + ItemRest.CATEGORY + "/" + ItemRest.PLURAL_NAME;
    private static final String ALL_ZIP_PATH = "allzip";
    private static final String HANDLE_PARAM = "handleId";
    private static final String AUTHOR = "Test author name";
    private Collection col;

    private Item publicItem;
    private Bitstream bts;

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    BitstreamService bitstreamService;


    @Override
    public void setUp() throws Exception {
        super.setUp();
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
                    .withMimeType("application/zip")
                    .build();
        }
        context.restoreAuthSystemState();
    }

    @Test
    public void downloadAllZip() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);

        MvcResult result = getClient(token).perform(get(METADATABITSTREAM_ENDPOINT + "/" + publicItem.getID() +
                        "/" + ALL_ZIP_PATH).param(HANDLE_PARAM, publicItem.getHandle()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] responseBytes = result.getResponse().getContentAsByteArray();
        long actualContentLength = responseBytes.length;

        String contentLengthHeader = result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH);
        String contentTypeHeader = result.getResponse().getHeader("Content-Type");
        String contentDispositionHeader = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertNotNull(contentLengthHeader);
        assertEquals(String.valueOf(actualContentLength), contentLengthHeader);

        assertNotNull(contentTypeHeader);
        assertTrue(contentTypeHeader.startsWith("application/zip"));

        assertNotNull(contentDispositionHeader);
        assertTrue(contentDispositionHeader.startsWith("attachment"));
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(responseBytes))) {
            ZipEntry entry = zipIn.getNextEntry();
            assertNotNull("ZIP should contain at least one entry.", entry);
            assertEquals("Bitstream", entry.getName());
        }
    }
}
