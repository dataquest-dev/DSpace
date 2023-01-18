package org.dspace.app.statistics.clarin;

import javax.servlet.http.HttpServletRequest;

public interface Tracker {
    void trackPage(HttpServletRequest request, String pageName);
}
