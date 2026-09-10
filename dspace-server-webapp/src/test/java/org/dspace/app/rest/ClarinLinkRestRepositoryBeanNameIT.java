/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.dspace.app.rest.exception.RepositoryNotFoundException;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.ClarinLicenseResourceMappingRest;
import org.dspace.app.rest.model.ClarinLicenseResourceUserAllowanceRest;
import org.dspace.app.rest.model.ClarinUserRegistrationRest;
import org.dspace.app.rest.model.LinkRest;
import org.dspace.app.rest.model.LinksRest;
import org.dspace.app.rest.model.RestAddressableModel;
import org.dspace.app.rest.repository.LinkRestRepository;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.builder.ClarinLicenseResourceMappingBuilder;
import org.dspace.builder.ClarinLicenseResourceUserAllowanceBuilder;
import org.dspace.builder.ClarinUserMetadataBuilder;
import org.dspace.builder.ClarinUserRegistrationBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.content.clarin.ClarinUserRegistration;
import org.dspace.eperson.EPerson;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Guards the DSpace 9 link-repository contract for the CLARIN models: the bean naming that decides
 * whether a rel resolves at all, and the HTTP status the rel answers for each role.
 * <P>
 * DSpace 7 resolved a rel by singularizing the URL segment before the bean lookup
 * ({@code Utils.getLinkResourceRepository} called {@code makeSingular}). DSpace 9 removed that
 * step and looks the bean up under the plural segment verbatim, so every
 * {@link LinkRestRepository} must be registered as
 * {@code category.typePlural.rel}. A repository still registered under the singular name
 * is simply never found: the lookup raises {@code RepositoryNotFoundException}, the client gets
 * a 404, and because a missing route resolves BEFORE any authorization check the same defect
 * shows up as "404 instead of 200" for an admin and "404 instead of 401/403" for everyone else.
 * It therefore reads like an authorization bug while the object's own {@code _links} keep
 * advertising the dead rels.
 * <P>
 * Registering the beans under the plural name then exposed a second defect that the dead route had
 * been hiding: the repository body ran for the first time and answered anonymous callers with
 * HTTP 500 instead of 401. Two causes, both in fork code -- a {@code NullPointerException} in
 * {@code ClarinUserRegistrationServiceImpl.authorizeClarinUserRegistrationAction} for a context
 * with no current user, and a checked {@code AuthorizeException} escaping
 * {@code CLRUAResourceMappingLinkRepository}, which {@code RestResourceController.findRelInternal}
 * wraps into a {@code RuntimeException}. The role tests below lock both in.
 * <P>
 * The bean-name tests assert the invariant directly instead of going through HTTP, because the link
 * repositories also raise {@code ResourceNotFoundException} (another 404) when the linked data
 * simply does not exist -- an endpoint test could not tell the two apart without fixtures for
 * every entity type. They are driven off the {@link LinksRest} annotation and off the registered
 * beans rather than a hardcoded list, so a rel added to any of these models is covered
 * automatically.
 */
public class ClarinLinkRestRepositoryBeanNameIT extends AbstractControllerIntegrationTest {

    private static final String USER_REGISTRATIONS_URL = "/api/core/clarinuserregistrations/";
    private static final String ALLOWANCES_URL = "/api/core/clarinlruallowances/";
    private static final String OTHER_EPERSON_EMAIL = "other-eperson@mail.com";

    @Autowired
    private Utils utils;

    @Autowired
    private ApplicationContext applicationContext;

    private ClarinLicenseResourceUserAllowance allowance;

    /**
     * The CLARIN builders are not part of the ordered cleanup map, so they are torn down in the order they
     * were first used: the user registration would be deleted while the allowance still references it. Drop
     * the allowance first, the way {@code ClarinLicenseResourceUserAllowanceServiceImplIT} does.
     */
    @Override
    public void destroy() throws Exception {
        if (allowance != null) {
            ClarinLicenseResourceUserAllowanceBuilder.deleteClarinLicenseResourceUserAllowance(allowance.getID());
            allowance = null;
        }
        super.destroy();
    }

    /**
     * Asserts that every rel declared via {@link LinksRest} on the given model resolves to a
     * registered {@link LinkRestRepository}, using the same lookup {@link RestResourceController}
     * performs when a client traverses the rel.
     *
     * @param modelClass the REST model whose declared rels should all be resolvable
     */
    private void assertAllDeclaredRelsResolve(Class<? extends RestAddressableModel> modelClass)
            throws ReflectiveOperationException {
        RestAddressableModel model = modelClass.getDeclaredConstructor().newInstance();
        LinksRest linksRest = modelClass.getDeclaredAnnotation(LinksRest.class);
        assertNotNull(modelClass.getSimpleName() + " is expected to declare @LinksRest", linksRest);
        assertTrue(modelClass.getSimpleName() + " is expected to declare at least one @LinkRest",
                linksRest.links().length > 0);

        for (LinkRest linkRest : linksRest.links()) {
            assertRelResolves(model.getCategory(), model.getTypePlural(), linkRest.name());
        }
    }

    /**
     * Asserts that a single rel resolves to a registered {@link LinkRestRepository}.
     *
     * @param category   the REST category of the model owning the rel
     * @param typePlural the plural model name, as it appears in the URL
     * @param rel        the name of the rel
     */
    private void assertRelResolves(String category, String typePlural, String rel) {
        String expectedBeanName = category + "." + typePlural + "." + rel;
        try {
            LinkRestRepository repository = utils.getLinkResourceRepository(category, typePlural, rel);
            assertNotNull("No LinkRestRepository registered as '" + expectedBeanName + "'", repository);
        } catch (RepositoryNotFoundException e) {
            // Translate the lookup failure into an actionable assertion. RepositoryNotFoundException
            // reports only "<category>.<typePlural>" and never the rel, so on a model with several
            // rels its own message cannot say which one is unregistered. It is also misleading here:
            // it claims the repository *type* is missing when the main repository resolves fine and
            // only the link repository bean is absent.
            throw new AssertionError("No LinkRestRepository is registered as '" + expectedBeanName
                    + "'. On DSpace 9 link repositories are looked up under the plural model name, so"
                    + " the @Component of the repository serving this rel must be built from"
                    + " PLURAL_NAME, not NAME.", e);
        }
    }

    @Test
    public void clarinLicenseResourceUserAllowanceRelsResolve() throws Exception {
        // resourceMapping, userRegistration, userMetadata
        assertAllDeclaredRelsResolve(ClarinLicenseResourceUserAllowanceRest.class);
    }

    @Test
    public void clarinUserRegistrationRelsResolve() throws Exception {
        // clarinLicenses, userMetadata
        assertAllDeclaredRelsResolve(ClarinUserRegistrationRest.class);
    }

    @Test
    public void clarinLicenseResourceMappingRelsResolve() throws Exception {
        // clarinLicense
        assertAllDeclaredRelsResolve(ClarinLicenseResourceMappingRest.class);
    }

    /**
     * The fork adds one rel to a vanilla model, so the CLARIN-model tests above do not cover it. Its bean
     * was made plural by a different commit than the six CLARIN ones, which is why it needs its own guard.
     */
    @Test
    public void bitstreamChecksumRelResolves() {
        assertRelResolves(BitstreamRest.CATEGORY, BitstreamRest.PLURAL_NAME, BitstreamRest.CHECKSUM);
    }

    /**
     * The model-driven tests above only cover the models somebody remembered to list. This one covers
     * every registered link repository, including one belonging to a model added later: a link repository
     * bean is named {@code category.typePlural.rel}, so its {@code category.typePlural} prefix must itself
     * be a registered bean -- the main repository, which is registered under the plural name. A link
     * repository still built from the singular {@code NAME} names a prefix that no bean answers to, and
     * fails here with its own bean name in the message.
     */
    @Test
    public void everyRegisteredLinkRepositoryUsesThePluralModelName() {
        Map<String, LinkRestRepository> linkRepositories =
                applicationContext.getBeansOfType(LinkRestRepository.class);
        assertTrue("No LinkRestRepository beans found - the lookup itself is broken",
                linkRepositories.size() > 0);

        for (String beanName : linkRepositories.keySet()) {
            String[] segments = beanName.split("\\.");
            assertEquals("The link repository bean '" + beanName + "' is not named category.typePlural.rel",
                    3, segments.length);
            String modelBeanName = segments[0] + "." + segments[1];
            assertTrue("The link repository bean '" + beanName + "' refers to the model '" + modelBeanName
                            + "', for which no repository is registered. On DSpace 9 the model segment must be"
                            + " the PLURAL_NAME of the model, not its NAME.",
                    applicationContext.containsBean(modelBeanName));
        }
    }

    /**
     * Builds a CLARIN user registration owned by {@code eperson}, with one user metadata row attached.
     *
     * @return the user registration
     */
    private ClarinUserRegistration userRegistrationOwnedByEPerson() throws Exception {
        context.turnOffAuthorisationSystem();
        ClarinUserRegistration clarinUserRegistration = ClarinUserRegistrationBuilder
                .createClarinUserRegistration(context)
                .withEPersonID(eperson.getID())
                .build();
        ClarinUserMetadataBuilder.createClarinUserMetadata(context)
                .withUserRegistration(clarinUserRegistration)
                .build();
        context.restoreAuthSystemState();
        return clarinUserRegistration;
    }

    /**
     * Builds a second EPerson, so that a caller who is authenticated but owns nothing can be tested.
     *
     * @return the other EPerson
     */
    private EPerson otherEPerson() throws Exception {
        context.turnOffAuthorisationSystem();
        EPerson otherEPerson = EPersonBuilder.createEPerson(context)
                .withEmail(OTHER_EPERSON_EMAIL)
                .withPassword(password)
                .build();
        context.restoreAuthSystemState();
        return otherEPerson;
    }

    /**
     * Both rels of a user registration answered 500 anonymously, because the authorization helper
     * dereferenced the (null) current user before it could throw AuthorizeException. The parent resource
     * answers 401, so the rels must answer 401 too.
     */
    @Test
    public void anonymousUserRegistrationRelsAreUnauthorized() throws Exception {
        ClarinUserRegistration clarinUserRegistration = userRegistrationOwnedByEPerson();

        getClient().perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID()))
                .andExpect(status().isUnauthorized());
        getClient().perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/userMetadata"))
                .andExpect(status().isUnauthorized());
        getClient().perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/clarinLicenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void adminUserRegistrationRelsAreOk() throws Exception {
        ClarinUserRegistration clarinUserRegistration = userRegistrationOwnedByEPerson();

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/userMetadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userMetadata", hasSize(1)));
        getClient(adminToken).perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/clarinLicenses"))
                .andExpect(status().isOk());
    }

    /**
     * A logged-in user who does not own the registration must be refused, not served and not answered with
     * a 500; the owner must still be served, which is what proves the fix does not simply deny everybody.
     */
    @Test
    public void otherUserRegistrationRelsAreForbidden() throws Exception {
        ClarinUserRegistration clarinUserRegistration = userRegistrationOwnedByEPerson();
        String otherUserToken = getAuthToken(otherEPerson().getEmail(), password);

        getClient(otherUserToken)
                .perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/userMetadata"))
                .andExpect(status().isForbidden());
        getClient(otherUserToken)
                .perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/clarinLicenses"))
                .andExpect(status().isForbidden());

        String ownerToken = getAuthToken(eperson.getEmail(), password);
        getClient(ownerToken)
                .perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/userMetadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.userMetadata", hasSize(1)));
        getClient(ownerToken)
                .perform(get(USER_REGISTRATIONS_URL + clarinUserRegistration.getID() + "/clarinLicenses"))
                .andExpect(status().isOk());
    }

    /**
     * Builds a licence resource user allowance owned by {@code eperson}. The resource mapping is attached
     * because the repository answers 404 when it is missing, which would hide the status under test.
     *
     * @return the licence resource user allowance
     */
    private ClarinLicenseResourceUserAllowance allowanceOwnedByEPerson() throws Exception {
        context.turnOffAuthorisationSystem();
        ClarinUserRegistration clarinUserRegistration = ClarinUserRegistrationBuilder
                .createClarinUserRegistration(context)
                .withEPersonID(eperson.getID())
                .build();
        allowance = ClarinLicenseResourceUserAllowanceBuilder
                .createClarinLicenseResourceUserAllowance(context)
                .withUser(clarinUserRegistration)
                .withMapping(ClarinLicenseResourceMappingBuilder.createClarinLicenseResourceMapping(context).build())
                .build();
        context.restoreAuthSystemState();
        return allowance;
    }

    /**
     * The resourceMapping rel answered 500 for every caller, because its repository was the only one of the
     * three siblings that let the checked AuthorizeException escape. The two denial cases are the
     * regression. The serving case for this fixture is covered by
     * {@link #clruaUserRegistrationAndUserMetadataRelsHonourRoles()}, which reaches the same service
     * authorization through a rel whose converter does not need a bitstream.
     */
    @Test
    public void anonymousAndForeignClruaResourceMappingIsDenied() throws Exception {
        ClarinLicenseResourceUserAllowance allowance = allowanceOwnedByEPerson();

        getClient().perform(get(ALLOWANCES_URL + allowance.getID() + "/resourceMapping"))
                .andExpect(status().isUnauthorized());

        String otherUserToken = getAuthToken(otherEPerson().getEmail(), password);
        getClient(otherUserToken).perform(get(ALLOWANCES_URL + allowance.getID() + "/resourceMapping"))
                .andExpect(status().isForbidden());
    }

    /**
     * These two rels already answered 401/403 before the fix, through a different exception type. Locking
     * their statuses in keeps a later alignment of the idiom from silently regressing them.
     */
    @Test
    public void clruaUserRegistrationAndUserMetadataRelsHonourRoles() throws Exception {
        ClarinLicenseResourceUserAllowance allowance = allowanceOwnedByEPerson();
        String otherUserToken = getAuthToken(otherEPerson().getEmail(), password);

        for (String rel : new String[] {"userRegistration", "userMetadata"}) {
            getClient().perform(get(ALLOWANCES_URL + allowance.getID() + "/" + rel))
                    .andExpect(status().isUnauthorized());
            getClient(otherUserToken).perform(get(ALLOWANCES_URL + allowance.getID() + "/" + rel))
                    .andExpect(status().isForbidden());
        }

        // The userMetadata rel answers 404 when the allowance carries none, so only the rel that always has
        // a value can assert that the owner and the administrator are served.
        String ownerToken = getAuthToken(eperson.getEmail(), password);
        getClient(ownerToken).perform(get(ALLOWANCES_URL + allowance.getID() + "/userRegistration"))
                .andExpect(status().isOk());
        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(get(ALLOWANCES_URL + allowance.getID() + "/userRegistration"))
                .andExpect(status().isOk());
    }
}
