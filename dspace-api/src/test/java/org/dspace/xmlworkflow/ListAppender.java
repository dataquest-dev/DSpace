/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.ArrayList;
import java.util.List;

public class ListAppender extends AbstractAppender {
    private final List<LogEvent> logEvents = new ArrayList<>();

    public ListAppender(String name) {
        super(name, null, PatternLayout.createDefaultLayout(), false, null);
        start(); // important!
    }

    @Override
    public void append(LogEvent event) {
        logEvents.add(event.toImmutable()); // Store a copy
    }

    public List<LogEvent> getLogEvents() {
        return logEvents;
    }
}
