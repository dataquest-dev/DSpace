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
import java.io.EOFException;
import java.io.File;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    /**
     *
     * @param input_file
     * @return
     * @throws InstantiationException
     */
    static public BufferedReader safe_reader( String input_file )
            throws EOFException, InstantiationException
    {

        File file = new File(input_file);
        String file_id = file.getPath();

        if( !file.exists() ) {
            throw new InstantiationException( file_id + " does not exist!" );
        }
        if( file.exists() && 0 == file.length() ) {
            throw new EOFException( file_id + " is empty!" );
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), Charset.forName("UTF8")) );
        } catch( IOException e ) {
            throw new InstantiationException( file_id + " exception while reading: " + e.toString() );
        }

        return reader;
    }

    static public String today_string() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }
}
