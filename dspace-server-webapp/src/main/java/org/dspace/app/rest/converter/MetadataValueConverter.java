/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.utils.TimezoneHelper;
import org.dspace.content.MetadataValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converter to translate between domain {@link MetadataValue}s and {@link MetadataValueRest} representations.
 */
@Component
public class MetadataValueConverter implements DSpaceConverter<MetadataValue, MetadataValueRest> {

    @Autowired
    private TimezoneHelper timezoneHelper;

    // Pattern to match ISO 8601 date-time strings in metadata values
    private static final Pattern ISO_DATETIME_PATTERN = 
        Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?Z?)");

    @Override
    public MetadataValueRest convert(MetadataValue metadataValue, Projection projection) {
        MetadataValueRest metadataValueRest = new MetadataValueRest();
        
        // Convert timezone-aware dates in the metadata value
        String convertedValue = convertDatesInValue(metadataValue.getValue());
        metadataValueRest.setValue(convertedValue);
        
        metadataValueRest.setLanguage(metadataValue.getLanguage());
        metadataValueRest.setAuthority(metadataValue.getAuthority());
        metadataValueRest.setConfidence(metadataValue.getConfidence());
        metadataValueRest.setPlace(metadataValue.getPlace());
        return metadataValueRest;
    }

    /**
     * Convert date-time strings in metadata values from UTC to user's timezone
     * This is particularly important for provenance metadata that contains timestamps
     */
    private String convertDatesInValue(String value) {
        if (value == null || !timezoneHelper.isTimezoneConversionEnabled()) {
            return value;
        }

        Matcher matcher = ISO_DATETIME_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String dateString = matcher.group(1);
            String convertedDate = convertISODateString(dateString);
            matcher.appendReplacement(result, convertedDate);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Convert a single ISO 8601 date string from UTC to user's timezone
     */
    private String convertISODateString(String isoDateString) {
        try {
            // Parse the UTC date string
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            SimpleDateFormat utcFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            
            Date utcDate;
            try {
                utcDate = utcFormat.parse(isoDateString);
            } catch (ParseException e) {
                utcDate = utcFormat2.parse(isoDateString);
            }
            
            // Convert to user's timezone
            Date localDate = timezoneHelper.convertDateForDisplay(utcDate);
            
            // Format back to ISO string format but without timezone indicator
            SimpleDateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return localFormat.format(localDate);
            
        } catch (ParseException e) {
            // If parsing fails, return original string
            return isoDateString;
        }
    }

    @Override
    public Class<MetadataValue> getModelClass() {
        return MetadataValue.class;
    }
}
