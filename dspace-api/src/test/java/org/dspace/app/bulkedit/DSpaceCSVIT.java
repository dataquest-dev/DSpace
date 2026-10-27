/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkedit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for hidden-metadata handling in DSpaceCSV export.
 *
 * @author Stuart Lewis
 */
public class DSpaceCSVIT extends AbstractIntegrationTestWithDatabase {

    ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    Item testItem;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        configurationService.addPropertyValue("metadata.hide.dc.subject", true);

        Collection parentCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Parent Collection")
                .build();

        testItem = ItemBuilder.createItem(context, parentCollection).withTitle("Test Item")
                .withMetadata("dc", "description", "provenance", "provenance")
                .withMetadata("dc", "subject", null, "hidden subject")
                .build();

        context.restoreAuthSystemState();
    }

    /**
     * Test the hidden metadata for csv is respected
     *
     */
    @Test
    public void testHiddenDspaceCSV() throws Exception {

        DSpaceCSV dSpaceCSV = new DSpaceCSV(false);

        dSpaceCSV.addItem(testItem);

        List<DSpaceCSVLine> lines = dSpaceCSV.getCSVLines();

        assertThat(lines.size(), equalTo(1));

        DSpaceCSVLine line = lines.get(0);

        List<String> subject = line.get("dc.subject");
        List<String> provenance = line.get("dc.description.provenance");
        List<String> title = line.get("dc.title");

        assertNull(subject);
        assertNull(provenance);
        assertNotNull(title);
        assertEquals("Test Item", title.get(0));

    }

    /**
     * Test the hidden metadata is still shown when force is applied.
     *
     */
    @Test
    public void testHiddenDspaceForceCSV() throws Exception {

        DSpaceCSV dSpaceCSV = new DSpaceCSV(true);

        dSpaceCSV.addItem(testItem);

        List<DSpaceCSVLine> lines = dSpaceCSV.getCSVLines();

        assertThat(lines.size(), equalTo(1));

        DSpaceCSVLine line = lines.get(0);

        List<String> subject = line.get("dc.subject");
        List<String> provenance = line.get("dc.description.provenance");
        List<String> title = line.get("dc.title");

        assertNotNull(subject);
        assertNotNull(provenance);
        assertEquals("hidden subject", subject.get(0));
        assertEquals("provenance", provenance.get(0));
        assertNotNull(title);
        assertEquals("Test Item", title.get(0));

    }

}
