/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.reportdiff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.flipkart.zjsonpatch.JsonDiff;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.builder.Diff;
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
import org.dspace.health.Check;
import org.dspace.health.Report;
import org.dspace.health.ReportInfo;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.commons.io.IOUtils.toInputStream;

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
            String checkOption = commandLine.getOptionValue('c');
            try {
                specificCheck = Integer.parseInt(checkOption);
                if (specificCheck < 0 || specificCheck >= HealthReport.getNumberOfChecks()) {
                    specificCheck = -1;
                }
            } catch (NumberFormatException e) {
                log.info("Invalid value for check. It has to be a number from the displayed range.");
                return;
            }
        }

        // `-d`: Dates, show all dates that the report was generated for a specific check type.
        if (commandLine.hasOption('d')) {
            showDates = true;
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        // `-f`: From, specify the start date for the report.
        if (commandLine.hasOption('f')) {
            String fromOption = commandLine.getOptionValue('f');
            try {
                LocalDateTime ldt = LocalDateTime.parse(fromOption, formatter);
                ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
                from = Date.from(zdt.toInstant());
            } catch (Exception e) {
                log.error("Cannot create a Date from the input: {}", fromOption, e);
                return;
            }
        }

        // `-t`: Till, specify the end date for the report.
        if (commandLine.hasOption('t')) {
            String toOption = commandLine.getOptionValue('t');
            try {
                LocalDateTime ldt = LocalDateTime.parse(toOption, formatter);
                ZonedDateTime zdt = ldt.atZone(ZoneId.systemDefault());
                to = Date.from(zdt.toInstant());
            } catch (Exception e) {
                log.error("Cannot create a Date from the input: {}", toOption, e);
            }
        }
    }

    @Override
    public void internalRun() throws Exception {
        if (info) {
            printHelp();
            return;
        }
        Context context = new Context();

        if (showDates) {
            // Show all dates for the specific check type

            try {
                // Show also report diff type in the format "CheckName - Date"
                context.setCurrentUser(ePersonService.find(context, this.getEpersonIdentifier()));
                List<ReportResult> allReports = reportResultService.findAll(context);
                Map<String, List<DateWithArgs>> reportDatesMap = new HashMap<>();

                for (ReportResult report : allReports) {
                    String dateStr = report.getLastModified().toString();

                    // Add the date to the list for this report type
                    reportDatesMap
                            .computeIfAbsent(report.getType(), k -> new ArrayList<>())
                            .add(new DateWithArgs(dateStr, report.getArgs()));
                }
                // Print the report dates
                StringBuilder sb = new StringBuilder();
                sb.append("Report Dates Summary:\n");
                // Get max 20 dates for each report type
                reportDatesMap.forEach((reportType, dateWithArgsList) -> {
                    dateWithArgsList.sort(Comparator.comparing(DateWithArgs::getDate).reversed());
                    List<DateWithArgs> topDates = dateWithArgsList.size() > 20
                            ? dateWithArgsList.subList(0, 20)
                            : dateWithArgsList;

                    sb.append(String.format("Report Type: %s%n", reportType));
                    sb.append(String.format("%s:%n", reportType));
                    topDates.forEach(entry -> sb.append(
                            String.format("  - %s | %s%n", entry.getDate(), entry.getArgs())));
                });
                handler.logInfo(sb.toString());
            } finally {
                context.complete();
            }
            return;

        }

        if (to != null && from != null && to.before(from)) {
            handler.logError("The 'to' date cannot be before the 'from' date.");
            return;
        }

        if (to != null && from == null) {
            handler.logError("The 'to' date is set, but the 'from' date is not. Please set both dates.");
            return;
        }

        if (from != null && to == null) {
            handler.logError("The 'from' date is set, but the 'to' date is not. Please set both dates.");
            return;
        }

        if (to != null && from != null) {
            // If both dates are set, we need to filter the reports by these dates
            handler.logInfo(String.format("Filtering reports from %s to %s", from, to));

            ReportResult toReport = null;
            ReportResult fromReport = null;
            if (specificCheck != -1) {
                // If a specific check is set, we need to filter the reports by this check type
                fromReport = reportResultService.findByLastModifiedAndCheckType(context, from, specificCheck);
                toReport = reportResultService.findByLastModifiedAndCheckType(context, to, specificCheck);
            } else {
                toReport = reportResultService.findByLastModified(context, to);
                fromReport = reportResultService.findByLastModified(context, from);
            }

            if (toReport == null || fromReport == null) {
                handler.logInfo(String.format("No reports found between %s and %s", from, to));
                return;
            }

            try {
                context.setCurrentUser(ePersonService.find(context, this.getEpersonIdentifier()));
                // Get the last two report results and compare them
                // Compare two JSONs
                // Write the report to the log
                // The report value is stored in the `value` column as a JSON string.
                String toReportValue = toReport.getValue();
                String fromReportValue = fromReport.getValue();
                if (toReportValue == null || fromReportValue == null) {
                    handler.logError("One of the reports has no value. Cannot compare reports.");
                    return;
                }

                // Create a diff comparing the two JSONs and the result store as String
                StringBuilder sbReport = new StringBuilder();
                sbReport.append("\nReport Diff between last two reports:\n");
                sbReport.append("The Report type: ").append(toReport.getType()).append("\n");
                sbReport.append("Command line options:\n");
                sbReport.append("From Report: ").append(fromReport.getLastModified()).append("\n");
                sbReport.append("To Report: ").append(toReport.getLastModified()).append("\n\n");
                sbReport.append("Differences:\n\n");
                // Compare the two JSONs
                sbReport.append(generateDiff(fromReportValue, toReportValue));

                handler.logInfo(sbReport.toString());
            } finally {
                context.complete();
            }

        }
    }

    @Override
    public void printHelp() {
        handler.printHelp(getScriptConfiguration().getOptions(), getScriptConfiguration().getName());
        handler.logInfo("This script compares two health reports and shows the differences between them.");
        handler.logInfo("You can specify the 'from' and 'to' dates to compare reports from specific dates.");
        handler.logInfo("If you want to see all available report dates, use the '-d' option.");
        handler.logInfo("If you want to compare a specific check, use the '-c' option with the check index, " +
                "in this case you must also specify the `from` and `to` dates.");
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

        // Compute the RFC6902 patch (an array of operations)
        JsonNode patch = JsonDiff.asJson(oldNode, newNode);

        if (!patch.isArray() || patch.size() == 0) {
            return "No differences found.";
        }

        StringBuilder sb = new StringBuilder();
        Iterator<JsonNode> elements = patch.elements();

        while (elements.hasNext()) {
            JsonNode op = elements.next();
            String operation = op.get("op").asText();   // “replace”, “add”, “remove”, etc.
            String path      = op.get("path").asText(); // e.g. “/checks/0/report/generated”

            switch (operation) {
                case "replace": {
                    JsonNode newValue = op.get("value");
                    // Lookup the old value from oldNode:
                    JsonNode oldValue = oldNode.at(path);
                    sb.append(String.format(
                            "- REPLACE at %s: %s -> %s%n",
                            path,
                            nodeToEscapedString(oldValue),
                            nodeToEscapedString(newValue)
                    ));
                    break;
                }
                case "add": {
                    JsonNode addedValue = op.get("value");
                    sb.append(String.format(
                            "- ADD     at %s: %s%n",
                            path,
                            nodeToEscapedString(addedValue)
                    ));
                    break;
                }
                case "remove": {
                    JsonNode removedValue = oldNode.at(path);
                    sb.append(String.format(
                            "- REMOVE  at %s: %s%n",
                            path,
                            nodeToEscapedString(removedValue)
                    ));
                    break;
                }
                case "move": {
                    String from = op.get("from").asText();
                    sb.append(String.format(
                            "- MOVE    from %s to %s%n",
                            from,
                            path
                    ));
                    break;
                }
                case "copy": {
                    String fromCopy = op.get("from").asText();
                    sb.append(String.format(
                            "- COPY    from %s to %s%n",
                            fromCopy,
                            path
                    ));
                    break;
                }
                case "test": {
                    JsonNode testValue = op.get("value");
                    sb.append(String.format(
                            "- TEST    at %s: must equal %s%n",
                            path,
                            nodeToEscapedString(testValue)
                    ));
                    break;
                }
                default: {
                    sb.append(String.format(
                            "%-7s at %s (unhandled op)%n",
                            operation.toUpperCase(),
                            path
                    ));
                }
            }
        }

        return sb.toString();
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
