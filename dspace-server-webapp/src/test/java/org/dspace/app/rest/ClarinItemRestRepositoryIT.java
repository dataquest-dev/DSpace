package org.dspace.app.rest;

import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.matcher.HalMatcher;
import org.dspace.app.rest.matcher.ItemMatcher;
import org.dspace.app.rest.model.patch.Operation;
import org.dspace.app.rest.model.patch.ReplaceOperation;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinItemRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;

    @Test
    public void findItemWithUnknownIssuedDate() throws Exception {
        context.turnOffAuthorisationSystem();

        //** GIVEN **
        //1. A community-collection structure with one parent community and one collection
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        //2. Three public items that are readable by Anonymous with different subjects
        Item publicItem = ItemBuilder.createItem(context, col)
                .withTitle("Public item")
                .withIssueDate("2021-04-27")
                .withMetadata("local", "approximateDate", "issued", "unknown")
                .withAuthor("Smith, Donald").withAuthor("Doe, John")
                .withSubject("ExtraEntry")
                .build();

        context.restoreAuthSystemState();
        Matcher<? super Object> publicItemMatcher = ItemMatcher.matchItemWithTitleAndApproximateDateIssued(publicItem,
                "Public item", "unknown");

        getClient().perform(get("/api/core/items/" + publicItem.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", HalMatcher.matchNoEmbeds()))
                .andExpect(jsonPath("$", publicItemMatcher));
    }

    @Test
    public void attachLicenseToItem() throws Exception {
        // 1. Create Item and ClarinLicense with ClarinLicenseLabels
        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        // 3. Check the new Item do not have attached ClarinLicense by default.
        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        context.turnOffAuthorisationSystem();
        // 1. Create Item and ClarinLicense with ClarinLicenseLabels
        Item item = createItemWithFile();

        // 2. Create Clarin License
        String clarinLicenseName = "Test Clarin License";
        ClarinLicense clarinLicense = createClarinLicense(clarinLicenseName, "Test Def", "Test R Info", 0);
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);

        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/license/attach", clarinLicenseName);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        // 3. Check the new Item do not have attached ClarinLicense by default.
        getClient(token).perform(get("/api/core/items/" + item.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value").doesNotExist());

        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value", is(clarinLicenseName)))
                .andExpect(jsonPath("$.metadata['dc.rights.uri'][0].value",
                        is(clarinLicense.getDefinition())));

        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        getClient(token).perform(get("/api/core/clarinlicenses/" + clarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(2)));
    }

    @Test
    public void detachLicenseFromItem() throws Exception {
        // 1. Create Item and ClarinLicense with ClarinLicenseLabels
        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        // 3. Check the new Item do not have attached ClarinLicense by default.
        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        // 6. Create a new ReplaceOperation with body to detach the ClarinLicense from the Item, and it's bitstreams
        // 7. Send the new Patch Request and check the ClarinLicense was detached from the Item's metadata
        // 8. Check the ClarinLicense was detached from the Item's bitstreams
        context.turnOffAuthorisationSystem();
        // 1. Create Item and ClarinLicense with ClarinLicenseLabels
        Item item = createItemWithFile();

        // 2. Create Clarin License
        String clarinLicenseName = "Test Clarin License";
        ClarinLicense clarinLicense = createClarinLicense(clarinLicenseName, "Test Def", "Test R Info", 0);
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);

        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/license/attach", clarinLicenseName);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        // 3. Check the new Item do not have attached ClarinLicense by default.
        getClient(token).perform(get("/api/core/items/" + item.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value").doesNotExist());

        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value", is(clarinLicenseName)))
                .andExpect(jsonPath("$.metadata['dc.rights.uri'][0].value",
                        is(clarinLicense.getDefinition())));

        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        getClient(token).perform(get("/api/core/clarinlicenses/" + clarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(2)));

        // 6. Create a new ReplaceOperation with body to detach the ClarinLicense from the Item, and it's bitstreams
        ReplaceOperation detachLicenseOp = new ReplaceOperation("/license/detach", clarinLicenseName);
        ops.clear();
        ops.add(detachLicenseOp);
        String detachLicensePatchBody = getPatchContent(ops);

        // 7. Send the new Patch Request and check the ClarinLicense was detached from the Item's metadata
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(detachLicensePatchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value").doesNotExist())
                .andExpect(jsonPath("$.metadata['dc.rights.uri'][0].value").doesNotExist());

        // 8. Check the ClarinLicense was detached from the Item's bitstreams
        getClient(token).perform(get("/api/core/clarinlicenses/" + clarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(0)));
    }

    @Test
    public void updateLicenseInItem() throws Exception {
        // 1. Create Item and two ClarinLicenses with ClarinLicenseLabels
        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        // 3. Check the new Item do not have attached ClarinLicense by default.
        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        // 6. Create a new ReplaceOperation with body to attach another ClarinLicense to the Item, and it's bitstreams
        // 7. Send the new Patch Request and check the ClarinLicense was updated in the Item's metadata
        // 8. Check the ClarinLicense was updated in the Item's bitstreams
        context.turnOffAuthorisationSystem();
        // 1. Create Item and two ClarinLicenses with ClarinLicenseLabels
        Item item = createItemWithFile();
        String clarinLicenseName = "Test Clarin License";
        String updatedClarinLicenseName = "Updated Clarin License";

        ClarinLicense clarinLicense = createClarinLicense(clarinLicenseName, "Test Def", "Test R Info", 0);
        ClarinLicense updatedClarinLicense =
                createClarinLicense(updatedClarinLicenseName, "Test Def2", "Test R Info2", 0);
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);

        // 2. Create ReplaceOperation with body to attach license to the item, and it's bitstreams.
        List<Operation> ops = new ArrayList<Operation>();
        ReplaceOperation replaceOperation = new ReplaceOperation("/license/attach", clarinLicenseName);
        ops.add(replaceOperation);
        String patchBody = getPatchContent(ops);

        // 3. Check the new Item do not have attached ClarinLicense by default.
        getClient(token).perform(get("/api/core/items/" + item.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value").doesNotExist());

        // 4. Send the Patch Request and check the ClarinLicense was attached to the Item's metadata
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(patchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value", is(clarinLicenseName)))
                .andExpect(jsonPath("$.metadata['dc.rights.uri'][0].value",
                        is(clarinLicense.getDefinition())));

        // 5. Check the ClarinLicense was attached to the Item's bitstreams
        getClient(token).perform(get("/api/core/clarinlicenses/" + clarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(2)));

        // 6. Create a new ReplaceOperation with body to detach the ClarinLicense from the Item, and it's bitstreams
        ReplaceOperation updateLicenseOp = new ReplaceOperation("/license/attach", updatedClarinLicenseName);
        ops.clear();
        ops.add(updateLicenseOp);
        String updateLicensePatchBody = getPatchContent(ops);

        // 7. Send the new Patch Request and check the ClarinLicense was detached from the Item's metadata
        getClient(token).perform(patch("/api/core/items/" + item.getID())
                        .content(updateLicensePatchBody)
                        .contentType(MediaType.APPLICATION_JSON_PATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", Matchers.is(item.getID().toString())))
                .andExpect(jsonPath("$.metadata['dc.rights'][0].value", is(updatedClarinLicenseName)))
                .andExpect(jsonPath("$.metadata['dc.rights.uri'][0].value",
                        is(updatedClarinLicense.getDefinition())));

        // 8. Check the ClarinLicense was detached from the Item's bitstreams
        getClient(token).perform(get("/api/core/clarinlicenses/" + updatedClarinLicense.getID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bitstreams", is(2)));
    }

    private Item createItemWithFile() throws SQLException, AuthorizeException, IOException {
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        Collection col1 = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection 1").build();

        Item item = ItemBuilder.createItem(context, col1)
                .withTitle("Test item -- thumbnail")
                .withIssueDate("2017-10-17")
                .withAuthor("Smith, Donald").withAuthor("Doe, John")
                .build();

        Bundle originalBundle = BundleBuilder.createBundle(context, item)
                .withName(Constants.DEFAULT_BUNDLE_NAME)
                .build();

        InputStream is = IOUtils.toInputStream("dummy", "utf-8");

        // With two ORIGINAL Bitstreams with matching THUMBNAIL Bitstreams
        BitstreamBuilder.createBitstream(context, originalBundle, is)
                .withName("test1.pdf")
                .withMimeType("application/pdf")
                .build();
        BitstreamBuilder.createBitstream(context, originalBundle, is)
                .withName("test2.pdf")
                .withMimeType("application/pdf")
                .build();

        return item;
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
