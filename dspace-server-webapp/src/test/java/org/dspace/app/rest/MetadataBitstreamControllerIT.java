/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.service.clarin.ClarinLicenseResourceMappingService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.eperson.EPerson;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.support.ErrorPageFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

public class MetadataBitstreamControllerIT extends AbstractControllerIntegrationTest {
    private static final String METADATABITSTREAM_ENDPOINT = "/api/" + ItemRest.CATEGORY + "/" + ItemRest.PLURAL_NAME;
    private static final String ALL_ZIP_PATH = "allzip";
    private static final String HANDLE_PARAM = "handleId";
    private static final String AUTHOR = "Test author name";
    private static final String BITSTREAM_CONTENT = "ThisIsSomeDummyText";
    private Collection col;

    private Item publicItem;
    private Bitstream bts;
    /** Carries a CLARIN licence that has to be agreed to; detached again in {@link #destroy()}. */
    private Bitstream licensedBitstream;

    @Autowired
    AuthorizeService authorizeService;
    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseResourceMappingService clarinLicenseResourceMappingService;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private List<Filter> requestFilters;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();

        col = CollectionBuilder.createCollection(context, parentCommunity).withName("Collection").build();

        publicItem = ItemBuilder.createItem(context, col)
                .withAuthor(AUTHOR)
                .build();

        String bitstreamContent = BITSTREAM_CONTENT;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bts = BitstreamBuilder.
                    createBitstream(context, publicItem, is)
                    .withName("Bitstream")
                    .withDescription("Description")
                    .withMimeType("application/zip")
                    .build();
        }
        context.restoreAuthSystemState();
    }

    /**
     * The resource mapping is not part of the builder cleanup and has to go before the licence it points at.
     */
    @Override
    public void destroy() throws Exception {
        if (licensedBitstream != null) {
            context.turnOffAuthorisationSystem();
            clarinLicenseResourceMappingService.detachLicenses(context, licensedBitstream);
            context.restoreAuthSystemState();
        }
        super.destroy();
    }

    @Test
    public void downloadAllZip() throws Exception {
        String token = getAuthToken(admin.getEmail(), password);
        byte[] zipBytes = getClient(token).perform(get(METADATABITSTREAM_ENDPOINT + "/" + publicItem.getID() +
                        "/" + ALL_ZIP_PATH).param(HANDLE_PARAM, publicItem.getHandle()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // A ZIP entry stores a DOS last-modified timestamp that defaults to "now" at 2-second resolution, so
        // comparing the response byte-for-byte against a locally-built ZIP intermittently failed when the server
        // and the test happened to build their entries in different time buckets. Assert the meaningful payload
        // instead: the archive must contain exactly the item's bitstream, with the expected content.
        Map<String, String> entries = new HashMap<>();
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                entries.put(entry.getName(), new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }

        // count tracked separately so a duplicate entry name can't be masked by the map
        assertEquals(1, entryCount);
        assertEquals(Set.of(bts.getName()), entries.keySet());
        assertEquals(BITSTREAM_CONTENT, entries.get(bts.getName()));
    }

    @Test
    public void downloadAllZipWithDoubleQuotesInItemName() throws Exception {
        context.turnOffAuthorisationSystem();

        // Double quotes in the name used to close the header's quoted-string early, which browsers
        // reported as ERR_RESPONSE_HEADERS_MULTIPLE_CONTENT_DISPOSITION.
        Item itemWithQuotes = ItemBuilder.createItem(context, col)
                .withTitle("Supported data for manuscript \"Thermally-induced evolution\"")
                .withAuthor(AUTHOR)
                .build();

        try (InputStream is = IOUtils.toInputStream("QuotedItemContent", CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, itemWithQuotes, is)
                    .withName("data.csv")
                    .withMimeType("text/csv")
                    .build();
        }
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get(METADATABITSTREAM_ENDPOINT + "/" + itemWithQuotes.getID() +
                        "/" + ALL_ZIP_PATH).param(HANDLE_PARAM, itemWithQuotes.getHandle()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Supported data for manuscript"
                        + " \\\"Thermally-induced evolution\\\".zip\";"
                        + " filename*=UTF-8''Supported%20data%20for%20manuscript"
                        + "%20%22Thermally-induced%20evolution%22.zip"));
    }

    @Test
    public void downloadAllZipWithNonAsciiItemName() throws Exception {
        context.turnOffAuthorisationSystem();

        Item itemWithDiacritics = ItemBuilder.createItem(context, col)
                .withTitle("Příliš žluťoučký kůň")
                .withAuthor(AUTHOR)
                .build();

        try (InputStream is = IOUtils.toInputStream("DiacriticsContent", CharEncoding.UTF_8)) {
            BitstreamBuilder.createBitstream(context, itemWithDiacritics, is)
                    .withName("file.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        context.restoreAuthSystemState();

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get(METADATABITSTREAM_ENDPOINT + "/" + itemWithDiacritics.getID() +
                        "/" + ALL_ZIP_PATH).param(HANDLE_PARAM, itemWithDiacritics.getHandle()))
                .andExpect(status().isOk())
                // fallback transliterates the diacritics away; filename* carries the real name
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Prilis zlutoucky kun.zip\";"
                        + " filename*=UTF-8''P%C5%99%C3%ADli%C5%A1%20%C5%BElu%C5%A5ou%C4%8Dk%C3%BD"
                        + "%20k%C5%AF%C5%88.zip"));
    }

    /**
     * A file under a CLARIN licence that has to be agreed to is refused before the zip headers or stream are
     * started: 401 for an anonymous caller, 403 for a logged-in user who has not agreed to the licence.
     */
    @Test
    public void downloadAllZipOfLicensedItemIsRefusedBeforeAnyByte() throws Exception {
        context.turnOffAuthorisationSystem();
        Item licensedItem = ItemBuilder.createItem(context, col)
                .withTitle("Licensed item")
                .withAuthor(AUTHOR)
                .build();
        try (InputStream is = IOUtils.toInputStream("LicensedContent", CharEncoding.UTF_8)) {
            licensedBitstream = BitstreamBuilder.createBitstream(context, licensedItem, is)
                    .withName("licensed.txt")
                    .withMimeType("text/plain")
                    .build();
        }
        // Not the submitter: the submitter always passes the CLARIN licence gate.
        EPerson reader = EPersonBuilder.createEPerson(context)
                .withEmail("allzip-reader@mail.com")
                .withPassword(password)
                .withCanLogin(true)
                .build();
        ClarinLicense clarinLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        clarinLicense.setName("Ask once licence");
        clarinLicense.setDefinition("http://example.com/licence");
        clarinLicense.setConfirmation(ClarinLicense.Confirmation.ASK_ONLY_ONCE);
        clarinLicenseService.update(context, clarinLicense);
        clarinLicenseResourceMappingService.attachLicense(context, clarinLicense, licensedBitstream);
        context.restoreAuthSystemState();
        context.commit();

        String url = METADATABITSTREAM_ENDPOINT + "/" + licensedItem.getID() + "/" + ALL_ZIP_PATH;
        ZipResponseRecorder anonymous = new ZipResponseRecorder();
        clientRecording(anonymous).perform(get(url).param(HANDLE_PARAM, licensedItem.getHandle()))
                .andExpect(status().isUnauthorized());
        assertFalse("zip headers or stream started before the licence refusal", anonymous.zipStarted);

        ZipResponseRecorder loggedIn = new ZipResponseRecorder();
        String token = getAuthToken(reader.getEmail(), password);
        clientRecording(loggedIn).perform(get(url).param(HANDLE_PARAM, licensedItem.getHandle())
                        .header(AUTHORIZATION_HEADER, AUTHORIZATION_TYPE + token))
                .andExpect(status().isForbidden());
        assertFalse("zip headers or stream started before the licence refusal", loggedIn.zipStarted);
    }

    /**
     * The client of {@link #getClient()} plus a filter next to the controller. DispatcherServlet resets the
     * body and drops Content-Disposition before the exception handler answers, so even the old controller
     * ends as a bare 401 here and the response alone cannot show whether the zip had started.
     */
    private MockMvc clientRecording(ZipResponseRecorder recorder) {
        return webAppContextSetup(webApplicationContext)
                .addFilters(new ErrorPageFilter())
                .addFilters(requestFilters.toArray(new Filter[0]))
                .addFilters(recorder)
                .apply(springSecurity())
                .build();
    }

    /** Remembers whether the response got a content type or an output stream, as the zip download does first. */
    private static final class ZipResponseRecorder implements Filter {
        private boolean zipStarted;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response) {
                @Override
                public void setContentType(String type) {
                    zipStarted = true;
                    super.setContentType(type);
                }

                @Override
                public ServletOutputStream getOutputStream() throws IOException {
                    zipStarted = true;
                    return super.getOutputStream();
                }
            });
        }
    }
}
