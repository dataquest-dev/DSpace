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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
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
 * Request-a-copy access tokens on {@code GET /api/core/bitstreams/{uuid}/content}.
 * <P>
 * The token opens only bitstreams of the item of its request, and the CLARIN licence still applies: a
 * licence-protected bitstream is served on a token only once the licence is agreed, as without a token.
 */
public class ClarinBitstreamAccessTokenGateIT extends AbstractControllerIntegrationTest {

    private static final String CONTENT_URL = "/api/core/bitstreams/%s/content";
    private static final String ACCESS_TOKEN = "clarin-gate-access-token";
    private static final String OPEN_ACCESS_TOKEN = "clarin-gate-open-access-token";
    private static final String ALL_FILES_TOKEN = "clarin-gate-all-files-token";
    private static final String DOWNLOAD_TOKEN = "clarin-gate-download-token";
    private static final String LICENSED_CONTENT = "licensed bitstream content";
    private static final String OPEN_CONTENT = "open bitstream content";
    private static final String OTHER_CONTENT = "bitstream content of another item";

    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;
    @Autowired
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;
    @Autowired
    private ConfigurationService configurationService;

    private Collection collection;
    private Group readerGroup;
    private Item item;
    /** Restricted by a resource policy AND covered by a CLARIN licence that has to be agreed. */
    private Bitstream licensedBitstream;
    /** Restricted by the same resource policy, but with no CLARIN licence at all. */
    private Bitstream openBitstream;
    private EPerson nonSubmitter;
    private EPerson licenceReader;
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
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection 1")
                .build();

        // Neither the submitter of the item nor a member of the reader group.
        nonSubmitter = EPersonBuilder.createEPerson(context)
                .withEmail("non-submitter@mail.com")
                .withPassword(password)
                .withCanLogin(true)
                .build();
        // A member of the reader group who is not the submitter. The submitter passes the CLARIN licence.
        licenceReader = EPersonBuilder.createEPerson(context)
                .withEmail("licence-reader@mail.com")
                .withPassword(password)
                .withCanLogin(true)
                .build();

        readerGroup = GroupBuilder.createGroup(context)
                .withName("Reader Group")
                .addMember(eperson)
                .addMember(licenceReader)
                .build();

        item = ItemBuilder.createItem(context, collection)
                .withTitle("Item with a restricted bitstream")
                .withIssueDate("2026-09-10")
                .build();
        licensedBitstream = createRestrictedBitstream(item, "licensed.txt", LICENSED_CONTENT);
        openBitstream = createRestrictedBitstream(item, "open.txt", OPEN_CONTENT);

        // Only the first bitstream gets a CLARIN licence, and one that always has to be agreed to.
        ClarinLicense clarinLicense = createClarinLicense(ClarinLicense.Confirmation.ASK_ALWAYS);
        clarinLicenseResourceMappingService.attachLicense(context, clarinLicense, licensedBitstream);

        context.restoreAuthSystemState();
        // BitstreamResourceAccessByToken reads through a Context in READ_ONLY mode, whose session does not
        // auto-flush, so anything still pending in this session would simply not be there.
        context.commit();
    }

    /**
     * The CLARIN rows are not part of the ordered builder cleanup, so the allowance and the resource
     * mapping have to go before the licence they point at.
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
     * A valid token does not replace the CLARIN licence. Until the licence is agreed, the licence-protected
     * bitstream of the request's item is refused to an anonymous caller and to a logged-in one.
     */
    @Test
    public void accessTokenStillNeedsTheClarinLicence() throws Exception {
        RequestItem request = acceptedRequestFor(licensedBitstream);

        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        String nonSubmitterToken = getAuthToken(nonSubmitter.getEmail(), password);
        getClient(nonSubmitterToken).perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * Once the licence is agreed (the {@code dtoken} the licence flow hands out), the same token serves the
     * licence-protected bitstream.
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
     * A bitstream of the request's item that carries no CLARIN licence answers a valid token with its content.
     */
    @Test
    public void accessTokenServesABitstreamWithoutAClarinLicence() throws Exception {
        RequestItem request = acceptedRequestFor(openBitstream);

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isOk())
                .andExpect(content().string(OPEN_CONTENT));

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * An "all files" token opens the bitstreams of its own item and nothing of another item. The last call
     * passes {@code @PreAuthorize} on the reader's own READ permission, so there it is the token check
     * inside the controller that refuses.
     */
    @Test
    public void allFilesAccessTokenDoesNotOpenABitstreamOfAnotherItem() throws Exception {
        Bitstream otherBitstream = createBitstreamOfAnotherItem();
        RequestItem request = acceptedAllFilesRequestFor(item);

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isOk())
                .andExpect(content().string(OPEN_CONTENT));

        getClient().perform(get(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        String nonSubmitterToken = getAuthToken(nonSubmitter.getEmail(), password);
        getClient(nonSubmitterToken).perform(get(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        String readerToken = getAuthToken(eperson.getEmail(), password);
        getClient(readerToken).perform(get(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * A single-file token opens nothing outside the item of its request, also for a reader of that file.
     */
    @Test
    public void singleFileAccessTokenDoesNotOpenABitstreamOfAnotherItem() throws Exception {
        Bitstream otherBitstream = createBitstreamOfAnotherItem();
        RequestItem request = acceptedRequestFor(otherBitstream);

        getClient().perform(get(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        String readerToken = getAuthToken(eperson.getEmail(), password);
        getClient(readerToken).perform(get(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * A single-file token opens its own bitstream and not the other bitstream of the same item, also for a
     * reader of that other bitstream. The other bitstream has no CLARIN licence, so only the token refuses it.
     */
    @Test
    public void singleFileAccessTokenOpensOnlyItsOwnBitstream() throws Exception {
        RequestItem request = acceptedRequestFor(licensedBitstream);
        agreeToTheLicence();

        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID()))
                        .param("accessToken", request.getAccess_token())
                        .param("dtoken", DOWNLOAD_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(LICENSED_CONTENT));

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        String readerToken = getAuthToken(licenceReader.getEmail(), password);
        getClient(readerToken).perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * HEAD follows the same rule as GET: the token opens bitstreams of the item of its request only.
     */
    @Test
    public void headRequestWithAccessTokenOpensOnlyBitstreamsOfTheItemOfTheRequest() throws Exception {
        Bitstream otherBitstream = createBitstreamOfAnotherItem();
        RequestItem request = acceptedAllFilesRequestFor(item);

        getClient().perform(head(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isOk());

        getClient().perform(head(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isUnauthorized());

        String readerToken = getAuthToken(eperson.getEmail(), password);
        getClient(readerToken).perform(head(String.format(CONTENT_URL, otherBitstream.getID()))
                        .param("accessToken", request.getAccess_token()))
                .andExpect(status().isForbidden());

        RequestItemBuilder.deleteRequestItem(request.getToken());
    }

    /**
     * The token of a request that is not decided yet is refused.
     */
    @Test
    public void pendingAccessTokenIsRefused() throws Exception {
        context.turnOffAuthorisationSystem();
        RequestItem pending = RequestItemBuilder.createRequestItem(context, item, openBitstream)
                .withAcceptRequest(false)
                .withAccessToken(ACCESS_TOKEN)
                .build();
        context.restoreAuthSystemState();
        context.commit();

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", pending.getAccess_token()))
                .andExpect(status().isUnauthorized());

        RequestItemBuilder.deleteRequestItem(pending.getToken());
    }

    /**
     * The token of a denied request is refused.
     */
    @Test
    public void deniedAccessTokenIsRefused() throws Exception {
        context.turnOffAuthorisationSystem();
        RequestItem denied = RequestItemBuilder.createRequestItem(context, item, openBitstream)
                .withAcceptRequest(false)
                .withDecisionDate(Instant.now())
                .withAccessToken(ACCESS_TOKEN)
                .build();
        context.restoreAuthSystemState();
        context.commit();

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", denied.getAccess_token()))
                .andExpect(status().isUnauthorized());

        RequestItemBuilder.deleteRequestItem(denied.getToken());
    }

    /**
     * An expired token and a token nobody issued are refused.
     */
    @Test
    public void expiredOrUnknownAccessTokenIsRefused() throws Exception {
        context.turnOffAuthorisationSystem();
        RequestItem expired = RequestItemBuilder.createRequestItem(context, item, openBitstream)
                .withAcceptRequest(true)
                .withDecisionDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .withAccessToken(ACCESS_TOKEN)
                .withAccessExpiry(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        context.restoreAuthSystemState();
        context.commit();

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", expired.getAccess_token()))
                .andExpect(status().isUnauthorized());

        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID()))
                        .param("accessToken", "not-a-real-token"))
                .andExpect(status().isUnauthorized());

        RequestItemBuilder.deleteRequestItem(expired.getToken());
    }

    /**
     * Nothing about a download without an access token changes. An anonymous caller is refused. A reader who
     * is not the submitter is served the bitstream that carries no CLARIN licence, and is still stopped by
     * the licence on the other one.
     */
    @Test
    public void behaviourWithoutAnAccessTokenIsUnchanged() throws Exception {
        getClient().perform(get(String.format(CONTENT_URL, openBitstream.getID())))
                .andExpect(status().isUnauthorized());
        getClient().perform(get(String.format(CONTENT_URL, licensedBitstream.getID())))
                .andExpect(status().isUnauthorized());

        String readerToken = getAuthToken(licenceReader.getEmail(), password);
        getClient(readerToken).perform(get(String.format(CONTENT_URL, openBitstream.getID())))
                .andExpect(status().isOk())
                .andExpect(content().string(OPEN_CONTENT));
        getClient(readerToken).perform(get(String.format(CONTENT_URL, licensedBitstream.getID())))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code BitstreamResourceAccessByToken} streams the bytes in its own context, so it checks the CLARIN
     * licence itself: a licence-protected bitstream is not served on an access token alone.
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
            assertTrue("Expected the CLARIN licence check to refuse, but the failure was: " + e,
                    isCausedByMissingLicenceAgreement(e));
        }
    }

    /**
     * {@code BitstreamResourceAccessByToken} still serves a bitstream without a CLARIN licence. It is a test
     * of its own because the failing case above aborts the shared transaction on its way out.
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

    private Bitstream createBitstreamOfAnotherItem() throws Exception {
        context.turnOffAuthorisationSystem();
        Item otherItem = ItemBuilder.createItem(context, collection)
                .withTitle("Another item with a restricted bitstream")
                .withIssueDate("2026-09-10")
                .build();
        Bitstream otherBitstream = createRestrictedBitstream(otherItem, "other.txt", OTHER_CONTENT);
        context.restoreAuthSystemState();
        return otherBitstream;
    }

    private Bitstream createRestrictedBitstream(Item owner, String name, String content) throws Exception {
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            return BitstreamBuilder.createBitstream(context, owner, is)
                    .withName(name)
                    .withMimeType("text/plain")
                    .withReaderGroup(readerGroup)
                    .build();
        }
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
     * Mints an accepted, unexpired request-a-copy access token for all files of an item.
     */
    private RequestItem acceptedAllFilesRequestFor(Item requested) throws Exception {
        RequestItem requestItem = RequestItemBuilder.createRequestItem(context, requested, null)
                .withAllFiles(true)
                .withAcceptRequest(true)
                .withDecisionDate(Instant.now())
                .withAccessToken(ALL_FILES_TOKEN)
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
