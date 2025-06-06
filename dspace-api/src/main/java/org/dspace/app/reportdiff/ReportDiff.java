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
import java.util.ArrayList;
import java.util.Date;
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

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    private ReportResultService reportResultService = ContentServiceFactory.getInstance().getReportResultService();
    private EPersonService ePersonService;

    /**
     * Checks to be performed.
     */
//    private static final LinkedHashMap<String, Check> checks = Report.checks();

    /**
     * `-i`: Info, show help information.
     */
    private boolean info = false;

//    /**
//     * `-e`: Email, send report to specified email address.
//     */
//    private String[] emails;
//
//    /**
//     * `-c`: Check, perform only specific check by index (0-`getNumberOfChecks()`).
//     */
//    private int specificCheck = -1;
//
//    /**
//     * `-f`: For, specify the last N days to consider.
//     * Default value is set in dspace.cfg.
//     */
//    private int forLastNDays = configurationService.getIntProperty("healthcheck.last_n_days");
//
//    /**
//     * `-o`: Output, specify a file to save the report.
//     */
//    private String fileName;

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


    }

    @Override
    public void internalRun() throws Exception {
        if (info) {
            printHelp();
            return;
        }

        Context context = new Context();
        try {
            context.setCurrentUser(ePersonService.find(context, this.getEpersonIdentifier()));
            // Get the last two report results and compare them
            // Compare two JSONs
            // Write the report to the log
            List<ReportResult> allReports = reportResultService.findAll(context);
            ReportResult newReport = allReports.get(allReports.size() - 1);
            ReportResult oldReport = allReports.get(allReports.size() - 2);
            if (newReport == null || oldReport == null) {
                handler.logError("No previous report found. Cannot compare reports.");
                return;
            }
            // The report value is stored in the `value` column as a JSON string.
            String newReportValue = newReport.getValue();
            String oldReportValue = oldReport.getValue();
            if (newReportValue == null || oldReportValue == null) {
                handler.logError("One of the reports has no value. Cannot compare reports.");
                return;
            }


            // Create a diff comparing the two JSONs and the result store as String
            StringBuilder sbReport = new StringBuilder();
            sbReport.append("\nReport Diff between last two reports:\n");
            sbReport.append("The Report type: ").append(newReport.getType()).append("\n");
            sbReport.append("Command line options:\n");
//            sbReport.append(printCommandlineOptions());
            sbReport.append("Last Report: ").append(newReport.getLastModified()).append("\n");
            sbReport.append("Previous Report: ").append(oldReport.getLastModified()).append("\n\n");
            sbReport.append("Differences:\n");
            // Compare the two JSONs
            sbReport.append(generateDiff(oldReportValue, newReportValue));

            handler.logInfo(sbReport.toString());
        } finally {
            context.complete();
        }
    }

    @Override
    public void printHelp() {
        handler.logInfo("HEEEEY");
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
                            "REPLACE at %s: %s -> %s%n",
                            path,
                            nodeToEscapedString(oldValue),
                            nodeToEscapedString(newValue)
                    ));
                    break;
                }
                case "add": {
                    JsonNode addedValue = op.get("value");
                    sb.append(String.format(
                            "ADD     at %s: %s%n",
                            path,
                            nodeToEscapedString(addedValue)
                    ));
                    break;
                }
                case "remove": {
                    JsonNode removedValue = oldNode.at(path);
                    sb.append(String.format(
                            "REMOVE  at %s: %s%n",
                            path,
                            nodeToEscapedString(removedValue)
                    ));
                    break;
                }
                case "move": {
                    String from = op.get("from").asText();
                    sb.append(String.format(
                            "MOVE    from %s to %s%n",
                            from,
                            path
                    ));
                    break;
                }
                case "copy": {
                    String fromCopy = op.get("from").asText();
                    sb.append(String.format(
                            "COPY    from %s to %s%n",
                            fromCopy,
                            path
                    ));
                    break;
                }
                case "test": {
                    JsonNode testValue = op.get("value");
                    sb.append(String.format(
                            "TEST    at %s: must equal %s%n",
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

//    /**
//     * Convert checks names to string.
//     */
//    private String checksNamesToString() {
//        StringBuilder names = new StringBuilder();
//        int pos = 0;
//        for (String name : checks.keySet()) {
//            names.append(String.format("   %d. %s\n", pos++, name));
//        }
//        return names.toString();
//    }
//
//    /**
//     * Get the number of checks. This is used for the `-c` option.
//     */
//    public static int getNumberOfChecks() {
//        return checks.size();
//    }
}
