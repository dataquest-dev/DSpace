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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[]}");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();

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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}}]}");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "report-diff", "-f", formatDate(report1.getLastModified()),
                "-t", formatDate(report2.getLastModified()) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("Report Diff between two reports:")));
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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report2.setArgs("-c: 0");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();

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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[]}");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();

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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();

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
        report1.setLastModified(new Date(1000));
        reportResultService.update(context, report1);


        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value\"}}]}");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

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
        report1.setLastModified(new Date(1000));
        report1.setArgs("-c: 0");
        reportResultService.update(context, report1);

        ReportResult report2 = reportResultService.create(context);
        report2.setType("healthcheck");
        report2.setValue("{\"checks\":[{\"name\":\"Check1\",\"report\":{\"key\":\"value2\"}},{\"name\":\"Check2\"" +
                ",\"report\":{\"key\":\"other\"}}]}");
        report2.setArgs("-c: 0");
        report2.setLastModified(new Date(2000));
        reportResultService.update(context, report2);

        context.restoreAuthSystemState();


        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[]{"report-diff"}; // No dates provided
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        List<String> infoMessages = handler.getInfoMessages();
        assertThat(infoMessages, hasItem(containsString("REPLACE at /checks/0/report/key: \"value1\" -> \"value2\"")));
    }
}
