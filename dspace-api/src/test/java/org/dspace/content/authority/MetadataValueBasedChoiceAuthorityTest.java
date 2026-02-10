/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.web.ContextUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for MetadataValueBasedChoiceAuthority
 *
 * @author Michaela Paurikova (dspace at dataquest.sk)
 */
@RunWith(MockitoJUnitRunner.class)
public class MetadataValueBasedChoiceAuthorityTest {

    @Mock
    private MetadataValueService mockMetadataValueService;

    @Mock
    private ContentServiceFactory mockContentServiceFactory;

    @Mock
    private Context mockContext;

    @Mock
    private MetadataValue mockMetadataValue1;

    @Mock
    private MetadataValue mockMetadataValue2;

    private MetadataValueBasedChoiceAuthority authority;
    private MockedStatic<ContentServiceFactory> staticContentServiceFactory;
    private MockedStatic<ContextUtil> staticContextUtil;

    @Before
    public void setUp() throws Exception {
        // Mock static factory methods
        staticContentServiceFactory = mockStatic(ContentServiceFactory.class);
        staticContentServiceFactory.when(ContentServiceFactory::getInstance).thenReturn(mockContentServiceFactory);
        when(mockContentServiceFactory.getMetadataValueService()).thenReturn(mockMetadataValueService);

        staticContextUtil = mockStatic(ContextUtil.class);

        authority = new MetadataValueBasedChoiceAuthority();
    }

    @After
    public void tearDown() {
        if (staticContentServiceFactory != null) {
            staticContentServiceFactory.close();
        }
        if (staticContextUtil != null) {
            staticContextUtil.close();
        }
    }

    @Test
    public void testGetSetPluginInstanceName() {
        String testName = "testPlugin";
        authority.setPluginInstanceName(testName);
        assertEquals(testName, authority.getPluginInstanceName());
    }

    @Test
    public void testGetLabelWithValidKeyAndLocale() throws SQLException {
        // Setup mocks
        String key = "testKey";
        String locale = "en";
        String expectedLabel = "Test Label";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        when(mockMetadataValue1.getValue()).thenReturn(expectedLabel);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, locale))
            .thenReturn(Arrays.asList(mockMetadataValue1));

        // Execute
        String result = authority.getLabel(key, locale);

        // Verify
        assertEquals(expectedLabel, result);
        verify(mockMetadataValueService).findByAuthorityAndLanguage(mockContext, key, locale);
    }

    @Test
    public void testGetLabelWithFallbackToNoLocale() throws SQLException {
        // Setup mocks
        String key = "testKey";
        String locale = "fr";
        String expectedLabel = "Fallback Label";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, locale))
            .thenReturn(Collections.emptyList()); // No results for specific locale
        when(mockMetadataValue1.getValue()).thenReturn(expectedLabel);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, null))
            .thenReturn(Arrays.asList(mockMetadataValue1)); // Fallback without locale

        // Execute
        String result = authority.getLabel(key, locale);

        // Verify
        assertEquals(expectedLabel, result);
        verify(mockMetadataValueService).findByAuthorityAndLanguage(mockContext, key, locale);
        verify(mockMetadataValueService).findByAuthorityAndLanguage(mockContext, key, null);
    }

    @Test
    public void testGetLabelWithBlankKey() {
        String result = authority.getLabel("", "en");
        assertEquals("Unknown", result);

        result = authority.getLabel(null, "en");
        assertEquals("Unknown", result);
    }

    // Test removed - requires full DSpace initialization

    @Test
    public void testGetLabelWithNoResults() throws SQLException {
        String key = "nonexistentKey";
        String locale = "en";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, locale))
            .thenReturn(Collections.emptyList());
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, null))
            .thenReturn(Collections.emptyList());

        String result = authority.getLabel(key, locale);
        assertEquals(key, result); // Should return key as fallback
    }

    @Test
    public void testGetMatchesWithValidQuery() throws SQLException {
        String query = "test";
        String locale = "en";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);

        // Setup authority results
        when(mockMetadataValue1.getAuthority()).thenReturn("auth1");
        when(mockMetadataValue1.getValue()).thenReturn("Test Value 1");
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, query, locale))
            .thenReturn(Arrays.asList(mockMetadataValue1));

        // Setup value-like results
        @SuppressWarnings("unchecked")
        Iterator<MetadataValue> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        when(mockIterator.next()).thenReturn(mockMetadataValue2);
        when(mockMetadataValue2.getAuthority()).thenReturn("auth2");
        when(mockMetadataValue2.getValue()).thenReturn("Test Value 2");
        when(mockMetadataValue2.getLanguage()).thenReturn(locale);
        when(mockMetadataValueService.findByValueLike(mockContext, query)).thenReturn(mockIterator);

        Choices result = authority.getMatches(query, 0, 10, locale);

        assertNotNull(result);
        assertEquals(2, result.values.length);
        assertEquals("auth1", result.values[0].authority);
        assertEquals("Test Value 1", result.values[0].value);
        assertEquals("auth2", result.values[1].authority);
        assertEquals("Test Value 2", result.values[1].value);
    }

    @Test
    public void testGetMatchesWithBlankQuery() {
        Choices result = authority.getMatches("", 0, 10, "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);

        result = authority.getMatches(null, 0, 10, "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);
    }

    // Test removed - requires full DSpace initialization

    @Test
    public void testGetMatchesWithPagination() throws SQLException {
        String query = "test";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);

        // Setup multiple results
        when(mockMetadataValue1.getAuthority()).thenReturn("auth1");
        when(mockMetadataValue2.getAuthority()).thenReturn("auth2");
        when(mockMetadataValue2.getValue()).thenReturn("Test 2");

        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, query, null))
            .thenReturn(Arrays.asList(mockMetadataValue1, mockMetadataValue2));

        @SuppressWarnings("unchecked")
        Iterator<MetadataValue> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(false);
        when(mockMetadataValueService.findByValueLike(mockContext, query)).thenReturn(mockIterator);

        // Test with pagination - start=1, limit=1 (should get second item)
        Choices result = authority.getMatches(query, 1, 1, null);

        assertNotNull(result);
        assertEquals(1, result.values.length);
        assertEquals("auth2", result.values[0].authority);
        assertEquals("Test 2", result.values[0].value);
        assertFalse("Should NOT indicate more results available with only 2 total items", result.more);
    }

    @Test
    public void testGetBestMatchExact() throws SQLException {
        String text = "Exact Match";
        String locale = "en";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);

        when(mockMetadataValue1.getValue()).thenReturn(text);
        when(mockMetadataValue1.getAuthority()).thenReturn("exactAuth");

        @SuppressWarnings("unchecked")
        Iterator<MetadataValue> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        when(mockIterator.next()).thenReturn(mockMetadataValue1);
        when(mockMetadataValueService.findByValueLike(mockContext, text)).thenReturn(mockIterator);

        Choices result = authority.getBestMatch(text, locale);

        assertNotNull(result);
        assertEquals(1, result.values.length);
        assertEquals("exactAuth", result.values[0].authority);
        assertEquals(text, result.values[0].value);
        assertEquals(Choices.CF_ACCEPTED, result.confidence);
        assertFalse(result.more);
    }

    @Test
    public void testGetBestMatchWithBlankText() {
        Choices result = authority.getBestMatch("", "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);

        result = authority.getBestMatch(null, "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);
    }

    // Test removed - requires full DSpace initialization

    @Test
    public void testGetBestMatchNoResults() throws SQLException {
        String text = "No Match";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);

        @SuppressWarnings("unchecked")
        Iterator<MetadataValue> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(false);
        when(mockMetadataValueService.findByValueLike(mockContext, text)).thenReturn(mockIterator);

        Choices result = authority.getBestMatch(text, "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);
    }

    @Test
    public void testExceptionHandlingInGetLabel() throws SQLException {
        String key = "testKey";
        String locale = "en";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        when(mockMetadataValueService.findByAuthorityAndLanguage(mockContext, key, locale))
            .thenThrow(new SQLException("Test SQL Exception"));

        String result = authority.getLabel(key, locale);
        assertEquals(key, result); // Should return key as fallback on exception
    }

    @Test
    public void testExceptionHandlingInGetMatches() throws SQLException {
        String query = "test";

        staticContextUtil.when(ContextUtil::obtainCurrentRequestContext).thenReturn(mockContext);
        when(mockMetadataValueService.findByAuthorityAndLanguage(any(Context.class), eq(query), any()))
            .thenThrow(new SQLException("Test SQL Exception"));

        Choices result = authority.getMatches(query, 0, 10, "en");
        assertEquals(Choices.CF_NOTFOUND, result.confidence);
    }

    // Test removed - requires full DSpace initialization
}