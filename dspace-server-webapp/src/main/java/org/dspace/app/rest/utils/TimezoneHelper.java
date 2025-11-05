package org.dspace.app.rest.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TimezoneHelper {
    
    @Autowired
    private ConfigurationService configurationService;
    
    private static final String DEFAULT_TIMEZONE = "UTC";
    
    public boolean isTimezoneConversionEnabled() {
        return configurationService.getBooleanProperty("timezone.conversion.enabled", false);
    }
    
    public String getDisplayTimezone() {
        return configurationService.getProperty("timezone.display", DEFAULT_TIMEZONE);
    }
    
    public Date convertDateForDisplay(Date utcDate) {
        if (utcDate == null || !isTimezoneConversionEnabled()) {
            return utcDate;
        }
        
        try {
            ZoneId targetZone = ZoneId.of(getDisplayTimezone());
            ZonedDateTime utcZoned = ZonedDateTime.ofInstant(utcDate.toInstant(), ZoneId.of("UTC"));
            ZonedDateTime converted = utcZoned.withZoneSameInstant(targetZone);
            return Date.from(converted.toInstant());
        } catch (Exception e) {
            return utcDate;
        }
    }
}