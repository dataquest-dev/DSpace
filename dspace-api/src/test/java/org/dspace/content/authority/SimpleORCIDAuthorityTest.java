/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;

import org.dspace.content.MetadataValue;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.external.CachingOrcidRestConnector;
import org.dspace.external.provider.orcid.xml.ExpandedSearchConverter;
import org.dspace.web.ContextUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for SimpleORCIDAuthority, verifying ORCID-first label resolution
 * with local DB fallback and ORCID-based search delegation.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
@RunWith(MockitoJUnitRunner.class)
public class SimpleORCIDAuthorityTest {

    private static final String ORCID_KEY = "0000-0002-1825-0097";
    private static final String AUTHOR_NAME = "Doe, John";
    private static final String LOCALE_EN = "en";
    private static final String LOCALE_FR = "fr";
    private static final int MAX_RESULTS = 100;

    @Mock
    private CachingOrcidRestConnector mockOrcidConnector;
    @Mock
    private MetadataValueService mockMetadataValueService;
    @Mock
    private Context mockContext;

    private SimpleORCIDAuthority authority;
    private MockedStatic<ContextUtil> staticContextUtil;

    @Before
    public void setUp() {
        staticContextUtil = mockStatic(ContextUtil.class);
        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        authority = new SimpleORCIDAuthority(mockOrcidConnector, mockMetadataValueService);
    }

    @After
    public void tearDown() {
        if (staticContextUtil != null) {
            staticContextUtil.close();
        }
    }

    // ========== getLabel tests ==========

    @Test
    public void testGetLabelReturnsOrcidResultWhenAvailable() {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(AUTHOR_NAME);

        assertEquals(AUTHOR_NAME, authority.getLabel(ORCID_KEY, LOCALE_EN));
        verifyNoInteractions(mockMetadataValueService);
    }

    @Test
    public void testGetLabelFallsBackToLocalDbWhenOrcidReturnsNull() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        MetadataValue mv = createMockMetadataValue(AUTHOR_NAME);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, ORCID_KEY, LOCALE_EN))
            .thenReturn(Arrays.asList(mv));

        assertEquals(AUTHOR_NAME, authority.getLabel(ORCID_KEY, LOCALE_EN));
    }

    @Test
    public void testGetLabelFallsBackToAnyLocaleWhenSpecificLocaleNotFound() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, ORCID_KEY, LOCALE_FR))
            .thenReturn(Collections.emptyList());
        MetadataValue mv = createMockMetadataValue(AUTHOR_NAME);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, ORCID_KEY, null))
            .thenReturn(Arrays.asList(mv));

        assertEquals(AUTHOR_NAME, authority.getLabel(ORCID_KEY, LOCALE_FR));
    }

    @Test
    public void testGetLabelReturnsKeyWhenNothingFound() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        when(mockMetadataValueService.findByAuthorityAndLanguage(any(), eq(ORCID_KEY), any()))
            .thenReturn(Collections.emptyList());

        assertEquals(ORCID_KEY, authority.getLabel(ORCID_KEY, LOCALE_EN));
    }

    @Test
    public void testGetLabelReturnsKeyForBlankInput() {
        assertEquals("", authority.getLabel("", LOCALE_EN));
        verifyNoInteractions(mockOrcidConnector, mockMetadataValueService);
    }

    @Test
    public void testGetLabelReturnsKeyForNullInput() {
        assertEquals(null, authority.getLabel(null, LOCALE_EN));
        verifyNoInteractions(mockOrcidConnector, mockMetadataValueService);
    }

    @Test
    public void testGetLabelWithNullLocaleQueriesOnce() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, ORCID_KEY, null))
            .thenReturn(Collections.emptyList());

        assertEquals(ORCID_KEY, authority.getLabel(ORCID_KEY, null));
        verify(mockMetadataValueService, times(1))
            .findByAuthorityAndLanguage(mockContext, ORCID_KEY, null);
    }

    @Test
    public void testGetLabelWithBlankLocaleNormalizesToNull() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        MetadataValue mv = createMockMetadataValue(AUTHOR_NAME);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, ORCID_KEY, null))
            .thenReturn(Arrays.asList(mv));

        assertEquals(AUTHOR_NAME, authority.getLabel(ORCID_KEY, "  "));
        verify(mockMetadataValueService, never())
            .findByAuthorityAndLanguage(any(), any(), eq("  "));
    }

    @Test
    public void testGetLabelReturnsKeyOnDatabaseError() throws SQLException {
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);
        when(mockMetadataValueService.findByAuthorityAndLanguage(any(), eq(ORCID_KEY), any()))
            .thenThrow(new SQLException("DB error"));

        assertEquals(ORCID_KEY, authority.getLabel(ORCID_KEY, LOCALE_EN));
    }

    @Test
    public void testGetLabelReturnsKeyWhenNoContextAndOrcidNull() {
        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(null);
        when(mockOrcidConnector.getLabel(ORCID_KEY)).thenReturn(null);

        SimpleORCIDAuthority spyAuthority = spy(authority);
        doReturn(null).when(spyAuthority).createReadOnlyContext();

        String result = spyAuthority.getLabel(ORCID_KEY, LOCALE_EN);

        assertEquals(ORCID_KEY, result);
        verify(mockOrcidConnector).getLabel(ORCID_KEY);
    }

    // ========== getMatches tests ==========

    @Test
    public void testGetMatchesDelegatesToOrcid() {
        when(mockOrcidConnector.search("test query", 0, MAX_RESULTS))
            .thenReturn(ExpandedSearchConverter.ERROR);

        Choices choices = authority.getMatches("test query", 0, MAX_RESULTS, LOCALE_EN);

        verify(mockOrcidConnector).search("test query", 0, MAX_RESULTS);
        verifyNoInteractions(mockMetadataValueService);
        assertEquals(Choices.CF_FAILED, choices.confidence);
    }

    @Test
    public void testGetMatchesReturnsEmptyForBlankQuery() {
        Choices choices = authority.getMatches("", 0, 10, LOCALE_EN);
        assertTrue(choices.values.length == 0);
        verifyNoInteractions(mockOrcidConnector);
    }

    @Test
    public void testGetMatchesReturnsEmptyForNullQuery() {
        Choices choices = authority.getMatches(null, 0, 10, LOCALE_EN);
        assertTrue(choices.values.length == 0);
        verifyNoInteractions(mockOrcidConnector);
    }

    @Test
    public void testGetMatchesClampsNegativeStart() {
        when(mockOrcidConnector.search("test", 0, MAX_RESULTS))
            .thenReturn(ExpandedSearchConverter.ERROR);

        authority.getMatches("test", -5, MAX_RESULTS, LOCALE_EN);

        verify(mockOrcidConnector).search("test", 0, MAX_RESULTS);
    }

    @Test
    public void testGetMatchesClampsExcessiveLimit() {
        when(mockOrcidConnector.search("test", 0, MAX_RESULTS))
            .thenReturn(ExpandedSearchConverter.ERROR);

        authority.getMatches("test", 0, 500, LOCALE_EN);

        verify(mockOrcidConnector).search("test", 0, MAX_RESULTS);
    }

    // ========== getBestMatch tests ==========

    @Test
    public void testGetBestMatchDelegatesToOrcid() {
        when(mockOrcidConnector.search("John Doe", 0, 1))
            .thenReturn(ExpandedSearchConverter.ERROR);

        authority.getBestMatch("John Doe", LOCALE_EN);

        verify(mockOrcidConnector).search("John Doe", 0, 1);
        verifyNoInteractions(mockMetadataValueService);
    }

    // ========== helpers ==========

    private MetadataValue createMockMetadataValue(String value) {
        MetadataValue mv = mock(MetadataValue.class);
        when(mv.getValue()).thenReturn(value);
        return mv;
    }
}
