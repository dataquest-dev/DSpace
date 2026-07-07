/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.healthreport.HealthReport;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.content.ReportResult;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ReportResultService;
import org.junit.Test;

/**
 * Integration test for the HealthReport script
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class HealthReportIT extends AbstractIntegrationTestWithDatabase {

    @Test
    public void testDefaultHealthcheckRun() throws Exception {

        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();

        String[] args = new String[] { "health-report" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);

        assertThat(testDSpaceRunnableHandler.getErrorMessages(), empty());
        assertThat(testDSpaceRunnableHandler.getWarningMessages(), empty());

        List<String> messages = testDSpaceRunnableHandler.getInfoMessages();
        assertThat(messages, hasSize(1));
        assertThat(messages, hasItem(containsString("HEALTH REPORT ")));
    }

    /**
     * Verifies that -h/--help prints help text and does not run any checks.
     * use -h instead of -i.
     */
    @Test
    public void testHelpOption() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-h" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(), empty());
        List<String> messages = handler.getInfoMessages();
        assertThat(messages, hasItem(containsString("HELP")));
        assertThat(messages, hasItem(containsString("Available checks:")));
    }

    /**
     * Verifies that multiple values for a single -c option run only the specified checks.
     * Supports multiple check selection (e.g. -c 0 3).
     */
    @Test
    public void testMultipleChecks() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        // Run only check 0 (General Information) and check 3 (Embargo check): space-separated
        String[] args = new String[] { "health-report", "-c", "0", "3" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(), empty());
        List<String> messages = handler.getInfoMessages();
        assertThat(messages, hasItem(containsString("HEALTH REPORT ")));
        assertThat(messages, hasItem(containsString("General Information")));
        assertThat(messages, hasItem(containsString("Embargo check")));
        // Item summary (check index 1) should NOT be present
        boolean hasItemSummary = messages.stream().anyMatch(m -> m.contains("Item summary:"));
        assertThat("Only selected checks should run", hasItemSummary, org.hamcrest.Matchers.is(false));
    }

    /**
     * Verifies that an out-of-range -c value causes a script error.
     */
    @Test
    public void testInvalidCheckOutOfRange() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        int maxCheck = HealthReport.getNumberOfChecks() - 1;
        String[] args = new String[] { "health-report", "-c", String.valueOf(maxCheck + 1) };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(),
                hasItem(containsString("Must be an integer from 0 to " + maxCheck)));
    }

    /**
     * Verifies that a non-integer -c value causes a script error.
     */
    @Test
    public void testInvalidCheckNonInteger() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-c", "abc" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(),
                hasItem(containsString("It has to be an integer number from 0 to")));
    }

    /**
     * Verifies that a non-positive -f value (zero) causes a script error.
     * Validate -f must be positive integer (greater than 0).
     */
    @Test
    public void testInvalidForDaysZero() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-f", "0" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(),
                hasItem(containsString("Must be a positive integer (greater than 0)")));
    }

    /**
     * Verifies that a non-integer -f value causes a script error.
     * Validate -f must be integer.
     */
    @Test
    public void testInvalidForDaysNonInteger() throws Exception {
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-f", "notanumber" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(),
                hasItem(containsString("Must be a positive integer")));
    }

    /**
     * Verifies that -r/--report saves report output to the specified file.
     * -o/--output renamed to -r/--report.
     */
    @Test
    public void testReportFileSaved() throws Exception {
        File tempFile = File.createTempFile("health-report-test-", ".txt");
        tempFile.deleteOnExit();

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-r", tempFile.getAbsolutePath() };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        assertThat(handler.getErrorMessages(), empty());
        assertThat("Report file must exist after -r option", tempFile.exists(), org.hamcrest.Matchers.is(true));
        String content = Files.readString(tempFile.toPath());
        assertThat("Report file must contain health report header", content, containsString("HEALTH REPORT "));
    }

    @Test
    public void testStoredArgsContainAllCheckOptions() throws Exception {
        ReportResultService reportResultService = ContentServiceFactory.getInstance().getReportResultService();

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        String[] args = new String[] { "health-report", "-c", "2", "-c", "3" };
        ScriptLauncher.handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl);

        context.reloadEntity(eperson);
        List<ReportResult> allReports = reportResultService.findAll(context);
        // findAll() does not guarantee ordering; sort by lastModified so the newest report is last.
        allReports.sort(java.util.Comparator.comparing(ReportResult::getLastModified));
        ReportResult latest = allReports.get(allReports.size() - 1);

        assertThat(handler.getErrorMessages(), empty());
        assertThat(latest.getArgs(), containsString("-c: 2"));
        assertThat(latest.getArgs(), containsString("-c: 3"));
    }
}
