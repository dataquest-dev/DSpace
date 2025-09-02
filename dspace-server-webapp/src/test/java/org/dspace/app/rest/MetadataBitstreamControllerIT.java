/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class MetadataBitstreamControllerIT extends AbstractControllerIntegrationTest {
    private static final String AUTHOR = "Test author name";

    private Item publicItem;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        Collection col = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection").build();

        publicItem = ItemBuilder.createItem(context, col)
                .withAuthor(AUTHOR)
                .build();

        String bitstreamContent = "ThisIsSomeDummyText";
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, publicItem, is)
                    .withName("Bitstream")
                    .withDescription("Description")
                    .withMimeType("application/zip")
                    .build();
        }
        context.restoreAuthSystemState();
    }

    /**
     * Test downloading multiple bitstreams separately by name using the new endpoint.
     * This test verifies that each bitstream can be downloaded individually without
     * creating a ZIP archive, allowing multiple files to be downloaded as separate files.
     */
    @Test
    public void downloadMultipleBitstreamsSeparatelyTest() throws Exception {
        context.turnOffAuthorisationSystem();
        
        // Create additional bitstreams for testing multiple downloads
        String content = "Document content for testing individual downloads";
        String name = "document1.txt";
        String mimeType = "text/plain";
        try (InputStream is = IOUtils.toInputStream(content, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, publicItem, is)
                    .withName(name)
                    .withDescription("First test document")
                    .withMimeType(mimeType)
                    .build();
        }
        
        context.restoreAuthSystemState();
        
        // Generate auth token for admin user
        String token = getAuthToken(admin.getEmail(), password);

        // Download bitstream by name using the new endpoint
        MvcResult mvcResult = getClient(token)
                .perform(get("/api/core/bitstreams/handle/" + publicItem.getHandle() + "/" + name))
                        .andExpect(status().isOk())
                        .andReturn();
        // Verify the downloaded content matches the expected content
        String downloadedContent = mvcResult.getResponse().getContentAsString();
        assertEquals("Downloaded content should match expected content for " + name,
                content, downloadedContent);
        // Verify correct content type
        String contentType = mvcResult.getResponse().getContentType();
        assertEquals("Content type should match expected MIME type for " + name,
                mimeType, contentType);
        // Verify Content-Disposition header for proper file download
        String contentDisposition = mvcResult.getResponse().getHeader("Content-Disposition");
        assertNotNull("Content-Disposition header should be present for " + name,
                contentDisposition);
        assertTrue("Content-Disposition should be attachment for " + name,
                contentDisposition.startsWith("attachment"));
        assertTrue("Filename should be in Content-Disposition header for " + name,
                contentDisposition.contains("filename=\"" + name + "\""));
        
        // Test error cases
        // Test downloading non-existent bitstream should return 422
        getClient(token)
                .perform(get("/api/core/bitstreams/handle/" + publicItem.getHandle() + "/nonexistent.txt"))
                .andExpect(status().isUnprocessableEntity());
        
        // Test with invalid handle should return 422
        getClient(token)
                .perform(get("/api/core/bitstreams/handle/invalid-handle/document1.txt"))
                .andExpect(status().isUnprocessableEntity());
        
        // Test unauthorized access (without token) should return 401
        getClient()
                .perform(get("/api/core/bitstreams/handle/" + publicItem.getHandle() + "/document1.txt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test downloading bitstream with special characters in filename.
     * This ensures the endpoint handles filenames with spaces, special characters correctly.
     */
    @Test
    public void downloadBitstreamWithSpecialCharactersTest() throws Exception {
        context.turnOffAuthorisationSystem();
        
        String specialContent = "Content of file with special characters in name";
        String specialFileName = "test file with spaces & special chars (2024).pdf";
        
        try (InputStream is = IOUtils.toInputStream(specialContent, CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, publicItem, is)
                    .withName(specialFileName)
                    .withDescription("File with special characters in name")
                    .withMimeType("application/pdf")
                    .build();
        }
        
        context.restoreAuthSystemState();
        
        String token = getAuthToken(admin.getEmail(), password);
        
        // Test downloading bitstream with special characters in name
        MvcResult mvcResult = getClient(token)
                .perform(get("/api/core/bitstreams/handle/" + publicItem.getHandle() + "/" + specialFileName))
                .andExpect(status().isOk())
                .andReturn();
        
        // Verify content
        String downloadedContent = mvcResult.getResponse().getContentAsString();
        assertEquals("Downloaded content should match for file with special characters", 
                    specialContent, downloadedContent);
        
        // Verify headers
        String contentDisposition = mvcResult.getResponse().getHeader("Content-Disposition");
        assertNotNull("Content-Disposition header should be present", contentDisposition);
        assertTrue("Content-Disposition should contain the special filename",
                  contentDisposition.contains("filename=\"" + specialFileName + "\""));
        
        String responseContentType = mvcResult.getResponse().getContentType();
        assertEquals("Content type should be PDF", "application/pdf", responseContentType);
    }
}
