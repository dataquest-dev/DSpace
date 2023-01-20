package org.dspace.app.statistics.clarin;

import org.dspace.content.Item;
import org.dspace.core.Context;

import javax.servlet.http.HttpServletRequest;

public interface Tracker {
    void trackPage(Context context, HttpServletRequest request, Item item, String pageName);
}
