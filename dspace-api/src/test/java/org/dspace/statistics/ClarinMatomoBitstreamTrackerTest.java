/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;

import org.dspace.AbstractDSpaceTest;
import org.dspace.app.statistics.clarin.ClarinMatomoBitstreamTracker;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.matomo.java.tracking.MatomoRequest;
import org.matomo.java.tracking.MatomoTracker;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ClarinMatomoBitstreamTrackerTest extends AbstractDSpaceTest {

    private static final String HANDLE = "123456789/1";
    private static final String BASE_URL = "http://example.com";
    private static final String LOCALHOST_URL = "http://localhost:4000";

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private MatomoTracker matomoTracker;

    @Mock
    private HttpServletRequest request;

    @Mock
    private ClarinItemService clarinItemService;

    @Mock
    private ItemService itemService;

    @Mock
    private Bitstream bitstream;

    @InjectMocks
    private ClarinMatomoBitstreamTracker clarinMatomoBitstreamTracker;

    Context context;

    @Before
    public void setUp() {
        context = new Context();
    }

    @Test
    public void testTrackBitstreamDownload() throws SQLException {
        UUID bitstreamId = UUID.randomUUID();
        mockRequest("/bitstreams/" + bitstreamId + "/download");
        mockBitstreamAndItem(bitstreamId);
        when(matomoTracker.sendRequestAsync(any(MatomoRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        clarinMatomoBitstreamTracker.trackBitstreamDownload(context, request, bitstream);

        String expectedUrl = LOCALHOST_URL + "/bitstream/handle/" + HANDLE + "/" + bitstreamId;
        verifyMatomoRequest(expectedUrl);
    }

    @Test
    public void testTrackBitstreamDownloadWrongUrl() throws SQLException {
        context = new Context();
        UUID bitstreamId = UUID.randomUUID();
        mockRequest("/bitstreams/NOT_EXISTING_UUID/download");
        mockBitstreamAndItem(bitstreamId);
        when(matomoTracker.sendRequestAsync(any(MatomoRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        clarinMatomoBitstreamTracker.trackBitstreamDownload(context, request, bitstream);

        String expectedUrl = BASE_URL + "/bitstreams/NOT_EXISTING_UUID/download";
        verifyMatomoRequest(expectedUrl);
    }

    private void mockRequest(String requestURI) {
        when(request.getRequestURI()).thenReturn(requestURI);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("example.com");
        when(request.getServerPort()).thenReturn(80);
        when(request.getHeader("Range")).thenReturn(null);
    }

    private void mockBitstreamAndItem(UUID bitstreamId) throws SQLException {
        when(bitstream.getID()).thenReturn(bitstreamId);
        Item item = mock(Item.class);
        when(item.getHandle()).thenReturn(HANDLE);
        when(clarinItemService.findByBitstreamUUID(context, bitstreamId)).thenReturn(Collections.singletonList(item));

        MetadataValue metadataValue = mock(MetadataValue.class);
        when(metadataValue.getValue()).thenReturn("http://hdl.handle.net/" + HANDLE);
        List<MetadataValue> metadataValues = Collections.singletonList(metadataValue);
        when(itemService.getMetadata(item, "dc", "identifier", "uri",
                Item.ANY, false)).thenReturn(metadataValues);
    }

    private void verifyMatomoRequest(String expectedUrl) {
        ArgumentCaptor<MatomoRequest> captor = ArgumentCaptor.forClass(MatomoRequest.class);
        verify(matomoTracker, times(1)).sendRequestAsync(captor.capture());

        MatomoRequest sentRequest = captor.getValue();
        assertNotNull(sentRequest);
        assertEquals("Bitstream Download / Single File", sentRequest.getActionName());
        assertEquals("Action URL should match the request URL", expectedUrl, sentRequest.getDownloadUrl());
    }
}