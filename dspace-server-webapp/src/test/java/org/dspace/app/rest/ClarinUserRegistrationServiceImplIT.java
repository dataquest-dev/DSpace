/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.ClarinUserRegistrationBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.content.service.clarin.ClarinUserRegistrationService;
import org.dspace.eperson.EPerson;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ClarinUserRegistrationServiceImplIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinUserRegistrationService clarinUserRegistrationService;

    @Test
    public void testFind() throws Exception {
        context.turnOffAuthorisationSystem();
        ClarinUserRegistration clarinUserRegistration = ClarinUserRegistrationBuilder
                .createClarinUserRegistration(context)
                .withEPersonID(admin.getID())
                .build();
        context.restoreAuthSystemState();
        // Find created handle
        EPerson currentUser = context.getCurrentUser();
        context.setCurrentUser(admin);
        Assert.assertEquals(clarinUserRegistration, clarinUserRegistrationService
                .find(context, clarinUserRegistration.getID()));
        context.setCurrentUser(currentUser);
    }

    /**
     * Verify that calling create() with the same eperson_id twice does not produce a duplicate row
     * but instead updates the existing registration and returns it.
     */
    @Test
    public void createShouldUpdateExistingRegistrationForSameEPersonId() throws Exception {
        context.turnOffAuthorisationSystem();

        EPerson testPerson = EPersonBuilder.createEPerson(context)
                .withEmail("duptest@example.com")
                .withPassword("password123")
                .build();

        // First creation — should insert a new row
        ClarinUserRegistration first = new ClarinUserRegistration();
        first.setEmail("duptest@example.com");
        first.setPersonID(testPerson.getID());
        first.setOrganization("OrgA");
        first.setConfirmation(false);

        ClarinUserRegistration created = clarinUserRegistrationService.create(context, first);
        assertNotNull("First create should return a non-null registration", created);
        Integer firstId = created.getID();
        assertNotNull("Created registration should have an ID", firstId);
        assertEquals("OrgA", created.getOrganization());
        assertEquals("duptest@example.com", created.getEmail());

        // Second creation with same eperson_id — should update, not insert
        ClarinUserRegistration second = new ClarinUserRegistration();
        second.setEmail("duptest-updated@example.com");
        second.setPersonID(testPerson.getID());
        second.setOrganization("OrgB");
        second.setConfirmation(true);

        ClarinUserRegistration result = clarinUserRegistrationService.create(context, second);
        assertNotNull("Second create should return a non-null registration", result);

        // The returned registration must be the same row as the first one
        assertEquals("Should return the existing registration ID, not a new one",
                firstId, result.getID());

        // Fields should be updated to the new values
        assertEquals("OrgB", result.getOrganization());
        assertEquals("duptest-updated@example.com", result.getEmail());
        assertTrue("Confirmation should be updated to true", result.isConfirmation());

        // Verify only one row exists in the database for this eperson_id
        List<ClarinUserRegistration> allForEPerson =
                clarinUserRegistrationService.findByEPersonUUID(context, testPerson.getID());
        assertEquals("There must be exactly one registration for this eperson_id",
                1, allForEPerson.size());

        context.restoreAuthSystemState();
    }

    /**
     * Verify that creating registrations for different ePersons still works normally
     * (i.e. the dedup guard does not prevent distinct users from each having a registration).
     */
    @Test
    public void createShouldAllowDifferentEPersonIds() throws Exception {
        context.turnOffAuthorisationSystem();

        EPerson personA = EPersonBuilder.createEPerson(context)
                .withEmail("personA@example.com")
                .withPassword("password123")
                .build();
        EPerson personB = EPersonBuilder.createEPerson(context)
                .withEmail("personB@example.com")
                .withPassword("password123")
                .build();

        ClarinUserRegistration regA = new ClarinUserRegistration();
        regA.setEmail("personA@example.com");
        regA.setPersonID(personA.getID());
        regA.setOrganization("OrgA");
        regA.setConfirmation(true);

        ClarinUserRegistration regB = new ClarinUserRegistration();
        regB.setEmail("personB@example.com");
        regB.setPersonID(personB.getID());
        regB.setOrganization("OrgB");
        regB.setConfirmation(true);

        ClarinUserRegistration createdA = clarinUserRegistrationService.create(context, regA);
        ClarinUserRegistration createdB = clarinUserRegistrationService.create(context, regB);

        assertNotNull(createdA);
        assertNotNull(createdB);
        // They must be distinct rows
        assertTrue("Registrations for different ePersons must have different IDs",
                !createdA.getID().equals(createdB.getID()));
        assertEquals("OrgA", createdA.getOrganization());
        assertEquals("OrgB", createdB.getOrganization());

        context.restoreAuthSystemState();
    }

    /**
     * Verify that creating a registration with a null eperson_id (anonymous) does not trigger
     * the dedup guard and creates a new row every time.
     */
    @Test
    public void createShouldAllowMultipleNullEPersonIds() throws Exception {
        context.turnOffAuthorisationSystem();

        ClarinUserRegistration anon1 = new ClarinUserRegistration();
        anon1.setEmail("anonymous");
        anon1.setOrganization("Unknown");
        anon1.setConfirmation(true);
        // personID is null by default

        ClarinUserRegistration anon2 = new ClarinUserRegistration();
        anon2.setEmail("anonymous");
        anon2.setOrganization("Unknown");
        anon2.setConfirmation(true);

        ClarinUserRegistration created1 = clarinUserRegistrationService.create(context, anon1);
        ClarinUserRegistration created2 = clarinUserRegistrationService.create(context, anon2);

        assertNotNull(created1);
        assertNotNull(created2);
        // Both anonymous, so each should get its own row
        assertTrue("Null-eperson registrations should create separate rows",
                !created1.getID().equals(created2.getID()));

        context.restoreAuthSystemState();
    }
}
