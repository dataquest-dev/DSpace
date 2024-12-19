/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health.additionalUtilities;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class IOUtils {
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(IOUtils.class);

    public static String runScript(File where, String[] cmd) {
        String message = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory( where );
            Process p = pb.start();
            BufferedReader std_out = new BufferedReader(new InputStreamReader(p.getInputStream()) );
            BufferedReader std_err = new BufferedReader(new InputStreamReader(p.getErrorStream()) );

            String s = null;
            message = "Returned stdout:\n";
            while ((s = std_out.readLine()) != null) {
                message += s + "\n  ";
            }
            message += "Returned stderr:\n";
            while ((s = std_err.readLine()) != null) {
                message += s + "\n  ";
            }

            //Wait to get exit value
            int exitValue = p.waitFor();
            message += "Exit code: [" + String.valueOf( exitValue ) + "]\n";

        } catch (Exception e) {
            message += "\nException:" + e.toString();
            log.error( e );
        }
        return message;
    }
}
