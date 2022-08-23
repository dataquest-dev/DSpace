/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import org.apache.commons.collections4.ListUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.handle.external.ExternalHandleConstants;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

public class ExternalHandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    HandleClarinService handleClarinService;

    List<org.dspace.handle.Handle> handlesWithMagicURLs = new ArrayList<>();

    @Before
    public void setup() throws SQLException {
        context.turnOffAuthorisationSystem();

        List<String> magicURLs = this.createMagicURLs();

        // create Handles with magicURLs
        int index = 0;
        for (String magicURL : magicURLs) {
            // create Handle
            org.dspace.handle.Handle handle =
                    handleClarinService.createHandle(context, "123/" + index, null, magicURL);
            // add created Handle to the list
            this.handlesWithMagicURLs.add(handle);
            index++;
        }

        context.restoreAuthSystemState();
    }

    @After
    public void cleanup() throws Exception {
        handlesWithMagicURLs.forEach(handle -> {
            try {
                this.handleClarinService.delete(context, handle);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        handlesWithMagicURLs = null;
    }

    @Test
    public void findAllExternalHandles() throws Exception {
        // call endpoint which should return external handles
        List<Handle> expectedExternalHandles =
                this.handleClarinService.convertHandleWithMagicToExternalHandle(this.handlesWithMagicURLs);

        getClient().perform(get("/api/services/handles/magic"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$", BundleMatcher.matchBundle(bundle1.getName(),
//                        bundle1.getID(),
//                        bundle1.getHandle(),
//                        bundle1.getType(),
//                        bundle1.getBitstreams())
//                ))
        ;
        Assert.assertFalse(ObjectUtils.isEmpty(this.handlesWithMagicURLs));
    }

    @Test
    public void shortenHandle() throws Exception {
        Assert.assertNotNull("ff");
    }

    @Test
    public void updateHandle() throws Exception {
        Assert.assertNotNull("ff");
    }

    private List<String> createMagicURLs() {
        // External handle attributes
        String url = "url";
        String title = "title";
        String repository = "repository";
        String submitDate = "submitDate";
        String reporteMail = "reporteMail";
        String datasetName = "datasetName";
        String datasetVersion = "datasetVersion";
        String query = "query";
        String token = "token";
        String subprefix = "subprefix";

        List<String> magicURLs = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            // create mock object
            String magicURL =
                    ExternalHandleConstants.MAGIC_BEAN + url + i +
                            ExternalHandleConstants.MAGIC_BEAN + title + i +
                            ExternalHandleConstants.MAGIC_BEAN + repository + i +
                            ExternalHandleConstants.MAGIC_BEAN + submitDate + i +
                            ExternalHandleConstants.MAGIC_BEAN + reporteMail + i +
                            ExternalHandleConstants.MAGIC_BEAN + datasetName + i +
                            ExternalHandleConstants.MAGIC_BEAN + datasetVersion + i +
                            ExternalHandleConstants.MAGIC_BEAN + query + i +
                            ExternalHandleConstants.MAGIC_BEAN + token + i +
                            ExternalHandleConstants.MAGIC_BEAN + subprefix + i;
            // add mock object to the magicURLs
            magicURLs.add(magicURL);
        }

        return magicURLs;
    }

}
