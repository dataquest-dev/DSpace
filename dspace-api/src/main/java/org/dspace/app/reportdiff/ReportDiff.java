/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reportdiff;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.healthreport.HealthReport;
import org.dspace.content.ReportResult;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ReportResultService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

import javax.mail.MessagingException;

/**
 * This class implements a DSpace script that compares two health reports
 * and shows the differences between them.
 * It allows users to specify a date range
 *
 * @author Milan Majchrak (dspace at dataquest.sk)
 */
public class ReportDiff extends DSpaceRunnable<ReportDiffScriptConfiguration> {
    private static final Logger log = LogManager.getLogger(ReportDiff.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private ReportResultService reportResultService = ContentServiceFactory.getInstance().getReportResultService();
    private EPersonService ePersonService;

    /**
     * `-i`: Info, show help information.
     */
    private boolean info = false;

    /**
     * `-d`: Dates, show all dates that the report was generated for a specific check type.
     */
    private boolean showDates = false;

    /**
     * `-c`: Check, perform only specific check by index (0-`getNumberOfChecks()`).
     */
    private int specificCheck = -1;

    /**
     * `-f`: From, specify the start date for the report.
     */
    private Date from = null;

    /**
     * `-t`: Till, specify the end date for the report.
     */
    private Date to = null;

    /**
     * `-e`: Email, send report to specified email address.
     */
    private String[] emails;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");


    @Override
    public ReportDiffScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager()
                .getServiceByName("report-diff", ReportDiffScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        // `-i`: Info, show help information.
        if (commandLine.hasOption('i')) {
            info = true;
            return;
        }

        // `-c`: Check, perform only specific check by index (0-`getNumberOfChecks()`).
        if (commandLine.hasOption('c')) {
            specificCheck = parseCheckOption(commandLine.getOptionValue('c'));
            if (specificCheck == -1) {
                return;
            }
        }

        // `-d`: Dates, show all dates that the report was generated for a specific check type.
        showDates = commandLine.hasOption('d');

        // `-f`: From, specify the start date for the report.
        from = parseDateOption(commandLine.getOptionValue('f'));
        // `-t`: To, specify the end date for the report.
        to = parseDateOption(commandLine.getOptionValue('t'));

        if (commandLine.hasOption('e')) {
            emails = commandLine.getOptionValues('e');
            handler.logInfo("\nReport sent to this email address: " + String.join(", ", emails));
        }
    }

    @Override
    public void internalRun() throws Exception {
        // If the user requested help information, we will display it.
        if (info) {
            printHelp();
            return;
        }

        // If the user requested to see all report dates, we will display them.
        if (showDates) {
            displayReportDates();
            return;
        }
        try (Context context = new Context()) {
            defaultDate(context);

            // If the user specified a specific check, we need to ensure that both `from` and `to` dates are set.
            if (!validateDateRange()) {
                return;
            }

            // If the user specified a specific check, we will parse the dates and compare the reports.
            compareReports(context);
        }
    }


    /**
     * Parse the check option and return the index of the check.
     * If the option is invalid, log an error and return -1.
     *
     * @param checkOption the check option value
     * @return the index of the check or -1 if invalid
     */
    private int parseCheckOption(String checkOption) {
        try {
            int index = Integer.parseInt(checkOption);
            if (index < 0 || index >= HealthReport.getNumberOfChecks()) {
                handler.logError("Invalid value for check. Must be between 0 and " +
                        (HealthReport.getNumberOfChecks() - 1) + ". Using all checks.");
                return -1;
            }
            return index;
        } catch (NumberFormatException e) {
            handler.logError("Invalid value for check. It must be a NUMBER from the displayed range.");
            return -1;
        }
    }

    /**
     * Parse the date option and return a Date object.
     * If the option is invalid, log an error and return null.
     * The date format is expected to be "yyyy-MM-dd HH:mm:ss.SSS".
     *
     * @param optionValue the date option value
     * @return the parsed Date or null if invalid
     */
    private Date parseDateOption(String optionValue) {
        if (optionValue == null) {
            return null;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(optionValue, formatter);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            handler.logError("Cannot create a Date from the input: " + optionValue);
            return null;
        }
    }

    /**
     * Validate the date range specified by `from` and `to`.
     * If the dates are invalid, log an error and return false.
     * If both dates are set, ensure that `to` is not before `from`.
     *
     * @return true if the date range is valid, false otherwise
     */
    private boolean validateDateRange() {
        if (to != null && from != null && to.before(from)) {
            handler.logError("The 'to' date cannot be before the 'from' date.");
            return false;
        }
        return true;
    }

    /**
     * Sets default values for the `from` and `to` dates if they are not already specified.
     *
     * @param context the application context used for fetching reports and logging
     */
    private void defaultDate(Context context) {
        if (Objects.nonNull(from) && Objects.nonNull(to)) {
            return;
        }
        handler.logInfo("No dates specified, using the last two dates from the database.");
        try {
            List<ReportResult> allReports = reportResultService.findAll(context);

            if (allReports == null || allReports.isEmpty()) {
                handler.logInfo("No reports found in the database.");
                return;
            }

            int size = allReports.size();

            if (Objects.isNull(to) && size > 0) {
                to = allReports.get(size - 1).getLastModified();
            }
            if (Objects.isNull(from) && size > 1) {
                from = allReports.get(size - 2).getLastModified();
            }
        } catch (SQLException e) {
            handler.logError("Database error while fetching reports for default dates: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Display all report dates for the specified check type.
     * If no reports are found, log an appropriate message.
     * Display the last 20 report dates for each type, sorted by date.
     * In the format "Report Type: <type>\n  - <date> | <args>\n",
     */
    private void displayReportDates() {
        try (Context context = new Context()) {
            context.setCurrentUser(ePersonService.find(context, getEpersonIdentifier()));
            List<ReportResult> allReports = reportResultService.findAll(context);

            Map<String, List<DateWithArgs>> reportDatesMap = new HashMap<>();
            for (ReportResult report : allReports) {
                String formattedDate = formatter.format(report.getLastModified()
                        .toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime());
                reportDatesMap.computeIfAbsent(report.getType(), k -> new ArrayList<>())
                        .add(new DateWithArgs(formattedDate, report.getArgs()));
            }

            StringBuilder sb = new StringBuilder("Report Dates Summary:\n");
            reportDatesMap.forEach((type, dates) -> {
                sb.append("Report Type: ").append(type).append("\n");
                dates.stream()
                        .sorted(Comparator.comparing(DateWithArgs::getDate).reversed())
                        .limit(20)
                        .forEach(dwa -> sb
                                .append("  - ")
                                .append(dwa.getDate())
                                .append(" | ")
                                .append(dwa.getArgs().stripLeading())
                                .append("\n"));
            });

            handler.logInfo(sb.toString());
        } catch (Exception e) {
            handler.logError("Error fetching report dates: " + e.getMessage());
        }
    }

    /**
     * Compare two reports based on the specified `from` and `to` dates.
     * If the reports are not found, log an appropriate message.
     * If the reports are found, generate a comparison report showing the differences.
     *
     * @param context the application context
     */
    private void compareReports(Context context) {
        try {
            context.setCurrentUser(ePersonService.find(context, getEpersonIdentifier()));

            ReportResult fromReport = specificCheck != -1
                    ? reportResultService.findByLastModifiedAndCheckType(context, from, specificCheck)
                    : reportResultService.findByLastModified(context, from);

            ReportResult toReport = specificCheck != -1
                    ? reportResultService.findByLastModifiedAndCheckType(context, to, specificCheck)
                    : reportResultService.findByLastModified(context, to);

            if (fromReport == null || toReport == null) {
                handler.logInfo("No reports found for specified dates.");
                return;
            }

            String reportDif = generateReportComparison(fromReport, toReport);
            // send email to email address from argument
            if (emails != null && emails.length > 0) {
                try {
                    Email e = Email.getEmail(I18nUtil.getEmailFilename(Locale.getDefault(), "report_diff"));
                    for (String recipient : emails) {
                        e.addRecipient(recipient);
                    }
                    e.addArgument(reportDif);
                    e.send();
                    handler.logInfo("Report sent to: " + String.join(", ", emails));
                } catch (IOException | MessagingException e) {
                    log.error("Error sending email:", e);
                }
            }

            handler.logInfo(reportDif);
        } catch (Exception e) {
            handler.logError("Error comparing reports: " + e.getMessage());
        }
    }

    /**
     * Generate a comparison report between two ReportResult objects.
     * The report includes the type, last modified dates, and the differences in JSON format.
     *
     * @param fromReport the "from" report
     * @param toReport   the "to" report
     * @return a string containing the comparison report
     * @throws IOException if an error occurs while generating the diff
     */
    private String generateReportComparison(ReportResult fromReport, ReportResult toReport) throws IOException {
        String fromJson = fromReport.getValue();
        String toJson = toReport.getValue();

        if (fromJson == null || toJson == null) {
            return "One of the reports has no value. Cannot compare.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nReport Diff between two reports:\n")
                .append("Type: ").append(toReport.getType()).append("\n")
                .append("From: ").append(fromReport.getLastModified()).append("\n")
                .append("To: ").append(toReport.getLastModified()).append("\n\n")
                .append("Differences:\n\n")
                .append(generateDiff(fromJson, toJson));

        return sb.toString();
    }

    @Override
    public void printHelp() {
        handler.printHelp(getScriptConfiguration().getOptions(), getScriptConfiguration().getName());
        handler.logInfo("This script compares two health reports and shows the differences between them.");
        handler.logInfo("You can specify the 'from' and 'to' dates to compare reports from specific dates.");
        handler.logInfo("If you want to see all available report dates, use the '-d' option.");
        handler.logInfo("If you want to compare a specific check, use the '-c' option with the check index, " +
                "in this case you must also specify the `from` and `to` dates.");
        handler.logInfo("If you want to send the report to a specified email address, use '-e'.");
    }


    /**
     * Compute a JSON Patch (RFC 6902) between oldJson and newJson and return a human-readable summary.
     *
     * @param oldJson  the JSON string from the “previous” report
     * @param newJson  the JSON string from the “new” report
     * @return A multiline String describing each add/replace/remove/move/copy/test operation,
     *         with special characters shown in escaped form and using " -> " for replacements.
     * @throws IOException if parsing of either JSON string fails
     */
    public static String generateDiff(String oldJson, String newJson) throws IOException {
        JsonNode oldNode = mapper.readTree(oldJson);
        JsonNode newNode = mapper.readTree(newJson);

        JsonNode patch = JsonDiff.asJson(oldNode, newNode);

        if (!patch.isArray() || patch.size() == 0) {
            return "No differences found.";
        }

        StringBuilder sb = new StringBuilder();

        for (JsonNode op : patch) {
            String operation = op.path("op").asText();
            String path = op.path("path").asText();

            switch (operation) {
                case "replace":
                    appendReplace(sb, oldNode, op, path);
                    break;
                case "add":
                    appendAdd(sb, op, path);
                    break;
                case "remove":
                    appendRemove(sb, oldNode, path);
                    break;
                case "move":
                    appendMove(sb, op, path);
                    break;
                case "copy":
                    appendCopy(sb, op, path);
                    break;
                case "test":
                    appendTest(sb, op, path);
                    break;
                default:
                    sb.append(String.format("%-7s at %s (unhandled op)%n", operation.toUpperCase(), path));
            }
        }

        return sb.toString();
    }

    /**
     * Append a replace operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param oldNode   the old JSON node
     * @param op        the JSON patch operation node
     * @param path      the path where the operation occurs
     */
    private static void appendReplace(StringBuilder sb, JsonNode oldNode, JsonNode op, String path) {
        JsonNode newValue = op.path("value");
        JsonNode oldValue = oldNode.at(path);
        sb.append(String.format(
                "- REPLACE at %s: %s -> %s%n",
                path,
                nodeToEscapedString(oldValue),
                nodeToEscapedString(newValue)
        ));
    }

    /**
     * Append an add operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param op        the JSON patch operation node
     * @param path      the path where the operation occurs
     */
    private static void appendAdd(StringBuilder sb, JsonNode op, String path) {
        JsonNode addedValue = op.path("value");
        sb.append(String.format(
                "- ADD     at %s: %s%n",
                path,
                nodeToEscapedString(addedValue)
        ));
    }

    /**
     * Append a remove operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param oldNode   the old JSON node
     * @param path      the path where the operation occurs
     */
    private static void appendRemove(StringBuilder sb, JsonNode oldNode, String path) {
        JsonNode removedValue = oldNode.at(path);
        sb.append(String.format(
                "- REMOVE  at %s: %s%n",
                path,
                nodeToEscapedString(removedValue)
        ));
    }

    /**
     * Append a move operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param op        the JSON patch operation node
     * @param path      the path where the operation occurs
     */
    private static void appendMove(StringBuilder sb, JsonNode op, String path) {
        String from = op.path("from").asText();
        sb.append(String.format(
                "- MOVE    from %s to %s%n",
                from,
                path
        ));
    }

    /**
     * Append a copy operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param op        the JSON patch operation node
     * @param path      the path where the operation occurs
     */
    private static void appendCopy(StringBuilder sb, JsonNode op, String path) {
        String from = op.path("from").asText();
        sb.append(String.format(
                "- COPY    from %s to %s%n",
                from,
                path
        ));
    }

    /**
     * Append a test operation to the StringBuilder.
     *
     * @param sb        the StringBuilder to append to
     * @param op        the JSON patch operation node
     * @param path      the path where the operation occurs
     */
    private static void appendTest(StringBuilder sb, JsonNode op, String path) {
        JsonNode testValue = op.path("value");
        sb.append(String.format(
                "- TEST    at %s: must equal %s%n",
                path,
                nodeToEscapedString(testValue)
        ));
    }

    /**
     * Return the node’s JSON-string representation, so that special characters
     * like newline (\n) appear as "\\n" inside the returned quote marks.
     */
    private static String nodeToEscapedString(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "null";
        }
        // For any primitive or object/array, toString() returns valid JSON.
        // In particular, a text node will come out as "\"some text\\n\"" (with \\n escaped).
        return node.toString();
    }
}

/**
 * A simple class to hold a date and its associated arguments.
 * Used for displaying report dates with their arguments.
 */
class DateWithArgs {
    private final String date;
    private final String args;

    public DateWithArgs(String date, String args) {
        this.date = date;
        this.args = args;
    }

    public String getDate() {
        return date;
    }

    public String getArgs() {
        return args;
    }
}
