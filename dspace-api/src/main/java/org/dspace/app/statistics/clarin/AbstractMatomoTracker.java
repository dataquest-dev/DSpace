package org.dspace.app.statistics.clarin;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.clarin.ClarinUserMetadata;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.matomo.java.tracking.MatomoException;
import org.matomo.java.tracking.MatomoRequest;
import org.matomo.java.tracking.MatomoTracker;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AbstractMatomoTracker implements Tracker {
    protected AbstractMatomoTracker() {
    }

    /** log4j category */
    private static Logger log = Logger.getLogger(AbstractMatomoTracker.class);

    private final ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();

    private MatomoTracker tracker = ClarinServiceFactory.getInstance().getMatomoTracker();

    public void trackPage(HttpServletRequest request, String pageName) {
        log.debug("Matomo tracks " + pageName);
        String pageURL = getFullURL(request);

        MatomoRequest matomoRequest = null;
        try {
            matomoRequest = MatomoRequest.builder()
                    .siteId(1)
                    .actionUrl(pageURL) // include the query parameters to the url
                    .actionName(pageName)
                    .authToken(configurationService.getProperty("matomo.auth.token"))
                    .visitorIp(getIpAddress(request))
                    .build();
        } catch (MatomoException e) {
            log.error("Cannot create Matomo Request because: " + e.getMessage());
        }

        if (Objects.isNull(matomoRequest)) {
            return;
        }

        if (StringUtils.isNotBlank(request.getHeader("referer"))) {
            matomoRequest.setHeaderUserAgent(request.getHeader("referer"));
        }
        if (StringUtils.isNotBlank(request.getHeader("user-agent"))) {
            matomoRequest.setHeaderUserAgent(request.getHeader("user-agent"));
        }
        if (StringUtils.isNotBlank(request.getHeader("accept-language"))) {
            matomoRequest.setHeaderUserAgent(request.getHeader("accept-language"));
        }

//        URL url = tracker.getPageTrackURL(pageName);
//        try {
//            url = new URL(url.toString() + "&bots=1");
//        } catch(MalformedURLException e){}
        sendTrackingRequest(matomoRequest);
    }
//
//    public void trackDownload(HttpServletRequest request)
//    {
//        String downloadURL = getFullURL(request);
//
//        preTrack(request);
//        URL url = tracker.getDownloadTrackURL(downloadURL);
//        try {
//            url = new URL(url.toString() + "&bots=1");
//        } catch(MalformedURLException e){}
//        sendTrackingRequest(url);
//    }
//
    public void sendTrackingRequest(MatomoRequest request)
    {
        try {
            Future<HttpResponse> response = tracker.sendRequestAsync(request);
            // usually not needed:
            HttpResponse httpResponse = response.get();
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode > 399) {
                // problem
                log.error("Matomo tracker error the response has status code: " + statusCode);
            }
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("GET");
//            conn.connect();
//            int responseCode = conn.getResponseCode();
//
//            if (responseCode != 200)
//            {
//                log.error("Invalid response code from Piwik tracker API: "
//                        + responseCode);
//            }

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
//
    protected String getFullURL(HttpServletRequest request)
    {
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme());
        url.append("://");
        url.append(request.getServerName());
        url.append("http".equals(request.getScheme())
                && request.getServerPort() == 80
                || "https".equals(request.getScheme())
                && request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
        url.append(request.getRequestURI());
        url.append(request.getQueryString() != null ? "?"
                + request.getQueryString() : "");
        return url.toString();
    }

    protected String getIpAddress(HttpServletRequest request)
    {
        String ip = "";
        String header = request.getHeader("X-Forwarded-For");
        if(header == null) {
            header = request.getRemoteAddr();
        }
        if(header != null) {
            String[] ips = header.split(", ");
            ip = ips.length > 0 ? ips[0] : "";
        }
        return ip;
    }
}
