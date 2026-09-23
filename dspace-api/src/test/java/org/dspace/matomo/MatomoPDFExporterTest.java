/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.matomo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.itextpdf.text.Image;
import org.dspace.content.Item;
import org.dspace.content.clarin.MatomoReportSubscription;
import org.dspace.content.service.ItemService;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/** The report logo has to be packaged with dspace-api, and its absence has to be
 * reported by the exporter rather than by iText. A report that fails must fail the run. */
public class MatomoPDFExporterTest {

    private MockedStatic<Email> emailFactory;
    private MockedStatic<I18nUtil> i18n;
    private final Email email = mock(Email.class);

    @Before
    public void stubEmail() {
        emailFactory = mockStatic(Email.class);
        emailFactory.when(() -> Email.getEmail(any())).thenReturn(email);
        i18n = mockStatic(I18nUtil.class);
    }

    @After
    public void releaseStaticMocks() {
        emailFactory.close();
        i18n.close();
    }

    @Test
    public void reportLogoResourceIsPackagedWithTheApi() {
        assertEquals("/org/dspace/lindat/lindat-logo.png", MatomoPDFExporter.ITEM_STATISTICS_LOGO_PATH);
        assertNotNull("logo resource missing from the dspace-api classpath",
                MatomoPDFExporter.class.getResource(MatomoPDFExporter.ITEM_STATISTICS_LOGO_PATH));
    }

    @Test
    public void logoIsRenderableWhenTheResourceIsPresent() throws Exception {
        URL logoResource = MatomoPDFExporter.class.getResource(MatomoPDFExporter.ITEM_STATISTICS_LOGO_PATH);

        Image logo = Image.getInstance(MatomoPDFExporter.requireItemStatisticsLogo(logoResource));

        assertTrue("logo has no width", logo.getWidth() > 0);
        assertTrue("logo has no height", logo.getHeight() > 0);
    }

    @Test
    public void missingLogoResourceFailsWithAComprehensibleMessage() {
        try {
            MatomoPDFExporter.requireItemStatisticsLogo(null);
            fail("expected IllegalStateException for a missing logo resource");
        } catch (IllegalStateException e) {
            assertTrue("message does not name the missing resource: " + e.getMessage(),
                    e.getMessage().contains(MatomoPDFExporter.ITEM_STATISTICS_LOGO_PATH));
        }
    }

    @Test
    public void failedReportFailsTheRunAfterTheOtherReportsAreSent() throws Exception {
        Item broken = item("123456789/1");
        Item healthy = item("123456789/2");
        List<Item> attempted = new ArrayList<>();

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                MatomoPDFExporter.sendReports(List.of(subscription(broken), subscription(healthy)), item -> {
                    attempted.add(item);
                    if (item == broken) {
                        throw new IOException("Matomo is not reachable");
                    }
                }, false));

        assertTrue("message does not give the number of failed reports: " + e.getMessage(),
                e.getMessage().startsWith("1 of 2 "));
        assertEquals(List.of(broken, healthy), attempted);
        verify(email, times(1)).send();
    }

    @Test
    public void nothingLoggedForTheMonthIsNotAFailure() throws Exception {
        MatomoPDFExporter.sendReports(List.of(subscription(item("123456789/1"))), item -> {
            throw new FileNotFoundException("no statistics for that date");
        }, false);

        verify(email, never()).send();
    }

    @Test
    public void runWithAllReportsGeneratedSucceeds() throws Exception {
        MatomoPDFExporter.sendReports(List.of(subscription(item("123456789/1")), subscription(item("123456789/2"))),
                item -> { }, false);

        verify(email, times(2)).send();
    }

    private static Item item(String handle) {
        Item item = mock(Item.class);
        when(item.getHandle()).thenReturn(handle);
        when(item.getItemService()).thenReturn(mock(ItemService.class));
        return item;
    }

    private static MatomoReportSubscription subscription(Item item) {
        MatomoReportSubscription subscription = new MatomoReportSubscription();
        subscription.setItem(item);
        subscription.setEPerson(mock(EPerson.class));
        return subscription;
    }
}
