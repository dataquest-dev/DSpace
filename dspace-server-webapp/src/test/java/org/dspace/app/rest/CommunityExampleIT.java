/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.model.CommunityRest;
import org.dspace.app.rest.model.MetadataRest;
import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.CommunityBuilder;
import org.dspace.core.Constants;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration Tests for class Community
 *
 * @author milanmajchrak
 */
public class CommunityExampleIT extends AbstractControllerIntegrationTest {

    @Autowired
    AuthorizeService authorizeService;

    private ObjectMapper mapper;
    private CommunityRest comm;
    private String authToken;

    /**
     * This method will be run before every test as per @Before. It will
     * initialize resources required for the tests.
     *
     * Other methods can be annotated with @Before here or in subclasses
     * but no execution order is guaranteed
     */
    @Before
    public void createStructure() throws Exception {
        //We turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        // Create a parent community
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        // ADD authorization on parent community
        context.setCurrentUser(eperson);
        authorizeService.addPolicy(context, parentCommunity, Constants.ADD, eperson);
        context.restoreAuthSystemState();

        this.authToken = getAuthToken(eperson.getEmail(), password);

        this.mapper = new ObjectMapper();
        this.comm = new CommunityRest();
        // We send a name but the created community should set this to the title
        this.comm.setName("Test Parent Community");
        this.comm.setMetadata(new MetadataRest()
                .put("dc.description",
                        new MetadataValueRest("<p>Some cool HTML code here</p>"))
                .put("dc.description.abstract",
                        new MetadataValueRest("Sample top-level community created via the REST API"))
                .put("dc.description.tableofcontents",
                        new MetadataValueRest("<p>HTML News</p>"))
                .put("dc.rights",
                        new MetadataValueRest("Custom Copyright Text"))
                .put("dc.title",
                        new MetadataValueRest("Title Text")));
    }

    /**
     * Test of find the created community.
     */
    @Test
    public void shouldCreateCommunity() throws Exception {
        AtomicReference<UUID> idRef = new AtomicReference<>();
        try {
            getClient(authToken).perform(post("/api/core/communities")
                            .content(mapper.writeValueAsBytes(comm))
                            .param("parent", parentCommunity.getID().toString())
                            .contentType(contentType))
                    .andExpect(status().isCreated());
        } finally {
            // Delete the created community (cleanup after ourselves!)
            CommunityBuilder.deleteCommunity(idRef.get());
        }
    }
}
