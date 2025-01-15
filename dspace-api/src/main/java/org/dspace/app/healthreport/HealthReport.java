/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.app.healthreport;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.health.*;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

import javax.mail.MessagingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.io.IOUtils.toInputStream;


public class HealthReport extends DSpaceRunnable<HealthReportScriptConfiguration> {
    ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();
    private static final Logger log = LogManager.getLogger(HealthReport.class);
    private EPersonService ePersonService;
//    private static final Map<String, Check> checks =
//            Collections.unmodifiableMap(Report.checks()); instead of checks?
    private static final LinkedHashMap<String, Check> checks = Report.checks();

    private boolean info = false;
    private String email;
    private int specificCheck = -1;
    private int forLastNDays = configurationService.getIntProperty("healthcheck.last_n_days");
    private String fileName;

    @Override
    public HealthReportScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager()
                .getServiceByName("health-report", HealthReportScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        if (commandLine.hasOption('i')) {
            info = true;
            return;
        }

        if (commandLine.hasOption('e')) {
            email = commandLine.getOptionValue('e');
            handler.logInfo("\nEmail where the health report will be send: " + email);
        }

        if (commandLine.hasOption('c')) {
            String checkOption = commandLine.getOptionValue('c');
            try {
                specificCheck = Integer.parseInt(checkOption);
                if (specificCheck < 0 || specificCheck >= getNumberOfChecks()) {
                    specificCheck = -1;
                    throw new IllegalArgumentException("Invalid value: " + specificCheck + ". It must be between 0 and " + (getNumberOfChecks() - 1) + ".");
                }
                handler.logInfo("\nOnly one specific task will be executed: " + specificCheck);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value, must be a number");
            }
        }

        if (commandLine.hasOption('f')) {
            String daysOption = commandLine.getOptionValue('f');
            try {
                forLastNDays = Integer.parseInt(daysOption);
            } catch (NumberFormatException e) {
                System.out.println("Invalid value for last N days, N must be a number");
                return;
            }
        }

        if (commandLine.hasOption('o')) {
            fileName = commandLine.getOptionValue('o');
        }
    }

    @Override
    public void internalRun() throws Exception {
        if (info) {
            printHelp();
            return;
        }

        ReportInfo ri = new ReportInfo(this.forLastNDays);

        StringBuilder sbReport = new StringBuilder();
        sbReport.append("\n\nHEALTH REPORT:\n");

        int pos = -1;
        for (Map.Entry<String, Check> check_entry : Report.checks().entrySet()) {
            ++pos;
            if (specificCheck != -1 && specificCheck != pos) {
                continue;
            }

            String name = check_entry.getKey();
            Check check = check_entry.getValue();

            if (check instanceof InfoCheck) {
                sbReport.append("\n\n######################\n").append(name).append(":\n");
                sbReport.append(((InfoCheck) check).run(ri));
            } else if (check instanceof ItemCheck) {
                sbReport.append("\n\n######################\n").append(name).append(":\n");
                sbReport.append(((ItemCheck) check).run(ri));
            } else if (check instanceof UserCheck) {
                sbReport.append("\n\n######################\n").append(name).append(":\n");
                sbReport.append(((UserCheck) check).run(ri));
            }
        }
        
        // save output to file
        if (fileName != null) {
            Context context = new Context();
            context.setCurrentUser(ePersonService.find(context, this.getEpersonIdentifier()));

            InputStream inputStream = toInputStream(sbReport.toString(), StandardCharsets.UTF_8);
            handler.writeFilestream(context, fileName, inputStream, "export");

            context.restoreAuthSystemState();
            context.complete();
        }

        // send an email
        if (email != null) {
            System.out.println(email);
            if (!email.contains("@")) {
                email = configurationService.getProperty(email);
            }
            try {
                String dspace_dir = configurationService.getProperty("dspace.dir");
                String email_path = dspace_dir.endsWith("/") ? dspace_dir
                        : dspace_dir + "/";
                email_path += Report.EMAIL_PATH;
                log.info(String.format(
                        "Looking for email template at [%s]", email_path));
                Email e = Email.getEmail(email_path);
                e.addRecipient(email);
                e.addArgument(sbReport.toString());
                e.send();
                System.out.println("email_path " + email_path + "; email " + email);
            } catch (IOException | MessagingException e) {
                log.error("Error sending email:", e);
                System.err.println("Error sending email:\n" + e.getMessage());
            }
        }

        handler.logInfo(sbReport.toString());
    }

    public void printHelp() {
        handler.logInfo("\n\nINFORMATION\nThis process creates a health report of your DSpace.\n" +
                "You can choose from these available options:\n" +
                "  -i, --info            Show help information\n" +
                "  -e, --email           Send report to specified email address\n" +
                "  -c, --check           Perform only specific check by index (0-" + (getNumberOfChecks() - 1) + ")\n" +
                "  -f, --for             Specify the last N days to consider\n" +
                "  -o, --output          Specify a file to save the report\n\n" +
//                "  -m, --maxResults      Limit the number of results displayed\n\n" +
                "If you want to execute only one check using -c, use check index:\n" + checksNamesToString() + "\n"
        );
    }

    public String checksNamesToString() {
        StringBuilder names = new StringBuilder();
        int pos = 0;
        for (String name : checks.keySet()) {
            names.append(String.format("   %d. %s\n", pos++, name));
        }
        return names.toString();
    }

    public static int getNumberOfChecks() {
        return checks.size();
    }
}
