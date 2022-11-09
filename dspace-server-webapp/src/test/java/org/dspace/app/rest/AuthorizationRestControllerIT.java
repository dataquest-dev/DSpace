/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.dspace.app.rest.exception.DownloadTokenExpiredException;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;

public class AuthorizationRestControllerIT extends AbstractControllerIntegrationTest {
    Item item;
    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Community child1 = CommunityBuilder.createSubCommunity(context, parentCommunity)
                .withName("Sub Community")
                .build();
        Collection col1 = CollectionBuilder.createCollection(context, child1)
                .withName("Collection 1")
                .build();

        context.setCurrentUser(eperson);
        InputStream pdf = getClass().getResourceAsStream("simple-article.pdf");

        WorkspaceItem witem = WorkspaceItemBuilder.createWorkspaceItem(context, col1)
                .withTitle("Test WorkspaceItem")
                .withIssueDate("2017-10-17")
                .withFulltext("simple-article.pdf", "/local/path/simple-article.pdf", pdf)
                .build();

        item = witem.getItem();
        context.restoreAuthSystemState();
    }

    // Submitter should be authorized to download th bitstream, 200
    @Test
    public void shouldAuthorizeUserAsSubmitter() throws Exception {
        String authTokenAdmin = getAuthToken(eperson.getEmail(), password);

        // Load bitstream from the item.
        Bitstream bitstream = item.getBundles().get(0).getBitstreams().get(0);
        getClient(authTokenAdmin).perform(get("/api/authrn/" + bitstream.getID().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.errorName", Matchers.is("")))
                .andExpect(jsonPath("$.responseStatusCode", Matchers.is(HttpStatus.OK.value())));;
    }

    // DownloadTokenExpiredException, 401
    @Test
    public void shouldNotAuthorizeUserByWrongToken() throws Exception {
        // Admin is not the submitter.
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);

        // Load bitstream from the item.
        Bitstream bitstream = item.getBundles().get(0).getBitstreams().get(0);
        getClient(authTokenAdmin).perform(get("/api/authrn/" +
                        bitstream.getID().toString() + "?dtoken=wrongToken"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.errorName", Matchers.is(DownloadTokenExpiredException.NAME)))
                .andExpect(jsonPath("$.responseStatusCode", Matchers.is(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    public void shouldAuthorizeUserByCorrectToken() throws Exception {
        // Admin is not the submitter.
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);

        // Load bitstream from the item.
        Bitstream bitstream = item.getBundles().get(0).getBitstreams().get(0);
        getClient(authTokenAdmin).perform(get("/api/authrn/" +
                        bitstream.getID().toString() + "?dtoken=wrongToken"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.errorName", Matchers.is("")))
                .andExpect(jsonPath("$.responseStatusCode", Matchers.is(HttpStatus.OK.value())));
    }

    // 400
    @Test
    public void shouldReturnBadRequestException() throws Exception {
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(authTokenAdmin).perform(get("/api/authrn"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(contentType));
    }

    // 404
    @Test
    public void shouldReturnNotFoundException() throws Exception {
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(authTokenAdmin).perform(get("/api/authrn/wrongID"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(contentType));
    }


}
