/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.dspace.app.rest.model.ClarinLicenseResourceMappingRest;
import org.dspace.app.rest.model.ClarinLicenseResourceUserAllowanceRest;
import org.dspace.app.rest.model.ClarinUserRegistrationRest;
import org.dspace.app.rest.model.LinkRest;
import org.dspace.app.rest.model.LinksRest;
import org.dspace.app.rest.model.RestAddressableModel;
import org.dspace.app.rest.repository.LinkRestRepository;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guards the DSpace 9 link-repository bean naming contract for the CLARIN models.
 * <P>
 * DSpace 7 resolved a rel by singularizing the URL segment before the bean lookup
 * ({@code Utils.getLinkResourceRepository} called {@code makeSingular}). DSpace 9 removed that
 * step and looks the bean up under the plural segment verbatim, so every
 * {@link LinkRestRepository} must be registered as
 * {@code <category>.<typePlural>.<rel>}. A repository still registered under the singular name
 * is simply never found: the lookup raises {@code RepositoryNotFoundException}, the client gets
 * a 404, and because a missing route resolves BEFORE any authorization check the same defect
 * shows up as "404 instead of 200" for an admin and "404 instead of 401/403" for everyone else.
 * It therefore reads like an authorization bug while the object's own {@code _links} keep
 * advertising the dead rels.
 * <P>
 * This test asserts the invariant directly instead of going through HTTP, because the link
 * repositories also raise {@code ResourceNotFoundException} (another 404) when the linked data
 * simply does not exist -- an endpoint test could not tell the two apart without fixtures for
 * every entity type. It is deliberately driven off the {@link LinksRest} annotation rather than
 * a hardcoded list, so a rel added to any of these models is covered automatically.
 */
public class ClarinLinkRestRepositoryBeanNameIT extends AbstractControllerIntegrationTest {

    @Autowired
    private Utils utils;

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
            String expectedBeanName = model.getCategory() + "." + model.getTypePlural() + "." + linkRest.name();
            // Throws RepositoryNotFoundException (-> HTTP 404) when the bean is registered under
            // the old singular name instead of the plural one.
            LinkRestRepository repository =
                    utils.getLinkResourceRepository(model.getCategory(), model.getTypePlural(), linkRest.name());
            assertNotNull("No LinkRestRepository registered as '" + expectedBeanName + "'", repository);
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
}
