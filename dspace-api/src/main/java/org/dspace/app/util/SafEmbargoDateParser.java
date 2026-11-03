/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.util;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

import org.apache.commons.lang3.StringUtils;

/**
 * Turns the {@code dc.date.embargoend} value of a SAF package into the UTC calendar day on which the embargo
 * ends, for {@code dspace import} and {@code dspace itemupdate} alike.
 *
 * <p>Parsing is strict, unlike the {@link org.dspace.content.DCDate} both tools used before, which rolls
 * {@code 2026-02-30} over into 2 March and so turns a typo into a real embargo date. The shapes
 * {@code DCDate} accepted are still read, and read as the same day, so existing SAF packages keep working.</p>
 */
public final class SafEmbargoDateParser {

    /**
     * {@code yyyy-MM-dd'T'HH[:mm[:ss[.fff]]]['Z']}, the ISO shapes {@code DCDate} accepted. Always read as
     * UTC, which is what {@code DCDate} assumed for the shapes without a trailing {@code Z}.
     */
    private static final DateTimeFormatter LEGACY_TIMESTAMP = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .optionalStart().appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd().optionalEnd().optionalEnd()
            .optionalStart().appendLiteral('Z').optionalEnd()
            .toFormatter().withResolverStyle(ResolverStyle.STRICT);

    /**
     * {@code yyyy-M-d} with unpadded month and day, which {@code SimpleDateFormat} accepted and older SAF
     * packages therefore contain. Strict all the same: {@code 2027-2-30} is rejected.
     */
    private static final DateTimeFormatter UNPADDED_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter().withResolverStyle(ResolverStyle.STRICT);

    /**
     * {@code yyyy-M} with an unpadded month, which {@code SimpleDateFormat} accepted for the same reason.
     */
    private static final DateTimeFormatter UNPADDED_YEAR_MONTH = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .toFormatter().withResolverStyle(ResolverStyle.STRICT);

    /** Listed in operator messages, so that the two tools describe the same set of values. */
    public static final String ACCEPTED_FORMATS =
            "yyyy-MM-dd, yyyy-MM (first of the month), yyyy (1 January) or yyyy-MM-dd'T'HH[:mm[:ss]][Z]";

    private SafEmbargoDateParser() {
    }

    /**
     * The UTC calendar day on which the embargo ends, i.e. the last day the files stay closed.
     *
     * @param value raw {@code dc.date.embargoend}, surrounding whitespace is ignored
     * @return the embargo end day
     * @throws DateTimeParseException if the value is none of the accepted shapes; a caller that cannot read
     *                                the date has to refuse the package instead of assuming no embargo
     */
    public static LocalDate parseEmbargoEndDay(String value) {
        String trimmed = StringUtils.trimToEmpty(value);

        try {
            // yyyy-MM-dd, the shape DSpace itself writes
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException notAnIsoDay) {
            // an older shape or garbage, decided below
        }

        try {
            return LocalDate.parse(trimmed, LEGACY_TIMESTAMP);
        } catch (DateTimeParseException notAnIsoTimestamp) {
            // ditto
        }

        try {
            // DCDate.toDate() reported a bare month as its first day, so it keeps meaning that day
            return YearMonth.parse(trimmed).atDay(1);
        } catch (DateTimeParseException notAYearMonth) {
            // ditto
        }

        try {
            // and a bare year as 1 January, for the same reason
            return Year.parse(trimmed).atDay(1);
        } catch (DateTimeParseException notAYear) {
            // ditto
        }

        try {
            return LocalDate.parse(trimmed, UNPADDED_DATE);
        } catch (DateTimeParseException notAnUnpaddedDay) {
            // ditto
        }

        try {
            return YearMonth.parse(trimmed, UNPADDED_YEAR_MONTH).atDay(1);
        } catch (DateTimeParseException notAnUnpaddedYearMonth) {
            throw new DateTimeParseException("Unparseable embargo end date '" + value + "', expected "
                    + ACCEPTED_FORMATS, trimmed, notAnUnpaddedYearMonth.getErrorIndex());
        }
    }
}
