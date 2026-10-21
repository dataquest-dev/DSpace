/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.requestitem;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractUnitTest;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author mwood
 */
public class RequestItemHelpdeskStrategyTest
        extends AbstractUnitTest {
    private static final String HELPDESK_ADDRESS = "helpdesk@example.com";
    // Distinct from HELPDESK_ADDRESS:  builder cleanup only happens after the
    // class, so this EPerson must not match the "no EPerson" test's address.
    private static final String HELPDESK_EPERSON_ADDRESS = "helpdesk.staff@example.com";
    private static final String AUTHOR_ADDRESS = "john.doe@example.com";
    private static final String HELPDESK_NAME_KEY
            = "org.dspace.app.requestitem.RequestItemHelpdeskStrategy.helpdeskname";

    private static ConfigurationService configurationService;
    private static EPersonService epersonService;
    private static EPerson johnDoe;

    private Item item;

    // ConfigurationService is not reset between test classes, so restore it.
    private Object originalMailHelpdesk;

    @BeforeClass
    public static void setUpClass()
            throws SQLException {
        AbstractBuilder.init(); // AbstractUnitTest doesn't do this for us.

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        epersonService = EPersonServiceFactory.getInstance().getEPersonService();

        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        johnDoe = EPersonBuilder.createEPerson(ctx)
                .withEmail(AUTHOR_ADDRESS)
                .withNameInMetadata("John", "Doe")
                .build();
        ctx.restoreAuthSystemState();
        ctx.complete();
    }

    @AfterClass
    public static void tearDownClass() {
        AbstractBuilder.destroy(); // AbstractUnitTest doesn't do this for us.
    }

    /**
     * Remember the configuration a test may change, and create an Item
     * submitted by John Doe.
     */
    @Before
    public void setUp() {
        originalMailHelpdesk = configurationService.getPropertyValue(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK);

        context = new Context();
        context.setCurrentUser(johnDoe);
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        Collection collection = CollectionBuilder.createCollection(context, community).build();
        item = ItemBuilder.createItem(context, collection)
                .build();
        context.restoreAuthSystemState();
        context.setCurrentUser(null);
    }

    /**
     * Restore the configuration changed by a test.  AbstractUnitTest does not
     * reset ConfigurationService, so a leftover value would leak into other test classes.
     */
    @After
    public void restoreConfiguration() {
        // A null original clears the property again.
        configurationService.setProperty(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK, originalMailHelpdesk);
    }

    /**
     * Test of getRequestItemAuthor method, of class RequestItemHelpdeskStrategy,
     * with a help desk address that matches no EPerson.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthorWithHelpdesk()
            throws Exception {
        RequestItemHelpdeskStrategy instance = newInstance();

        configurationService.setProperty(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK, HELPDESK_ADDRESS);
        List<RequestItemAuthor> authors = instance.getRequestItemAuthor(context, item);
        assertEquals("Should be one author", 1, authors.size());
        assertEquals("Wrong author address", HELPDESK_ADDRESS, authors.get(0).getEmail());
        assertEquals("Wrong author name", I18nUtil.getMessage(HELPDESK_NAME_KEY, context),
                authors.get(0).getFullName());
    }

    /**
     * Test of getRequestItemAuthor method, of class RequestItemHelpdeskStrategy,
     * with a blank help desk address:  falls back to the submitter.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthorWithoutHelpdesk()
            throws Exception {
        RequestItemHelpdeskStrategy instance = newInstance();

        configurationService.setProperty(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK, "");
        List<RequestItemAuthor> authors = instance.getRequestItemAuthor(context, item);
        assertEquals("Should be one author", 1, authors.size());
        assertEquals("Wrong author address", AUTHOR_ADDRESS, authors.get(0).getEmail());
        assertEquals("Wrong author name", johnDoe.getFullName(), authors.get(0).getFullName());
    }

    /**
     * Test of getHelpDeskPerson method, of class RequestItemHelpdeskStrategy,
     * with a help desk address that matches an existing EPerson.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetHelpDeskPerson()
            throws Exception {
        context.turnOffAuthorisationSystem();
        EPerson helpdeskEPerson = EPersonBuilder.createEPerson(context)
                .withEmail(HELPDESK_EPERSON_ADDRESS)
                .withNameInMetadata("Helen", "Deskworth") // must differ from the i18n "helpdeskname"
                .build();
        context.restoreAuthSystemState();

        RequestItemHelpdeskStrategy instance = newInstance();
        RequestItemAuthor author = instance.getHelpDeskPerson(context, HELPDESK_EPERSON_ADDRESS);
        assertEquals("Wrong author address", HELPDESK_EPERSON_ADDRESS, author.getEmail());
        assertEquals("Wrong author name", helpdeskEPerson.getFullName(), author.getFullName());
    }

    private RequestItemHelpdeskStrategy newInstance() {
        RequestItemHelpdeskStrategy instance = new RequestItemHelpdeskStrategy();
        instance.configurationService = configurationService;
        instance.ePersonService = epersonService;
        return instance;
    }
}
