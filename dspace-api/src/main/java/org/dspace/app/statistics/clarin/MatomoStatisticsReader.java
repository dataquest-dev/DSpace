package org.dspace.app.statistics.clarin;

import jdk.management.jfr.ConfigurationInfo;
import org.apache.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MatomoStatisticsReader {

    private static Logger log = Logger.getLogger(MatomoStatisticsReader.class);

    @Autowired
    ConfigurationService configurationService;

    private String request;
    private String response;

    public MatomoStatisticsReader() {
    }

    // Prepare request
    private void prepareRequest() throws ParseException {
        log.info("Preparing the Matomo API request");

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        Calendar cal = Calendar.getInstance();
        // default start and end data
        Date startDate = df.parse("2014-01-01");
        Date endDate = cal.getTime();

        String dspaceURL = configurationService.getProperty("dspace.url");

        String period = "month";
        String matomoSiteId = configurationService.getProperty("matomo.site.id");
        String authToken = configurationService.getProperty("matomo.auth.token");

        String urlParams =
                "&date=" + df.format(startDate) + "," + df.format(endDate)
                        + "&period=" + period
                        + "&idSite=" + matomoSiteId
                        + "&token_auth=" + authToken
//                        + "&segment=pageUrl=@" + dspaceURL + "/handle/" + item.getHandle()
                        + "&showColumns=label,url,nb_visits,nb_hits";
//        String downloadUrlParams =
//                "&date=" + df.format(startDate) + "," + df.format(endDate)
//                        + "&period=" + period
//                        + "&idSite=" + PIWIK_DOWNLOAD_SITE_ID
//                        + "&token_auth=" + PIWIK_AUTH_TOKEN
//                        + "&segment=pageUrl=@" + dspaceURL + "/bitstream/handle/" + item.getHandle()
//                        + "&showColumns=label,url,nb_visits,nb_hits";
    }

    // Send request
    private void sendRequest() {

    }

    // Process response
    private void processResponse() {

    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
