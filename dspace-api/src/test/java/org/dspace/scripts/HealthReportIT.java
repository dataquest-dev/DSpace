/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.junit.Test;

/**
 * Integration test for the HealthReport script
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
public class HealthReportIT extends AbstractIntegrationTestWithDatabase {
    @Test
    public void testDefaultHealthcheckRun() throws Exception {

        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();

        String[] args = new String[] { "health-report" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);

        assertThat(testDSpaceRunnableHandler.getErrorMessages(), empty());
        assertThat(testDSpaceRunnableHandler.getWarningMessages(), empty());

        List<String> messages = testDSpaceRunnableHandler.getInfoMessages();
        assertThat(messages, hasSize(1));
        assertThat(messages, hasItem(containsString("HEALTH REPORT:")));
    }

    @Test
    public void testLegalCheck() throws Exception {
        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
        context.turnOffAuthorisationSystem();
        Community rootCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Community community = CommunityBuilder.createSubCommunity(context, rootCommunity)
                .withName("Sub Community A")
                .build();

        Collection collection = CollectionBuilder.createCollection(context, community)
                .withName("Collection 1")
                .withSubmitterGroup(eperson)
                .build();

        Item item = ItemBuilder.createItem(context, collection)
                .withTitle("Test Item Without Bitstream")
                .build();

        String[] args = new String[] { "health-report", "-c", "3" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);

        assertThat(testDSpaceRunnableHandler.getErrorMessages(), empty());
        List<String> messages = testDSpaceRunnableHandler.getInfoMessages();
        assertThat(messages, hasSize(1));
        assertThat(messages, hasItem(containsString("no license")));
    }
    // NOVY TEST
    // Pouzijes builder na vytvorenie Itemu bez bitstreamu (WorkspaceItemBuilder, ItemBUilder(
    /**
     *         WorkspaceItem witem = WorkspaceItemBuilder.createWorkspaceItem(context, col1)
     *                 .withTitle("Test WorkspaceItem")
     *                 .withIssueDate("2017-10-17")
     *                 .withFulltext("simple-article.pdf", "/local/path/simple-article.pdf", pdf)
     *                 .build();
     *
     *         return witem;
     */
    // Zavolat report iba pre LegalCheck
    // assertThat noLicense
}
