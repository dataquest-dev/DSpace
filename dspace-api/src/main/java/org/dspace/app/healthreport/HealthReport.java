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

import static org.apache.commons.io.IOUtils.toInputStream;


public class HealthReport extends DSpaceRunnable<HealthReportScriptConfiguration> {
    ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();
    private static final Logger log = LogManager.getLogger(HealthReport.class);
    private EPersonService ePersonService;

    private boolean info = false;
    private String email;
    private int specificCheck;
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
                throw new IllegalArgumentException("Invalid value, must be a number");
            }
        }

        if (commandLine.hasOption('v')) {

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




        StringBuilder sb = new StringBuilder();
        sb.append("\n\nHEALTH REPORT:\n");
        sb.append("\nGeneral Information:\n");
        InfoCheck infoCheck = new InfoCheck();
        sb.append(infoCheck.run(ri));

        sb.append("\nItem Summary:\n");
        ItemCheck itemCheck = new ItemCheck();
        sb.append(itemCheck.run(ri));

        sb.append("\nUser Summary:\n");
        UserCheck userCheck = new UserCheck();
        sb.append(userCheck.run(ri));

        // save output to file
        if (fileName != null) {
            Context context = new Context();
            context.setCurrentUser(ePersonService.find(context, this.getEpersonIdentifier()));

            InputStream inputStream = toInputStream(sb.toString(), StandardCharsets.UTF_8);
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
                e.addRecipient(email.toString());
                e.addArgument(sb.toString());
                e.send();
                System.out.println("email_path " + email_path + "; email " + email.toString());
            } catch (IOException | MessagingException e) {
                log.error("Error sending email:", e);
                System.err.println("Error sending email:\n" + e.getMessage());
            }
        }

        handler.logInfo(sb.toString());
    }

    public void printHelp() {
        handler.logInfo("\n\nINFORMATION\nThis process creates a health report of your DSpace.\n" +
                "You can choose from these available options:\n" +
                "  -i, --info            Show help information\n" +
                "  -e, --email           Send report to specified email address\n" +
                "  -c, --check           Perform only specific check by index\n" +
                "  -f, --for             Specify the last N days to consider\n" +
                "  -v, --verbose         Verbose report\n" +
                "  -o, --outputFile      Specify a file to save the output\n" +
                "  -m, --maxResults      Limit the number of results displayed\n\n" +
                "If you want to execute only one check using -c, use check index:\n" +
                "   0. General Information\n   1. Item Summary\n   2. User Summary\n"
        );
    }

//    private void store(String name, long took, String report) {
//        name += String.format(" [took: %ds] [# lines: %d]",
//                took / 1000,
//                new StringTokenizer(report, "\r\n").countTokens()
//        );
//
//        String one_summary = String.format(
//                "\n#### %s\n%s\n\n###############################\n",
//                name,
//                report.replaceAll("\\s+$", "")
//        );
//        //summary_.append(one_summary);
//
//        // output it
//        System.out.println(one_summary);
//
//    }
}
