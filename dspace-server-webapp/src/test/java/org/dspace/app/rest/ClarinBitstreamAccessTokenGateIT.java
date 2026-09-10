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
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.dspace.app.requestitem.RequestItem;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.BitstreamResource;
import org.dspace.app.rest.utils.BitstreamResourceAccessByToken;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.MissingLicenseAgreementException;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.builder.ClarinLicenseResourceUserAllowanceBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.RequestItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.clarin.ClarinLicenseResourceMapping;
import org.dspace.content.clarin.ClarinLicenseResourceUserAllowance;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guards the boundary between vanilla request-a-copy and the CLARIN licence gate on
 * {@code GET /api/core/bitstreams/{uuid}/content}.
 * <P>
 * DSpace 9 added an access token to that endpoint and guarded it with a {@code @PreAuthorize} expression
 * whose left side was only {@code #accessToken != null}. Because the operator is {@code ||}, a non-null
 * token satisfied authorization on its own: the resource policies were never consulted, and neither was
 * the CLARIN licence gate that {@code AuthorizeServiceImpl.authorizeAction} runs for every other bitstream
 * read. The fork has no such token path at all, so a valid request-a-copy token streamed licence-protected
 * content to anyone holding it.
 * <P>
 * Request-a-copy stays enabled (owner decision O-8). The token is still honoured, but only together with
 * the CLARIN licence check, so the tests below have to pin down three things at once: the token no longer
 * opens a licence-protected file ({@link #accessTokenDoesNotBypassTheClarinLicenceGate()}), it still opens
 * a file that carries no CLARIN licence ({@link #accessTokenStillServesABitstreamWithoutAClarinLicence()}),
 * and it opens a licence-protected file once the licence flow has been satisfied
 * ({@link #accessTokenServesTheBitstreamOnceTheClarinLicenceIsSatisfied()}) - the last one is what proves
 * the fix wires the {@code accessToken} path into the existing {@code dtoken} gate rather than simply
 * killing request-a-copy for CLARIN items. Two further tests reach past the endpoint to
 * {@code BitstreamResourceAccessByToken}, the class that streams the bytes with authorisation switched
 * off, because a guard on the controller alone would leave that class as a second door.
 */
public class ClarinBitstreamAccessTokenGateIT extends AbstractControllerIntegrationTest {

    private static final String CONTENT_URL = "/api/core/bitstreams/%s/content";
    private static final String ACCESS_TOKEN = "clarin-gate-access-token";
    private static final String OPEN_ACCESS_TOKEN = "clarin-gate-open-access-token";
    private static final String DOWNLOAD_TOKEN = "clarin-gate-download-token";
    private static final String LICENSED_CONTENT = "licensed bitstream content";
    private static final String OPEN_CONTENT = "open bitstream content";

    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;
    @Autowired
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;
    @Autowired
    private ConfigurationService configurationService;

    private Item item;
    /** Restricted by a resource policy AND covered by a CLARIN licence that has to be agreed. */
    private Bitstream licensedBitstream;
    /** Restricted by the same resource policy, but with no CLARIN licence at all. */
    private Bitstream openBitstream;
    private EPerson nonSubmitter;
    private ClarinLicense clarinLicense;
    private ClarinLicenseResourceUserAllowance allowance;

    @Before
    public void setup() throws Exception {
        // If request-a-copy were switched off, every assertion below would pass for the wrong reason.
        assertNotNull("request.item.type must be set, otherwise this test proves nothing",
                configurationService.getProperty("request.item.type"));

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        Collection collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection 1")
                .build();

        // A user who is neither the submitter of the item nor a member of the reader group. The submitter
        // is allowed past the CLARIN gate by design, so the gate can only be observed through someone else.
        nonSubmitter = EPersonBuilder.createEPerson(context)
                .withEmail("non-submitter@mail.com")
                .withPassword(password)
                .withCanLogin(true)
                .build();

        Group readerGroup = GroupBuilder.createGroup(context)
                .withName("Reader Group")
                .addMember(eperson)
                .build();

        item = ItemBuilder.createItem(context, collection)
                .withTitle("Item with a restricted bitstream")
                .withIssueDate("2026-09-10")
                .build();

        try (InputStream is = toStream(LICENSED_CONTENT)) {
            licensedBitstream = BitstreamBuilder.createBitstream(context, item, is)
                    .withName("licensed.txt")
                    .withMimeType("text/plain")
                    .withReaderGroup(readerGroup)
                    .build();
        }
        try (InputStream is = toStream(OPEN_CONTENT)) {
            openBitstream = BitstreamBuilder.createBitstream(context, item, is)
                    .withName("open.txt")
                    .withMimeType("text/plain")
                    .withReaderGroup(readerGroup)
                    .build();
        }

        // Only the first bitstream gets a CLARIN licence, and one that always has to be agreed to.
        clarinLicense = createClarinLicense(ClarinLicense.Confirmation.ASK_ALWAYS);
        clarinLicenseResourceMappingService.attachLicense(context, clarinLicense, licensedBitstream);

        context.restoreAuthSystemState();
        // BitstreamResourceAccessByToken reads through a Context in READ_ONLY mode, whose session does not
        // auto-flush, so anything still pending in this session would simply not be there.
        context.commit();
    }

    /**
     * The CLARIN rows are not part of the ordered builder cleanup, so the allowance and the resource
     * mapping have to go before the licence they point at, the way
     * {@code ClarinLinkRestRepositoryBeanNameIT} does it.
     */
    @Override
    public void destroy() throws Exception {
        if (allowance != null) {
            ClarinLicenseResourceUserAllowanceBuilder.deleteClarinLicenseResourceUserAllowance(allowance.getID());
            allowance = null;
        }
        if (licensedBitstream != null) {
            context.turnOffAuthorisationSystem();
            clarinLicenseResourceMappingService.detachLicenses(context, licensedBitstream);
            context.restoreAuthSystemState();
            licensedBitstream = null;
        }
        super.destroy();
    }

    /**
     * The bypass itself. A valid, accepted, unexpired request-a-copy token for a bitstream that sits behind
     * a CLARIN licence must not serve the content - neither to an anonymous caller nor to a logged-in one
     * who has not been through the licence flow.
     */
    @Test
    public void accessTokenDoesNotBypassTheClarinLicenceGate() throws Exception {
        RequestItem request = acceptedRequestFor(licensedBitstream);

        // Anonymous, valid token: 401 (before the fix: 200 with the file body)
        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        // Logged in but neither submitter nor reader, valid token: 403
        String nonSubmitterToken = getAuthToken(nonSubmitter.getEmail(), password);
        getClient(nonSubmitterToken).perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * The other half of the decision: request-a-copy is not disabled. A bitstream that carries no CLARIN
     * licence still answers a valid access token with its content, exactly as it did before the fix.
     */
    @Test
    public void accessTokenStillServesABitstreamWithoutAClarinLicence() throws Exception {
        RequestItem request = acceptedRequestFor(openBitstream);

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isOk())
                .andExpect(content().string(OPEN_CONTENT));

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * The wiring. The CLARIN gate reads {@code dtoken}, the vanilla path carries {@code accessToken}; the
     * fix routes the second through the first instead of copying the licence rules. A caller who holds both
     * - a valid access token and a download token minted by the licence flow - is served, which is what
     * shows the licence check is being satisfied rather than merely refusing everything.
     */
    @Test
    public void accessTokenServesTheBitstreamOnceTheClarinLicenceIsSatisfied() throws Exception {
        RequestItem request = acceptedRequestFor(licensedBitstream);
        agreeToTheLicence();

        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token())
                        .param("dtoken", DOWNLOAD_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(LICENSED_CONTENT));

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * The regression guard: nothing about a download without an access token may change. An anonymous
     * caller is refused, a member of the reader group is served, and an invalid token is still refused.
     */
    @Test
    public void behaviourWithoutAnAccessTokenIsUnchanged() throws Exception {
        // No token, anonymous: refused for both bitstreams
        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID())))
                .andExpect(status().isUnauthorized());
        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID())))
                .andExpect(status().isUnauthorized());

        // No token, member of the reader group: served the bitstream that carries no CLARIN licence
        String epersonToken = getAuthToken(eperson.getEmail(), password);
        getClient(epersonToken).perform(get(String.format(CONTENT_URL, openBitstream.getID())))
                .andExpect(status().isOk())
                .andExpect(content().string(OPEN_CONTENT));

        // An invalid access token is still refused
        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The endpoint tests above stop at {@code @PreAuthorize}, so on their own they say nothing about the
     * class that actually streams the bytes. {@code BitstreamResourceAccessByToken} opens its own context
     * and turns authorisation off in it, so it has to enforce the licence itself - anything else leaves a
     * second door into the same content for any caller that can reach the class.
     */
    @Test
    public void bitstreamResourceRefusesToServeLicensedContentOnAnAccessTokenAlone() throws Exception {
        RequestItem licensedRequest = acceptedRequestFor(licensedBitstream, ACCESS_TOKEN);

        BitstreamResource licensedResource = new BitstreamResourceAccessByToken("licensed.txt",
                licensedBitstream.getID(), null, Set.of(), false, licensedRequest.getAccess_token());
        try {
            licensedResource.getChecksum();
            fail("BitstreamResourceAccessByToken served a CLARIN licence protected bitstream on an access"
                    + " token alone");
        } catch (RuntimeException e) {
            assertTrue("Expected the CLARIN licence gate to refuse, but the failure was: " + e,
                    isCausedByMissingLicenceAgreement(e));
        }
    }

    /**
     * The companion of the test above: the licence check added to the streaming resource must not turn it
     * into a resource that refuses everything. A bitstream with no CLARIN licence is still served.
     * <P>
     * It is a test of its own because the failing case above aborts the shared transaction on its way out,
     * which would take this fixture with it.
     */
    @Test
    public void bitstreamResourceStillServesUnlicensedContentOnAnAccessToken() throws Exception {
        RequestItem openRequest = acceptedRequestFor(openBitstream, OPEN_ACCESS_TOKEN);

        BitstreamResource openResource = new BitstreamResourceAccessByToken("open.txt", openBitstream.getID(),
                null, Set.of(), false, openRequest.getAccess_token());
        assertEquals(OPEN_CONTENT, IOUtils.toString(openResource.getInputStream(), StandardCharsets.UTF_8));

        RequestItemBuilder.deleteRequestItem(openRequest.getToken());
    }

    private boolean isCausedByMissingLicenceAgreement(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof MissingLicenseAgreementException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Mints an accepted, unexpired request-a-copy access token for one bitstream.
     */
    private RequestItem acceptedRequestFor(Bitstream bitstream) throws Exception {
        return acceptedRequestFor(bitstream, ACCESS_TOKEN);
    }

    private RequestItem acceptedRequestFor(Bitstream bitstream, String accessToken) throws Exception {
        RequestItem requestItem = RequestItemBuilder.createRequestItem(context, item, bitstream)
                .withAcceptRequest(true)
                .withDecisionDate(Instant.now())
                .withAccessToken(accessToken)
                .withAccessExpiry(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        context.commit();
        return requestItem;
    }

    /**
     * Records that the licence has been agreed, by creating the allowance row the CLARIN licence flow
     * writes and whose token it hands back as the {@code dtoken} request parameter.
     */
    private void agreeToTheLicence() throws Exception {
        List<ClarinLicenseResourceMapping> mappings =
                clarinLicenseResourceMappingService.findByBitstreamUUID(context, licensedBitstream.getID());
        assertEquals("The CLARIN licence fixture did not attach exactly one resource mapping",
                1, mappings.size());

        context.turnOffAuthorisationSystem();
        allowance = ClarinLicenseResourceUserAllowanceBuilder.createClarinLicenseResourceUserAllowance(context)
                .withToken(DOWNLOAD_TOKEN)
                .withCreatedOn(new Date())
                .withMapping(mappings.get(0))
                .build();
        context.restoreAuthSystemState();
        context.commit();
    }

    /**
     * Creates a CLARIN licence with one label, mirroring {@code AuthorizationRestControllerIT}.
     */
    private ClarinLicense createClarinLicense(ClarinLicense.Confirmation confirmation)
            throws SQLException, AuthorizeException {
        ClarinLicenseLabel label = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        label.setLabel("GAT");
        label.setExtended(false);
        label.setTitle("Access token gate label");
        clarinLicenseLabelService.update(context, label);

        ClarinLicense license = ClarinLicenseBuilder.createClarinLicense(context).build();
        license.setName("Access token gate licence");
        license.setDefinition("http://example.com/licence");
        license.setRequiredInfo("NAME");
        license.setConfirmation(confirmation);
        HashSet<ClarinLicenseLabel> labels = new HashSet<>();
        labels.add(label);
        license.setLicenseLabels(labels);
        clarinLicenseService.update(context, license);
        return license;
    }
}
