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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dspace.AbstractDSpaceTest;
import org.dspace.app.rest.exception.DSpaceApiExceptionControllerAdvice;
import org.dspace.app.rest.exception.DSpaceBadRequestException;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.ScopeResolver;
import org.dspace.app.util.XMLUtils;
import org.dspace.app.util.service.OpenSearchService;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchUtils;
import org.dspace.discovery.configuration.DiscoveryConfiguration;
import org.dspace.discovery.configuration.DiscoveryConfigurationService;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.w3c.dom.Document;

/**
 * Verifies OpenSearch format validation happens before any response body is written.
 * The legacy feed-label helper requires a configured DSpace kernel.
 */
public class OpenSearchControllerTest extends AbstractDSpaceTest {
    private OpenSearchController controller;
    private OpenSearchService openSearchService;
    private DiscoveryConfigurationService configurationService;
    private SearchService searchService;
    private ScopeResolver scopeResolver;
    private Context context;
    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        controller = new OpenSearchController();
        openSearchService = mock(OpenSearchService.class);
        configurationService = mock(DiscoveryConfigurationService.class);
        searchService = mock(SearchService.class);
        scopeResolver = mock(ScopeResolver.class);
        context = mock(Context.class);
        request = new MockHttpServletRequest();
        controller.setOpenSearchService(openSearchService);
        ReflectionTestUtils.setField(controller, "searchIndices", List.of("any"));
        ReflectionTestUtils.setField(controller, "searchConfigurationService", configurationService);
        ReflectionTestUtils.setField(controller, "scopeResolver", scopeResolver);
        when(openSearchService.isEnabled()).thenReturn(true);
        when(openSearchService.getFormats()).thenReturn(List.of("html", "atom", "rss"));
        when(openSearchService.getMaxNumOfItemsPerRequest()).thenReturn(100);
    }

    @Test
    public void unsupportedFormatWithInvalidSortDoesNotWriteBeforeThrowing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class)) {
            contexts.when(() -> ContextUtil.obtainContext(request)).thenReturn(context);
            assertThrows(DSpaceBadRequestException.class, () -> controller.search(request, response, "query", 0, 10,
                "unsupported", "dc.title", "invalid", null));
            contexts.verifyNoInteractions();
        }
        assertEquals("", response.getContentAsString());
        assertFalse(response.isCommitted());
        verifyNoInteractions(configurationService, scopeResolver, searchService);
    }

    @Test
    public void unsupportedFormatWithDefaultsDoesNotSearch() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class)) {
            contexts.when(() -> ContextUtil.obtainContext(request)).thenReturn(context);
            assertThrows(DSpaceBadRequestException.class, () -> controller.search(request, response, null, null, null,
                "unsupported", null, null, null));
            contexts.verifyNoInteractions();
        }
        assertEquals("", response.getContentAsString());
        verifyNoInteractions(configurationService, scopeResolver, searchService);
    }

    @Test
    public void configuredHtmlFormatIsOnlyAdvertisedForTheUi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class)) {
            assertThrows(DSpaceBadRequestException.class, () -> controller.search(request, response, "query", 0, 10,
                "html", null, null, null));
            contexts.verifyNoInteractions();
        }
        assertEquals("", response.getContentAsString());
        verifyNoInteractions(configurationService, scopeResolver, searchService);
    }

    @Test
    public void atomFormatStillRenders() throws Exception {
        assertSupportedFormat("atom", "application/atom+xml");
    }

    @Test
    public void rssFormatStillRenders() throws Exception {
        assertSupportedFormat("rss", "application/rss+xml");
    }

    @Test
    public void omittedFormatStillDefaultsToAtom() throws Exception {
        assertSupportedFormat(null, "application/atom+xml");
    }

    @Test
    public void emptyFormatStillDefaultsToAtom() throws Exception {
        assertSupportedFormat("", "application/atom+xml");
    }

    @Test
    public void unsupportedFormatMapsToBadRequest() throws Exception {
        DSpaceApiExceptionControllerAdvice advice = new DSpaceApiExceptionControllerAdvice();
        ConfigurationService loggingConfiguration = mock(ConfigurationService.class);
        when(loggingConfiguration.getArrayProperty(any(), any(String[].class))).thenReturn(new String[] { "422" });
        ReflectionTestUtils.setField(advice, "configurationService", loggingConfiguration);
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice)
            .build()
            .perform(get("/opensearch/search")
                .param("format", "unsupported").param("sort_direction", "invalid").param("f.title", "query,equals"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(configurationService, scopeResolver, searchService);
    }

    @Test
    public void disabledServiceKeepsNotFoundResponse() throws Exception {
        when(openSearchService.isEnabled()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class)) {
            controller.search(request, response, null, null, null, "unsupported", null, null, null);
            contexts.verifyNoInteractions();
        }
        assertEquals(404, response.getStatus());
        assertEquals("OpenSearch Service is disabled", response.getContentAsString());
        verifyNoInteractions(configurationService, scopeResolver, searchService);
    }

    @Test
    public void overlappingSearchesKeepTheirRequestContexts() throws Exception {
        CountDownLatch firstSearchWaiting = new CountDownLatch(1);
        CountDownLatch secondSearchFinished = new CountDownLatch(1);
        AtomicBoolean firstConfigurationLookup = new AtomicBoolean(true);
        Context secondContext = mock(Context.class);
        Document results = XMLUtils.getDocumentBuilderFactory().newDocumentBuilder().newDocument();
        results.appendChild(results.createElement("results"));
        IndexableObject scope = mock(IndexableObject.class);
        when(scopeResolver.resolveScope(any(Context.class), eq("scope"))).thenReturn(scope);
        when(configurationService.getDiscoveryConfiguration(any(Context.class), eq(scope))).thenAnswer(invocation -> {
            if (firstConfigurationLookup.getAndSet(false)) {
                firstSearchWaiting.countDown();
                assertTrue("Second request did not complete", secondSearchFinished.await(10, TimeUnit.SECONDS));
            }
            return new DiscoveryConfiguration();
        });
        when(searchService.search(any(Context.class), eq(scope), any(DiscoverQuery.class)))
            .thenReturn(new DiscoverResult());
        when(openSearchService.getResultsDoc(any(Context.class), eq("atom"), any(), anyInt(), anyInt(), anyInt(),
            eq(scope), any(), any())).thenReturn(results);
        when(openSearchService.getContentType("atom")).thenReturn("application/atom+xml");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> renderWithContext(context, "first"));
            assertTrue("First request did not reach configuration lookup",
                firstSearchWaiting.await(10, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                try {
                    renderWithContext(secondContext, "second");
                } finally {
                    secondSearchFinished.countDown();
                }
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            secondSearchFinished.countDown();
            executor.shutdownNow();
        }
        verify(searchService).search(eq(context), eq(scope), argThat(query -> "first".equals(query.getQuery())));
        verify(searchService).search(eq(secondContext), eq(scope), argThat(query -> "second".equals(query.getQuery())));
    }

    private void renderWithContext(Context requestContext, String query) {
        MockHttpServletRequest searchRequest = new MockHttpServletRequest();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class);
             MockedStatic<SearchUtils> searches = mockStatic(SearchUtils.class)) {
            contexts.when(() -> ContextUtil.obtainContext(searchRequest)).thenReturn(requestContext);
            searches.when(SearchUtils::getSearchService).thenReturn(searchService);
            controller.search(searchRequest, new MockHttpServletResponse(), query, 0, 10, "atom",
                null, null, "scope");
        } catch (Exception e) {
            throw new AssertionError("Search failed", e);
        }
    }

    private void assertSupportedFormat(String format, String contentType) throws Exception {
        String feedFormat = format == null || format.isEmpty() ? "atom" : format;
        MockHttpServletResponse response = new MockHttpServletResponse();
        Document results = XMLUtils.getDocumentBuilderFactory().newDocumentBuilder().newDocument();
        results.appendChild(results.createElement("results"));
        when(searchService.search(eq(context), isNull(), any(DiscoverQuery.class))).thenReturn(new DiscoverResult());
        when(openSearchService.getResultsDoc(eq(context), eq(feedFormat), eq("query"), anyInt(), anyInt(), anyInt(),
            isNull(), any(), any())).thenReturn(results);
        when(openSearchService.getContentType(feedFormat)).thenReturn(contentType);
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class);
             MockedStatic<SearchUtils> searches = mockStatic(SearchUtils.class)) {
            contexts.when(() -> ContextUtil.obtainContext(request)).thenReturn(context);
            searches.when(SearchUtils::getSearchService).thenReturn(searchService);
            controller.search(request, response, "query", 0, 10, format, null, null, null);
        }
        assertEquals(200, response.getStatus());
        assertEquals(contentType, response.getContentType());
        assertTrue(response.getContentAsString().contains("<results"));
    }
}
