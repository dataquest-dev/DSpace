package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.test.AbstractEntityIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.ClarinBitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.apache.commons.codec.CharEncoding.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.springframework.data.rest.webmvc.RestMediaTypes.TEXT_URI_LIST_VALUE;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinLogoImportControllerIT extends AbstractEntityIntegrationTest {
    private Bitstream bitstream;

    @Autowired
    private CommunityService communityService;
    @Autowired
    private CollectionService collectionService;

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();
        bitstream = ClarinBitstreamBuilder.createBitstream(context, toInputStream("test", UTF_8)).build();
        context.restoreAuthSystemState();
    }

    @Test
    public void addCommunityLogoTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        context.restoreAuthSystemState();

        String authToken = getAuthToken(admin.getEmail(), password);
        getClient(authToken).perform(
                post("/api/clarin/import/logo/community")
                        .contentType(contentType)
                        .param("bitstream_id", bitstream.getID().toString())
                        .param("community_id", community.getID().toString()))
        .andExpect(status().isOk());

        community = communityService.find(context, community.getID());
        assertEquals(community.getLogo().getID(), bitstream.getID());
    }

    @Test
    public void addCollectionLogoTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, community).build();
        context.restoreAuthSystemState();

        String authToken = getAuthToken(admin.getEmail(), password);
        getClient(authToken).perform(
                        post("/api/clarin/import/logo/collection")
                                .contentType(contentType)
                                .param("bitstream_id", bitstream.getID().toString())
                                .param("collection_id", collection.getID().toString()))
                .andExpect(status().isOk());

        collection = collectionService.find(context, collection.getID());
        assertEquals(collection.getLogo().getID(), bitstream.getID());
    }
}
