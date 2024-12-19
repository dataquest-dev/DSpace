/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health.additionalUtilities;

import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Scanner;

import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
//import src.main.java.org.dspace.xoai.services.impl.config.DSpaceConfigurationService;

public class Info {

    protected static final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

    final static public String get_proc_uptime() {
        String uptime = "unknown";
        try {
            //works only on linux
            uptime = new Scanner(new FileInputStream("/proc/uptime")).next();
            System.out.println("\nUPTIME " + uptime);
            float fuptime = Float.parseFloat( uptime );
            int seconds = (int) (fuptime % 60);
            int minutes = (int) ((fuptime / 60) % 60);
            int hours   = (int) ((fuptime / (60 * 60)) % 24);
            int days   = (int) ((fuptime / (60 * 60 * 24)) );
            return Integer.toString( days ) + "d " +
                    Integer.toString( hours ) + "h:" +
                    Integer.toString( minutes ) + "m." +
                    Integer.toString( seconds );
        } catch (Exception e) {
            return uptime;
        }
    }

    final static public String get_jvm_uptime() {
        RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
        long milliseconds = mxBean.getUptime();
        int seconds = (int) (milliseconds / 1000) % 60 ;
        int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
        int hours   = (int) ((milliseconds / (1000 * 60 * 60)) % 24);
        int days   = (int) ((milliseconds / (1000 * 60 * 60 * 24)));

        return Integer.toString( days ) + "d " +
                Integer.toString( hours ) + "h:" +
                Integer.toString( minutes ) + "m." +
                Integer.toString( seconds );
    }

    final static public String get_build_time() {
        //springacka do nespring triedy, chceme autowired, component
        String buildTime = configurationService.getProperty("testing-config");
        if (buildTime != null && !buildTime.equals("")) {
            return buildTime;
        }
        return "unknown";
    }
}
