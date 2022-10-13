package org.dspace.content;

import org.apache.commons.collections4.SetUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;

public class BundleClarinTest extends AbstractDSpaceObjectTest {
    /**
     * log4j category
     */
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(BundleTest.class);

    private static final String LICENSE_LABEL = "TEST";
    private static final String LICENSE_NAME = "TEST NAME";
    private static final String LICENSE_URI = "TEST URI";

    /**
     * Bundle instance for the tests
     */
    private Bundle b;
    private Item item;
    private Collection collection;
    private Community owningCommunity;
    private ClarinLicenseLabel clarinLicenseLabel;
    private ClarinLicense clarinLicense;

    private ClarinLicenseLabel secondClarinLicenseLabel;
    private ClarinLicense secondClarinLicense;

    private ClarinLicenseLabelService clarinLicenseLabelService = ClarinServiceFactory.getInstance()
            .getClarinLicenseLabelService();
    private ClarinLicenseService clarinLicenseService = ClarinServiceFactory.getInstance().getClarinLicenseService();
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService = ClarinServiceFactory
            .getInstance().getClarinLicenseResourceMappingService();

    /**
     * Spy of AuthorizeService to use for tests
     * (initialized / setup in @Before method)
     */
    private AuthorizeService authorizeServiceSpy;

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
            this.item = installItemService.installItem(context, workspaceItem);
            this.b = bundleService.create(context, item, Constants.CONTENT_BUNDLE_NAME);
            this.dspaceObject = b;

            // create clarin license label
            this.clarinLicenseLabel = clarinLicenseLabelService.create(context);
            this.clarinLicenseLabel.setLabel(LICENSE_LABEL);
            this.clarinLicenseLabel.setExtended(false);
            this.clarinLicenseLabel.setTitle("TEST TITLE");
            this.clarinLicenseLabel.setIcon(new byte[3]);
            this.clarinLicenseLabelService.update(context, this.clarinLicenseLabel);

            HashSet<ClarinLicenseLabel> cllSet = new HashSet<>();
            cllSet.add(this.clarinLicenseLabel);

            // create clarin license with clarin license labels
            this.clarinLicense = clarinLicenseService.create(context);
            this.clarinLicense.setLicenseLabels(cllSet);
            this.clarinLicense.setName(LICENSE_NAME);
            this.clarinLicense.setDefinition(LICENSE_URI);
            this.clarinLicense.setConfirmation(0);
            this.clarinLicenseService.update(context, this.clarinLicense);

            // initialize second clarin license and clarin license label
//            // create second clarin license label
//            this.secondClarinLicenseLabel = clarinLicenseLabelService.create(context);
//            this.secondClarinLicenseLabel.setLabel("wrong label");
//            this.secondClarinLicenseLabel.setExtended(false);
//            this.secondClarinLicenseLabel.setTitle("wrong title");
//            this.secondClarinLicenseLabel.setIcon(new byte[3]);
//            this.clarinLicenseLabelService.update(context, this.secondClarinLicenseLabel);
//
//            HashSet<ClarinLicenseLabel> secondCllSet = new HashSet<>();
//            secondCllSet.add(this.secondClarinLicenseLabel);
//
//            // create second clarin license with clarin license labels
//            this.secondClarinLicense = clarinLicenseService.create(context);
//            this.secondClarinLicense.setLicenseLabels(secondCllSet);
//            this.secondClarinLicense.setName("wrong name");
//            this.secondClarinLicense.setDefinition("wrong uri");
//            this.secondClarinLicense.setConfirmation(0);
//            this.clarinLicenseService.update(context, this.secondClarinLicense);

            //we need to commit the changes, so we don't block the table for testing
            context.restoreAuthSystemState();

            // Initialize our spy of the autowired (global) authorizeService bean.
            // This allows us to customize the bean's method return values in tests below
            authorizeServiceSpy = spy(authorizeService);
            // "Wire" our spy to be used by the current loaded itemService, bundleService & bitstreamService
            // (To ensure it uses the spy instead of the real service)
            ReflectionTestUtils.setField(itemService, "authorizeService", authorizeServiceSpy);
            ReflectionTestUtils.setField(bundleService, "authorizeService", authorizeServiceSpy);
            ReflectionTestUtils.setField(bitstreamService, "authorizeService", authorizeServiceSpy);
        } catch (SQLException | AuthorizeException ex) {
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
        b = null;
        item = null;
        collection = null;
        owningCommunity = null;
        clarinLicense = null;
        clarinLicenseLabel = null;
        super.destroy();
    }

    @Test
    public void testAttachLicenseToBitstream() throws IOException, SQLException, AuthorizeException {
        // the license is not attached to the bitstream
        assertEquals(clarinLicense.getNonDeletedBitstreams(), 0);

        context.turnOffAuthorisationSystem();
        // add clarin license data to the item metadata
        itemService.addMetadata(context, item, "dc", "rights", "uri", Item.ANY,
                clarinLicense.getDefinition());
        itemService.addMetadata(context, item, "dc", "rights", null, Item.ANY,
                clarinLicense.getName());
        itemService.addMetadata(context, item, "dc", "rights", "label", Item.ANY,
                clarinLicense.getNonExtendedClarinLicenseLabel().getLabel());

        // run addBitstream method
        File f = new File(testProps.get("test.bitstream").toString());
        Bitstream bs = bitstreamService.create(context, new FileInputStream(f));
        bundleService.addBitstream(context, b, bs);

        List<ClarinLicenseResourceMapping> bitstreamAddedToLicense = clarinLicenseResourceMappingService
                .findAllByLicenseId(context, clarinLicense.getID());

        // the license is attached to the bitstream
        assertNotNull(bitstreamAddedToLicense);
        assertEquals(bitstreamAddedToLicense.size(), 1);
    }

    // clear and add metadata to the item
    @Test
    public void testAddLicenseMetadataToItem() throws SQLException, AuthorizeException, IOException {
        context.turnOffAuthorisationSystem();
        // add clarin license data to the item metadata
        itemService.addMetadata(context, item, "dc", "rights", "uri", Item.ANY,
                clarinLicense.getDefinition());
        itemService.addMetadata(context, item, "dc", "rights", null, Item.ANY,
                clarinLicense.getName());
        itemService.addMetadata(context, item, "dc", "rights", "label", Item.ANY,
                clarinLicense.getNonExtendedClarinLicenseLabel().getLabel());

        // run addBitstream method
        File f = new File(testProps.get("test.bitstream").toString());
        Bitstream bs = bitstreamService.create(context, new FileInputStream(f));
        bundleService.addBitstream(context, b, bs);

        // the license is attached to the bitstream
        List<MetadataValue> licenseName =
                this.itemService.getMetadata(item, "dc", "rights", null, Item.ANY, false);
        assertNotNull(licenseName);
        assertEquals(licenseName.size(), 1);
        assertEquals(licenseName.get(0).getValue(), clarinLicense.getName());
    }

    /**
     * Test of getType method, of class Bundle.
     */
    @Override
    @Test
    public void testGetType() {
        assertThat("testGetType 0", b.getType(), equalTo(Constants.BUNDLE));
    }

    /**
     * Test of getID method, of class Bundle.
     */
    @Override
    @Test
    public void testGetID() {
        assertTrue("testGetID 0", b.getID() != null);
    }

    /**
     * Test of getHandle method, of class Bundle.
     */
    @Override
    @Test
    public void testGetHandle() {
        //no handle for bundles
        assertThat("testGetHandle 0", b.getHandle(), nullValue());
    }

    /**
     * Test of getName method, of class Bundle.
     */
    @Override
    @Test
    public void testGetName() {
        //created bundle has no name
        assertThat("testGetName 0", b.getName(), equalTo("TESTBUNDLE"));
    }

    /**
     * Test of getAdminObject method, of class Bundle.
     */
    @Test
    @Override
    public void testGetAdminObject() throws SQLException {
        //default bundle has no admin object
        assertThat("testGetAdminObject 0", bundleService.getAdminObject(context, b, Constants.REMOVE),
                instanceOf(Item.class));
        assertThat("testGetAdminObject 1", bundleService.getAdminObject(context, b, Constants.ADD),
                instanceOf(Item.class));
    }

    /**
     * Test of getParentObject method, of class Bundle.
     */
    @Test
    @Override
    public void testGetParentObject() throws SQLException {
        //default bundle has no parent
        assertThat("testGetParentObject 0", bundleService.getParentObject(context, b), notNullValue());
        assertThat("testGetParentObject 0", bundleService.getParentObject(context, b), instanceOf(Item.class));
    }
}
