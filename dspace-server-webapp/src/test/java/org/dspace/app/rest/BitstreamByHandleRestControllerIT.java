/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Constants;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Integration tests for {@link BitstreamByHandleRestController}.
 */
public class BitstreamByHandleRestControllerIT extends AbstractControllerIntegrationTest {

    private static final String ENDPOINT_BASE = "/api/core/bitstreams/handle";

    @Autowired
    AuthorizeService authorizeService;

    @Autowired
    BitstreamService bitstreamService;

    @Test
    public void downloadBitstreamByHandle() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();
        String bitstreamContent = "TestBitstreamContent";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, item, is)
                    .withName("testfile.txt")
                    .withDescription("A test file")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/testfile.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        equalTo("attachment; filename=\"testfile.txt\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, equalTo("text/plain")))
                .andExpect(content().string(bitstreamContent));
    }

    @Test
    public void downloadBitstreamByHandleMultipleFiles() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();

        String content1 = "FileOneContent";
        String content2 = "FileTwoContent";
        try (InputStream is1 = IOUtils.toInputStream(content1, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, item, is1)
                    .withName("file1.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        try (InputStream is2 = IOUtils.toInputStream(content2, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, item, is2)
                    .withName("file2.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        // Download first file
        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/file1.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(content1));

        // Download second file
        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/file2.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(content2));
    }

    @Test
    public void downloadBitstreamByHandleForbiddenForNonAdmin() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();
        String bitstreamContent = "RestrictedContent";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, item, is)
                    .withName("restricted.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        // Remove all read policies from the bitstream
        authorizeService.removeAllPolicies(context, bitstream);
        // Add a read policy only for admin
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
                .withDspaceObject(bitstream)
                .withAction(Constants.READ)
                .build();
        context.restoreAuthSystemState();
        String handle = item.getHandle();
        String[] handleParts = handle.split("/");
        // Authenticated non-admin user should get 403
        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/restricted.txt"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    public void downloadBitstreamByHandleInvalidHandle() throws Exception {
        getClient().perform(get(ENDPOINT_BASE + "/99999/99999/nonexistent.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadBitstreamByHandleMissingFile() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();
        String bitstreamContent = "SomeContent";
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, item, is)
                    .withName("existing.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/nonexistent.txt"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadBitstreamByHandleSpecialCharInFilename() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();
        String bitstreamContent = "SpecialCharContent";
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, item, is)
                    .withName("my file (2).txt")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/my file (2).txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(bitstreamContent));
    }

    @Test
    public void downloadBitstreamByHandleUnauthorized() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();

        String bitstreamContent = "RestrictedContent";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, item, is)
                    .withName("restricted.txt")
                    .withMimeType("text/plain")
                    .build();
        }

        // Remove all read policies from the bitstream
        authorizeService.removeAllPolicies(context, bitstream);
        // Add a read policy only for admin
        ResourcePolicyBuilder.createResourcePolicy(context, admin, null)
                .withDspaceObject(bitstream)
                .withAction(Constants.READ)
                .build();

        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        // Anonymous user should get 401
        getClient().perform(get(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/restricted.txt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void headRequestBitstreamByHandle() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();
        Item item = ItemBuilder.createItem(context, col)
                .withAuthor("Test Author")
                .build();
        String bitstreamContent = "HeadRequestContent";
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, item, is)
                    .withName("headtest.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String handle = item.getHandle();
        String[] handleParts = handle.split("/");

        getClient().perform(head(ENDPOINT_BASE + "/" + handleParts[0] + "/" + handleParts[1] + "/headtest.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        equalTo("attachment; filename=\"headtest.txt\"")));
    }
}
