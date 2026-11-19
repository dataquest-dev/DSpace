/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.junit.After;
import org.junit.Test;

/**
 * Runs the create-administrator command and checks the CLARIN user registration it creates.
 */
public class CreateAdministratorIT extends AbstractIntegrationTestWithDatabase {

    private static final String EMAIL = "create-administrator-it@example.com";

    private final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private final ClarinUserRegistrationService clarinUserRegistrationService =
            ClarinServiceFactory.getInstance().getClarinUserRegistration();

    /**
     * Removes the administrator the command created, the command commits with its own context.
     */
    @After
    public void deleteCreatedAdministrator() throws Exception {
        EPerson created = ePersonService.findByEmail(context, EMAIL);
        if (created != null) {
            EPersonBuilder.deleteEPerson(created.getID());
        }
    }

    /**
     * -o ends up in user_registration.organization of the new administrator.
     */
    @Test
    public void organizationOptionIsStoredInUserRegistration() throws Exception {
        CreateAdministrator.main(new String[] {"-e", EMAIL, "-f", "First", "-l", "Last", "-c", "en",
            "-p", password, "-o", "MyOrg"});

        context.turnOffAuthorisationSystem();
        EPerson created = ePersonService.findByEmail(context, EMAIL);
        assertNotNull(created);
        assertTrue(groupService.isMember(context, created, Group.ADMIN));

        List<ClarinUserRegistration> registrations =
                clarinUserRegistrationService.findByEPersonUUID(context, created.getID());
        assertEquals(1, registrations.size());
        assertEquals("MyOrg", registrations.get(0).getOrganization());
        assertEquals(EMAIL, registrations.get(0).getEmail());
        assertTrue(registrations.get(0).isConfirmation());
    }

    /**
     * Without -o the registration gets the unknown organization.
     */
    @Test
    public void missingOrganizationOptionFallsBackToUnknown() throws Exception {
        CreateAdministrator.main(new String[] {"-e", EMAIL, "-f", "First", "-l", "Last", "-c", "en",
            "-p", password});

        context.turnOffAuthorisationSystem();
        EPerson created = ePersonService.findByEmail(context, EMAIL);
        List<ClarinUserRegistration> registrations =
                clarinUserRegistrationService.findByEPersonUUID(context, created.getID());
        assertEquals(1, registrations.size());
        assertEquals(ClarinUserRegistration.UNKNOWN_USER_REGISTRATION, registrations.get(0).getOrganization());
    }
}
