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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;

import com.itextpdf.text.Image;
import org.junit.Test;

/** The report logo has to be packaged with dspace-api, and its absence has to be
 * reported by the exporter rather than by iText. */
public class MatomoPDFExporterTest {

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
}
