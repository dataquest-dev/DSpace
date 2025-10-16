/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.IsNot.not;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.healthreport.HealthReport;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.content.ReportResult;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ReportResultService;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the report-diff script.
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class ReportDiffIT extends AbstractIntegrationTestWithDatabase {

    private ReportResultService reportResultService;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Before
    public void setup() {
        reportResultService = ContentServiceFactory.getInstance().getReportResultService();
    }

    // Helper method to format dates consistently
    private String formatDate(Date date) {
        return DATE_FORMAT.format(date.toInstant());
    }

    @Test
    public void testHelpInformation() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-i" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("This script compares two health reports")));
        assertThat(handler.getErrorMessages(), empty());
    }

    @Test
    public void testShowDates() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[]}");
        reportResultService.update(context, report1);
        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();
        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-d" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("Report Dates Summary:")));
        assertThat(infoMessages, hasItem(containsString("Report Type: healthcheck")));
        assertThat(infoMessages, hasItem(containsString(formatDate(report1.getLastModified()))));
        assertThat(infoMessages, hasItem(containsString(formatDate(report2.getLastModified()))));
    }

    @Test
    public void testCompareReports() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value1\"}}]}");
        reportResultService.update(context, report1);
        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);
        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("CLARIN DSpace: Repository Health Report Diff")));
        assertThat(infoMessages, hasItem(containsString("Section 1: Executive Summary")));
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/key: \"value1\" " +
                "-> \"value2\"")));
    }

    @Test
    public void testCompareSpecificCheck() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value1\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report1.setArgs("-c: 0");
        reportResultService.update(context, report1);
        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report2.setArgs("-c: 0");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()), "-c", "0" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
         assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/key: \"value1\" " +
                 "-> \"value2\"")));
        assertThat(infoMessages, not(hasItem(containsString("Check2"))));
    }

    @Test
    public void testInvalidCheckIndex() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", "2023-01-01 00:00:00.000",
                "-t", "2023-01-02 00:00:00.000", "-c", "999" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasItem("Invalid value for check. Must be between 0 and " +
                (HealthReport.getNumberOfChecks() - 1) + ". Using all checks."));
    }

    @Test
    public void testInvalidDateFormat() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", "invalid-date", "-t", "2023-01-02 00:00:00.000" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasItem(containsString("Cannot create a Date from the input: invalid-date")));
    }

    @Test
    public void testNoReportsForDates() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", "2022-01-01 00:00:00.000",
                "-t", "2022-01-02 00:00:00.000" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("No reports found for specified dates.")));
    }

    @Test
    public void testToBeforeFrom() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", "2023-01-02 00:00:00.000",
                "-t", "2023-01-01 00:00:00.000" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> errorMessages = handler.getErrorMessages();
        assertThat(errorMessages, hasItem(containsString("The 'to' date cannot be before the 'from' date.")));
    }

    @Test
    public void testReportWithMissingValue() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue(null); // Missing value
        reportResultService.update(context, report1);
        // Force commit and flush to ensure timestamp is set
        context.commit();

        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("One of the reports has no value")));
    }

    @Test
    public void testNoDifferences() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report1);
        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);
        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("No differences found.")));
    }

    @Test
    public void testNoEnteredDate() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report1);

        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[]{"report-diff"}; // No dates provided
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("No dates specified, " +
                "using the last two dates from the database.")));
    }

    @Test
    public void testReportDiff() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value1\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report1.setArgs("-c: 0");
        reportResultService.update(context, report1);

        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report2.setArgs("-c: 0");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();


        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[]{"report-diff"}; // No dates provided
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/key: \"value1\" -> \"value2\"")));
    }

    @Test
    public void testShowDatesLimit() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[]}");
        reportResultService.update(context, report1);
        report1 = reportResultService.find(context, report1.getID());
        // Force commit and flush to ensure timestamp is set
        context.commit();

        // Wait longer to ensure different timestamps
        Thread.sleep(1000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();
        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-d", "-l", "1" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("Report Dates Summary:")));
        assertThat(infoMessages, hasItem(containsString("Report Type: healthcheck")));
        assertThat(infoMessages, not(hasItem(containsString(formatDate(report1.getLastModified())))));
        assertThat(infoMessages, hasItem(containsString(formatDate(report2.getLastModified()))));
    }

    @Test
    public void testProfessionalReportFormat() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create first report with sample health data
        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"HealthCheck\",\"report\":{" +
                "\"publishedItems\":0," +
                "\"ePersonsCount\":1," +
                "\"communitiesCount\":0," +
                "\"generated\":\"2025-08-05 09:49:20\"," +
                "\"directoryStats\":[" +
                "{\"size_bytes\":7932,\"size_display\":\"7 KB\"}," +
                "{\"size_bytes\":2411029,\"size_display\":\"2 MB\"}" +
                "]}}]}");
        reportResultService.update(context, report1);
        context.commit();

        Thread.sleep(1000);

        // Create second report with changes
        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"HealthCheck\",\"report\":{" +
                "\"publishedItems\":2," +
                "\"ePersonsCount\":1721," +
                "\"communitiesCount\":9," +
                "\"generated\":\"2025-08-05 10:04:05\"," +
                "\"directoryStats\":[" +
                "{\"size_bytes\":353581,\"size_display\":\"345 KB\"}," +
                "{\"size_bytes\":9684308,\"size_display\":\"9 MB\"}" +
                "]}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();

        // Test professional report header
        assertThat(infoMessages, hasItem(containsString("CLARIN DSpace: Repository Health Report Diff")));
        
        // Test executive summary section
        assertThat(infoMessages, hasItem(containsString("Section 1: Executive Summary")));
        assertThat(infoMessages, hasItem(containsString("Report Type: healthcheck")));
        assertThat(infoMessages, hasItem(containsString("Report Period:")));
        
        // Test key changes table
        assertThat(infoMessages, hasItem(containsString("Key Changes")));
        assertThat(infoMessages, hasItem(containsString("| Field")));
        assertThat(infoMessages, hasItem(containsString("| Difference")));
        assertThat(infoMessages, hasItem(containsString("Assetstore Size (bytes)")));
        assertThat(infoMessages, hasItem(containsString("Log Directory Size (bytes)")));
        
        // Test change types summary
        assertThat(infoMessages, hasItem(containsString("Change Types")));
        assertThat(infoMessages, hasItem(containsString("Content changes:")));
        assertThat(infoMessages, hasItem(containsString("Storage changes:")));
        
        // Test detailed change log section
        assertThat(infoMessages, hasItem(containsString("Section 2: Detailed Change Log")));
        assertThat(infoMessages, hasItem(containsString("Changes Summary")));
        assertThat(infoMessages, hasItem(containsString("Total operations:")));
        assertThat(infoMessages, hasItem(containsString("Fields modified:")));
        
        // Test that the detailed diff still includes individual changes
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/publishedItems: 0 -> 2")));
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/ePersonsCount: 1 -> 1721")));
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/communitiesCount: 0 -> 9")));
    }

    @Test
    public void testReportFormatWithNoChanges() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report1);
        context.commit();

        Thread.sleep(1000);
        
        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        
        // Should still have professional format even with no changes
        assertThat(infoMessages, hasItem(containsString("CLARIN DSpace: Repository Health Report Diff")));
        assertThat(infoMessages, hasItem(containsString("Section 1: Executive Summary")));
        assertThat(infoMessages, hasItem(containsString("No significant changes detected")));
        assertThat(infoMessages, hasItem(containsString("No differences found.")));
    }

    @Test
    public void testCalculateTimePeriod() throws Exception {
        context.turnOffAuthorisationSystem();

        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value1\"}}]}");
        reportResultService.update(context, report1);
        context.commit();

        // Wait to ensure measurable time difference
        Thread.sleep(2000);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        
        // Test that time period calculation is included
        assertThat(infoMessages, hasItem(containsString("Report Period:")));
        // Should show some time difference (seconds or minutes)
        boolean hasTimeDifference = infoMessages.stream()
            .anyMatch(msg -> msg.contains("seconds") || msg.contains("minutes"));
        assertThat("Report should show time difference", hasTimeDifference, org.hamcrest.Matchers.is(true));
    }

    @Test
    public void testEnhancedKeyChangesTable() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create first report with sample health data
        ReportResult report1 = reportResultService.create(context);
        report1.setType("healthcheck");
        report1.setValue("{\"checks\":[{\"name\":\"Info summary\",\"report\":{}}," +
                "{\"name\":\"Item summary\",\"report\":{" +
                "\"publishedItems\":10," +
                "\"ePersonsCount\":5," +
                "\"communitiesCount\":2," +
                "\"collectionsCount\":3," +
                "\"bitstreamsCount\":15," +
                "\"workspaceItemsCount\":1" +
                "}}]}");
        reportResultService.update(context, report1);
        context.commit();

        Thread.sleep(1000);

        // Create second report with changes
        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Info summary\",\"report\":{}}," +
                "{\"name\":\"Item summary\",\"report\":{" +
                "\"publishedItems\":25," +
                "\"ePersonsCount\":8," +
                "\"communitiesCount\":2," +
                "\"collectionsCount\":5," +
                "\"bitstreamsCount\":30," +
                "\"workspaceItemsCount\":2" +
                "}}]}");
        reportResultService.update(context, report2);
        context.commit();
        context.restoreAuthSystemState();

        report1 = reportResultService.find(context, report1.getID());
        report2 = reportResultService.find(context, report2.getID());

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();

        // Test enhanced table format
        assertThat(infoMessages, hasItem(containsString("Key Changes Between Reports")));
        
        // Should use actual report dates as column headers (not "Before/After")
        assertThat(infoMessages, not(hasItem(containsString("Before"))));
        assertThat(infoMessages, not(hasItem(containsString("After"))));
        
        // Should have proper table structure with Field and Difference columns
        assertThat(infoMessages, hasItem(containsString("| Field")));
        assertThat(infoMessages, hasItem(containsString("| Difference")));
        
        // Should show changes with proper formatting
        assertThat(infoMessages, hasItem(containsString("+15"))); // Published items increased
        assertThat(infoMessages, hasItem(containsString("+3")));  // EPerson count increased
        assertThat(infoMessages, hasItem(containsString("+2")));  // Collections increased
        
        // Should show field names from configuration
        assertThat(infoMessages, hasItem(containsString("Published Items")));
        assertThat(infoMessages, hasItem(containsString("Users")));
        assertThat(infoMessages, hasItem(containsString("Collections")));
        
        // Should show only changed fields (not unchanged ones like communities)
        boolean hasUnchangedCommunities = infoMessages.stream()
            .anyMatch(msg -> msg.contains("Communities") && msg.contains("| 2") && msg.contains("| 2"));
        assertThat("Unchanged fields should not appear in table", hasUnchangedCommunities, 
                   org.hamcrest.Matchers.is(false));
    }
}
