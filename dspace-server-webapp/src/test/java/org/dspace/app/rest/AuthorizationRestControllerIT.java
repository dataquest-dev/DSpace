/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.repository.ClarinLicenseRestRepository.OPERATION_PATH_LICENSE_RESOURCE;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.tools.ant.taskdefs.condition.Http;
import org.checkerframework.checker.units.qual.A;
import org.dspace.app.rest.exception.DownloadTokenExpiredException;
import org.dspace.app.rest.matcher.ClarinLicenseMatcher;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.builder.ClarinLicenseResourceUserAllowanceBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.eperson.EPerson;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orcid.jaxb.model.record_v2.Works;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthorizationRestControllerIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinLicenseService clarinLicenseService;
    @Autowired
    ClarinLicenseLabelService clarinLicenseLabelService;
    @Autowired
    ClarinLicenseResourceUserAllowanceService clarinLicenseResourceUserAllowanceService;
    @Autowired
    ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;

    Item item;
    WorkspaceItem witem;

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

        witem = WorkspaceItemBuilder.createWorkspaceItem(context, col1)
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
        context.turnOffAuthorisationSystem();
        String token = "amazingToken";

        // Create WorkspaceItem with Bitstream and Clarin License
        List<Operation> replaceOperations = new ArrayList<Operation>();
        String clarinLicenseName = "Test Clarin License";

        // Create clarin license with clarin license label
        ClarinLicense clarinLicense = createClarinLicense(clarinLicenseName, "Test Def", "Test R Info", 0);

        // Creating replace operation
        Map<String, String> licenseReplaceOpValue = new HashMap<String, String>();
        licenseReplaceOpValue.put("value", clarinLicenseName);
        replaceOperations.add(new ReplaceOperation("/" + OPERATION_PATH_LICENSE_RESOURCE,
                licenseReplaceOpValue));

        context.restoreAuthSystemState();
        String updateBody = getPatchContent(replaceOperations);

        // 3. Send request to add Clarin License to the Workspace Item
        String tokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(tokenAdmin).perform(patch("/api/submission/workspaceitems/" + witem.getID())
                        .content(updateBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk());

        // 4. Check if the Clarin License name was added to the Item's metadata `dc.rights`
        getClient(tokenAdmin).perform(get("/api/submission/workspaceitems/" + witem.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.item.metadata['dc.rights'][0].value", is(clarinLicenseName)));

        // 5. Check if the Clarin License was attached to the Bitstream
        getClient(tokenAdmin).perform(get("/api/core/clarinlicenses/" + clarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(1)));

        // Get clarin license resource mapping which will be added to the LicenseResourceUserAllowance
        List<ClarinLicenseResourceMapping> clarinLicenseResourceMappings =
                clarinLicenseResourceMappingService.findAllByLicenseId(context, clarinLicense.getID());

        context.turnOffAuthorisationSystem();
        // Create the ClarinLicenseResourceUserAllowance with token then the download with token should work
        ClarinLicenseResourceUserAllowance clarinLicenseResourceUserAllowance =
                ClarinLicenseResourceUserAllowanceBuilder.createClarinLicenseResourceUserAllowance(context)
                        .withToken(token)
                        .withCreatedOn(new Date())
                        .withMapping(clarinLicenseResourceMappings.get(0))
                        .build();
        context.restoreAuthSystemState();

        Bitstream bitstream = witem.getItem().getBundles().get(0).getBitstreams().get(0);
        // Admin is not the submitter.
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        // Load bitstream from the item.
        getClient(authTokenAdmin).perform(get("/api/authrn/" +
                        bitstream.getID().toString() + "?dtoken=" + token))
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

    /**
     * Create Clarin License Label object for testing purposes.
     */
    private ClarinLicenseLabel createClarinLicenseLabel(String label, boolean extended, String title)
            throws SQLException, AuthorizeException {
        ClarinLicenseLabel clarinLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        clarinLicenseLabel.setLabel(label);
        clarinLicenseLabel.setExtended(extended);
        clarinLicenseLabel.setTitle(title);

        clarinLicenseLabelService.update(context, clarinLicenseLabel);
        return clarinLicenseLabel;
    }

    /**
     * Create ClarinLicense object with ClarinLicenseLabel object for testing purposes.
     */
    private ClarinLicense createClarinLicense(String name, String definition, String requiredInfo, int confirmation)
            throws SQLException, AuthorizeException {
        ClarinLicense clarinLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        clarinLicense.setConfirmation(confirmation);
        clarinLicense.setDefinition(definition);
        clarinLicense.setRequiredInfo(requiredInfo);
        clarinLicense.setName(name);

        // add ClarinLicenseLabels to the ClarinLicense
        HashSet<ClarinLicenseLabel> clarinLicenseLabels = new HashSet<>();
        ClarinLicenseLabel clarinLicenseLabel = createClarinLicenseLabel("lbl", false, "Test Title");
        clarinLicenseLabels.add(clarinLicenseLabel);
        clarinLicense.setLicenseLabels(clarinLicenseLabels);

        clarinLicenseService.update(context, clarinLicense);
        return clarinLicense;
    }


}
