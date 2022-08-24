/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.ListUtils;
import org.dspace.app.rest.matcher.ExternalHandleMatcher;
import org.dspace.app.rest.matcher.MetadataFieldMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.handle.HandlePlugin;
import org.dspace.handle.external.ExternalHandleConstants;
import org.dspace.handle.external.Handle;
import org.dspace.handle.service.HandleClarinService;
import org.hamcrest.Matchers;
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

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

public class ExternalHandleRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    HandleClarinService handleClarinService;

    List<org.dspace.handle.Handle> handlesWithMagicURLs = new ArrayList<>();

    @Before
    public void setup() throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();

        List<String> magicURLs = this.createMagicURLs();

        // create Handles with magicURLs
        int index = 0;
        for (String magicURL : magicURLs) {
            // create Handle
            org.dspace.handle.Handle handle =
                    handleClarinService.createExternalHandle(context, "123/" + index, magicURL);
            // add created Handle to the list
            this.handlesWithMagicURLs.add(handle);
            index++;
        }

        context.commit();
        context.restoreAuthSystemState();
    }

    @After
    public void cleanup() throws Exception {
        context.turnOffAuthorisationSystem();

        handlesWithMagicURLs.forEach(handle -> {
            try {
                this.handleClarinService.delete(context, handle);
            } catch (SQLException | AuthorizeException e) {
                e.printStackTrace();
            }
        });

        context.commit();
        context.restoreAuthSystemState();

        handlesWithMagicURLs = null;
    }

    @Test
    public void findAllExternalHandles() throws Exception {
        // call endpoint which should return external handles
        List<Handle> expectedExternalHandles =
                this.handleClarinService.convertHandleWithMagicToExternalHandle(this.handlesWithMagicURLs);

        // expectedExternalHandles should not be empty
        Assert.assertFalse(ObjectUtils.isEmpty(expectedExternalHandles));
        Handle externalHandle = expectedExternalHandles.get(0);

        getClient().perform(get("/api/services/handles/magic"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", ExternalHandleMatcher.matchListOfExternalHandles(
                        expectedExternalHandles
                )))

//                .andExpect(jsonPath("$[*].url", containsInAnyOrder(expectedExternalHandles.get(0).url,
//                        expectedExternalHandles.get(1).url,
//                        expectedExternalHandles.get(2).url )))
//                .andExpect(jsonPath("$", contains(
//                        hasJsonPath("$.url", contains(expectedExternalHandles.get(0).url)),
//                        hasJsonPath("$.title", hasItems(expectedExternalHandles.get(0).title,
//                                expectedExternalHandles.get(1).title,
//                                expectedExternalHandles.get(2).title)),
//                        hasJsonPath("$.repository", hasItems(expectedExternalHandles.get(0).repository,
//                                expectedExternalHandles.get(1).repository,
//                                expectedExternalHandles.get(2).repository)),
//                        hasJsonPath("$.submitdate", hasItems(expectedExternalHandles.get(0).submitdate,
//                                expectedExternalHandles.get(1).submitdate,
//                                expectedExternalHandles.get(2).submitdate)),
//                        hasJsonPath("$.reportemail", hasItems(expectedExternalHandles.get(0).reportemail,
//                                expectedExternalHandles.get(1).reportemail,
//                                expectedExternalHandles.get(2).reportemail)),
//                        hasJsonPath("$.subprefix", hasItems(expectedExternalHandles.get(0).subprefix,
//                                expectedExternalHandles.get(1).subprefix,
//                                expectedExternalHandles.get(2).subprefix)),
//                        hasJsonPath("$.handle", hasItems(HandlePlugin.getCanonicalHandlePrefix() + expectedExternalHandles.get(0).handle,
//                                HandlePlugin.getCanonicalHandlePrefix() + expectedExternalHandles.get(1).handle,
//                                HandlePlugin.getCanonicalHandlePrefix() + expectedExternalHandles.get(2).handle))
//                )))
        ;


    }

    @Test
    public void shortenHandle() throws Exception {
        Assert.assertNotNull("ff");
    }

    @Test
    public void updateHandle() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String updatedMagicURL = "@magicLindat@The Penn Treebank 3, Switchboard data set, parsed, tagged, and dysfluency annotated | Query | PML Tree-Query Engine@magicLindat@DSpace at My University@magicLindat@2022-08-24T14:13:46Z@magicLindat@lindat-help@ufal.mff.cuni.cz@magicLindat@@magicLindat@@magicLindat@@magicLindat@0dd32992-6b88-4844-801b-36aeff3d23b4@magicLindat@https://lindat.mff.cuni.cz/#!/services/pmltq/#!/treebank/ptb3_swbd/query/HYe2BcFMCcFsEtgEMA2ACA2mgxk8aBeNAIgEEARANQAViAaNAMwFdhtx4wBnQkgFQCytBqAgwEydFlz4ixarQC6ioA?filter=true&timeout=30&limit=100";
        String handle = "123/0";
        Handle externalHandle = new Handle(handle, updatedMagicURL);

        context.turnOffAuthorisationSystem();
        getClient().perform(put("/api/services/handles")
                .content(mapper.writeValueAsBytes(externalHandle))
                .contentType(contentType))
                .andExpect(status().isOk())
        ;
        context.restoreAuthSystemState();
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
