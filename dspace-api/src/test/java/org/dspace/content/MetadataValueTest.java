/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.dspace.AbstractUnitTest;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.WorkspaceItemService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit Tests for class MetadataValue
 *
 * @author pvillega
 */
public class MetadataValueTest extends AbstractUnitTest {

    /**
     * log4j category
     */
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MetadataValueTest.class);

    /**
     * MetadataValue instance for the tests
     */
    private MetadataValue mv = null;

    private Collection collection;
    private Community owningCommunity;
    private Item it;


    /**
     * MetadataField instance for the tests
     */
    private MetadataField mf;

    /**
     * Element of the metadata element
     */
    private String element = "contributor";

    /**
     * Qualifier of the metadata element
     */
    private String qualifier = "author";

    private MetadataFieldService metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
    private MetadataValueService metadataValueService = ContentServiceFactory.getInstance().getMetadataValueService();
    private MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    protected CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    protected CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    protected WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    protected InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();

    /**
     * This method will be run before every test as per @Before. It will
     * initialize resources required for the tests.
     *
     * Other methods can be annotated with @Before here or in subclasses
     * but no execution order is guaranteed
     */
    @Before
    @Override
    public void init() {
        super.init();
        try {
            context.turnOffAuthorisationSystem();
            this.owningCommunity = communityService.create(null, context);
            this.collection = collectionService.create(context, owningCommunity);
            WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, false);
            this.it = installItemService.installItem(context, workspaceItem);

            this.mf = metadataFieldService.findByElement(context,
                                                         MetadataSchemaEnum.DC.getName(), element, qualifier);
            this.mv = metadataValueService.create(context, it, mf);
            context.restoreAuthSystemState();
        } catch (AuthorizeException ex) {
            log.error("Authorize Error in init", ex);
            fail("Authorize Error in init: " + ex.getMessage());
        } catch (SQLException ex) {
            log.error("SQL Error in init", ex);
            fail("SQL Error in init: " + ex.getMessage());
        }
    }

    /**
     * This method will be run after every test as per @After. It will
     * clean resources initialized by the @Before methods.
     *
     * Other methods can be annotated with @After here or in subclasses
     * but no execution order is guaranteed
     */
    @After
    @Override
    public void destroy() {
        try {
            context.turnOffAuthorisationSystem();
            communityService.delete(context, owningCommunity);
        } catch (SQLException | AuthorizeException | IOException ex) {
            log.error("Error in destroy", ex);
            fail("Error in destroy: " + ex.getMessage());
        } finally {
            context.restoreAuthSystemState();
        }

        mf = null;
        mv = null;
        super.destroy();
    }

    /**
     * Test of getFieldId method, of class MetadataValue.
     */
    @Test
    public void testGetFieldId() {
        MetadataValue instance = new MetadataValue();
        assertThat("testGetFieldId 0", instance.getID(), equalTo(0));

        assertThat("testGetFieldId 1", mv.getMetadataField().getID(), equalTo(mf.getID()));
    }

    /**
     * Test of getItemId method, of class MetadataValue.
     */
    @Test
    public void testGetDSpaceObject() {
        assertTrue("testGetItemId 0", mv.getDSpaceObject().equals(it));
    }

    /**
     * Test of getLanguage method, of class MetadataValue.
     */
    @Test
    public void testGetLanguage() {
        assertThat("testGetLanguage 0", mv.getLanguage(), nullValue());
    }

    /**
     * Test of setLanguage method, of class MetadataValue.
     */
    @Test
    public void testSetLanguage() {
        String language = "eng";
        mv.setLanguage(language);
        assertThat("testSetLanguage 0", mv.getLanguage(), equalTo(language));
    }

    /**
     * Test of getPlace method, of class MetadataValue.
     */
    @Test
    public void testGetPlace() {
        assertThat("testGetPlace 0", mv.getPlace(), equalTo(1));
    }

    /**
     * Test of setPlace method, of class MetadataValue.
     */
    @Test
    public void testSetPlace() {
        int place = 5;
        mv.setPlace(place);
        assertThat("testSetPlace 0", mv.getPlace(), equalTo(place));
    }

    /**
     * Test of getValueId method, of class MetadataValue.
     */
    @Test
    public void testGetValueId() {
        assertThat("testGetValueId 0", mv.getID(), notNullValue());
    }

    /**
     * Test of getValue method, of class MetadataValue.
     */
    @Test
    public void testGetValue() {
        assertThat("testGetValue 0", mv.getValue(), nullValue());
    }

    /**
     * Test of setValue method, of class MetadataValue.
     */
    @Test
    public void testSetValue() {
        String value = "value";
        mv.setValue(value);
        assertThat("testSetValue 0", mv.getValue(), equalTo(value));
    }

    /**
     * Test of getAuthority method, of class MetadataValue.
     */
    @Test
    public void testGetAuthority() {
        assertThat("testGetAuthority 0", mv.getAuthority(), nullValue());
    }

    /**
     * Test of setAuthority method, of class MetadataValue.
     */
    @Test
    public void testSetAuthority() {
        String value = "auth_val";
        mv.setAuthority(value);
        assertThat("testSetAuthority 0", mv.getAuthority(), equalTo(value));
    }

    /**
     * Test of getConfidence method, of class MetadataValue.
     */
    @Test
    public void testGetConfidence() {
        assertThat("testGetConfidence 0", mv.getConfidence(), equalTo(-1));
    }

    /**
     * Test of setConfidence method, of class MetadataValue.
     */
    @Test
    public void testSetConfidence() {
        int value = 5;
        mv.setConfidence(value);
        assertThat("testSetConfidence 0", mv.getConfidence(), equalTo(value));
    }

    /**
     * Test of create method, of class MetadataValue.
     */
    @Test
    public void testCreate() throws Exception {
        metadataValueService.create(context, it, mf);
    }

    /**
     * Test of find method, of class MetadataValue.
     */
    @Test
    public void testFind() throws Exception {
        metadataValueService.create(context, it, mf);
        int id = mv.getID();
        MetadataValue found = metadataValueService.find(context, id);
        assertThat("testFind 0", found, notNullValue());
        assertThat("testFind 1", found.getID(), equalTo(id));
    }

    /**
     * Test of findByField method, of class MetadataValue.
     */
    @Test
    public void testFindByField() throws Exception {
        metadataValueService.create(context, it, mf);
        List<MetadataValue> found = metadataValueService.findByField(context, mf);
        assertThat("testFind 0", found, notNullValue());
        assertTrue("testFind 1", found.size() >= 1);
    }

    /**
     * Test of update method, of class MetadataValue.
     */
    @Test
    public void testUpdate() throws Exception {
        metadataValueService.create(context, it, mf);
        metadataValueService.update(context, mv);
    }

    /**
     * Test of findByAuthorityAndLanguage method with basic functionality
     * verifying language filtering and deterministic ordering
     */
    @Test
    public void testFindByAuthorityAndLanguage() throws Exception {
        context.turnOffAuthorisationSystem();

        try {
            String testAuthority = "test-authority-dao-" + System.currentTimeMillis();

            // Create test metadata values using the same item as other tests
            MetadataValue testMv1 = metadataValueService.create(context, it, mf);
            testMv1.setAuthority(testAuthority);
            testMv1.setValue("Beta Value");
            testMv1.setLanguage("en");
            testMv1.setPlace(1);
            metadataValueService.update(context, testMv1);

            MetadataValue testMv2 = metadataValueService.create(context, it, mf);
            testMv2.setAuthority(testAuthority);
            testMv2.setValue("Alpha Value");
            testMv2.setLanguage("en");
            testMv2.setPlace(0);
            metadataValueService.update(context, testMv2);

            // Test: Find with authority and language
            List<MetadataValue> results =
                    metadataValueService.findByAuthorityAndLanguage(context, testAuthority, "en");
            assertThat("Should find 2 values", results.size(), equalTo(2));

            // Test deterministic ordering: m.place ASC, m.value ASC, m.id ASC
            // mv2 has place=0, mv1 has place=1, so mv2 should come first
            assertThat("First result should be Alpha Value (lower place)",
                    results.get(0).getValue(), equalTo("Alpha Value"));
            assertThat("Second result should be Beta Value",
                    results.get(1).getValue(), equalTo("Beta Value"));

            // Test: Find with authority but different language (should return empty)
            List<MetadataValue> noResults =
                    metadataValueService.findByAuthorityAndLanguage(context, testAuthority, "fr");
            assertThat("Should find no French values", noResults.size(), equalTo(0));

            // Test: Find with authority and null language (should include all languages)
            List<MetadataValue> allResults =
                    metadataValueService.findByAuthorityAndLanguage(context, testAuthority, null);
            assertThat("Should find both values with null language filter", allResults.size(), equalTo(2));

            // Test: Find with non-existent authority
            List<MetadataValue> notFoundResults =
                    metadataValueService.findByAuthorityAndLanguage(context, "non-existent", null);
            assertThat("Should find no values for non-existent authority",
                    notFoundResults.size(), equalTo(0));

        } finally {
            context.restoreAuthSystemState();
        }
    }
}
