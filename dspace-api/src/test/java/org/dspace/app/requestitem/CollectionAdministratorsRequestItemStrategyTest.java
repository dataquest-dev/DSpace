/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.requestitem;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.EPersonService;
import org.dspace.services.ConfigurationService;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 * @author Mark H. Wood <mwood@iupui.edu>
 */
public class CollectionAdministratorsRequestItemStrategyTest {
    private static final String NAME = "John Q. Public";
    private static final String EMAIL = "jqpublic@example.com";
    private static final String HELPDESK_NAME = "Help Desk Staff";
    private static final String HELPDESK_EMAIL = "helpdesk@example.com";

    /**
     * Test of getRequestItemAuthor method, of class CollectionAdministratorsRequestItemStrategy.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthor()
            throws Exception {
        System.out.println("getRequestItemAuthor");

        Context context = Mockito.mock(Context.class);

        EPerson eperson1 = Mockito.mock(EPerson.class);
        Mockito.when(eperson1.getEmail()).thenReturn(EMAIL);
        Mockito.when(eperson1.getFullName()).thenReturn(NAME);

        Group group1 = Mockito.mock(Group.class);
        Mockito.when(group1.getMembers()).thenReturn(List.of(eperson1));

        Collection collection1 = Mockito.mock(Collection.class);
        Mockito.when(collection1.getAdministrators()).thenReturn(group1);

        Item item = Mockito.mock(Item.class);
        Mockito.when(item.getOwningCollection()).thenReturn(collection1);
        Mockito.when(item.getSubmitter()).thenReturn(eperson1);

        CollectionAdministratorsRequestItemStrategy instance = new CollectionAdministratorsRequestItemStrategy();
        List<RequestItemAuthor> result = instance.getRequestItemAuthor(context,
                item);
        assertEquals("Should be one author", 1, result.size());
        assertEquals("Name should match " + NAME, NAME, result.get(0).getFullName());
        assertEquals("Email should match " + EMAIL, EMAIL, result.get(0).getEmail());
    }

    /**
     * Test of getRequestItemAuthor method, of class CollectionAdministratorsRequestItemStrategy,
     * with no collection administrators and mail.helpdesk set:  delegates to the help desk.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthorNoAdministratorsWithHelpdesk()
            throws Exception {
        Context context = Mockito.mock(Context.class);

        EPerson submitter = Mockito.mock(EPerson.class);
        Mockito.when(submitter.getEmail()).thenReturn(EMAIL);
        Mockito.when(submitter.getFullName()).thenReturn(NAME);

        // A matching EPerson keeps getHelpDeskPerson() away from I18nUtil, which needs the kernel.
        EPerson helpdesk = Mockito.mock(EPerson.class);
        Mockito.when(helpdesk.getEmail()).thenReturn(HELPDESK_EMAIL);
        Mockito.when(helpdesk.getFullName()).thenReturn(HELPDESK_NAME);

        Item item = mockItemWithoutAdministrators(submitter);

        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Mockito.when(configurationService.getProperty(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK))
                .thenReturn(HELPDESK_EMAIL);
        EPersonService ePersonService = Mockito.mock(EPersonService.class);
        Mockito.when(ePersonService.findByEmail(context, HELPDESK_EMAIL)).thenReturn(helpdesk);

        CollectionAdministratorsRequestItemStrategy instance = new CollectionAdministratorsRequestItemStrategy();
        instance.configurationService = configurationService;
        instance.ePersonService = ePersonService;
        List<RequestItemAuthor> result = instance.getRequestItemAuthor(context,
                item);
        assertEquals("Should be one author", 1, result.size());
        assertEquals("Name should match " + HELPDESK_NAME, HELPDESK_NAME, result.get(0).getFullName());
        assertEquals("Email should match " + HELPDESK_EMAIL, HELPDESK_EMAIL, result.get(0).getEmail());
    }

    /**
     * Test of getRequestItemAuthor method, of class CollectionAdministratorsRequestItemStrategy,
     * with no collection administrators and blank mail.helpdesk:  falls back to the submitter.
     * @throws java.lang.Exception passed through.
     */
    @Test
    public void testGetRequestItemAuthorNoAdministratorsWithoutHelpdesk()
            throws Exception {
        Context context = Mockito.mock(Context.class);

        EPerson submitter = Mockito.mock(EPerson.class);
        Mockito.when(submitter.getEmail()).thenReturn(EMAIL);
        Mockito.when(submitter.getFullName()).thenReturn(NAME);

        Item item = mockItemWithoutAdministrators(submitter);

        ConfigurationService configurationService = Mockito.mock(ConfigurationService.class);
        Mockito.when(configurationService.getProperty(RequestItemHelpdeskStrategy.P_MAIL_HELPDESK))
                .thenReturn("");
        EPersonService ePersonService = Mockito.mock(EPersonService.class);

        CollectionAdministratorsRequestItemStrategy instance = new CollectionAdministratorsRequestItemStrategy();
        instance.configurationService = configurationService;
        instance.ePersonService = ePersonService;
        List<RequestItemAuthor> result = instance.getRequestItemAuthor(context,
                item);
        assertEquals("Should be one author", 1, result.size());
        assertEquals("Name should match " + NAME, NAME, result.get(0).getFullName());
        assertEquals("Email should match " + EMAIL, EMAIL, result.get(0).getEmail());
    }

    private static Item mockItemWithoutAdministrators(EPerson submitter) {
        Group emptyGroup = Mockito.mock(Group.class);
        Mockito.when(emptyGroup.getMembers()).thenReturn(List.of());

        Collection collection = Mockito.mock(Collection.class);
        Mockito.when(collection.getAdministrators()).thenReturn(emptyGroup);

        Item item = Mockito.mock(Item.class);
        Mockito.when(item.getOwningCollection()).thenReturn(collection);
        Mockito.when(item.getSubmitter()).thenReturn(submitter);
        return item;
    }
}
