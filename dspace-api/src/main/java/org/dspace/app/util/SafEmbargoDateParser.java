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
 * <p>Both tools used to read that value with {@link org.dspace.content.DCDate}, which accepts seven shapes and
 * is lenient: {@code 2026-02-30} silently becomes 2 March and {@code 2026-13-01} becomes 1 January 2027, so a
 * typo turns into a real - possibly future - embargo date. Parsing is therefore strict here, but it still has
 * to accept the shapes {@code DCDate} accepted, or SAF packages that used to import would stop working. Each
 * shape is mapped to exactly the day {@code DCDate} mapped it to (verified against the class itself):</p>
 *
 * <table>
 *   <caption>accepted values</caption>
 *   <tr><td>{@code 2027-05-01}</td><td>1 May 2027</td></tr>
 *   <tr><td>{@code 2027-5-1}</td><td>1 May 2027 - unpadded, {@code SimpleDateFormat} took it</td></tr>
 *   <tr><td>{@code 2027-05-01T00:00:00Z}, {@code ...T00:00:00}, {@code ...T00:00}, {@code ...T00}</td>
 *       <td>1 May 2027, the UTC day of the instant; the time of day is dropped</td></tr>
 *   <tr><td>{@code 2027-05}</td><td><b>1</b> May 2027, not the end of the month</td></tr>
 *   <tr><td>{@code 2027}</td><td><b>1 January</b> 2027, not the end of the year</td></tr>
 * </table>
 *
 * <p>The last two rows are the ones worth reading twice. {@code DCDate} keeps a granularity, but
 * {@code toDate()} returns the <em>first</em> instant of that year or month, and the old code took that
 * {@code Date} as the embargo end. So {@code 2027} has always meant "the embargo ends on 1 January 2027", the
 * files open on 2 January 2027, and that reading is kept - widening it to 31 December would extend embargoes
 * that operators have already been living with.</p>
 *
 * <p>What is deliberately <em>not</em> kept from {@code DCDate}: the lenient roll-over of impossible dates,
 * trailing garbage ({@code SimpleDateFormat} read {@code 2099garbage} as the year 2099), and a numeric UTC
 * offset, which {@code DCDate} did not really support either - it ignored the offset and read
 * {@code 2027-05-01T00:00:00+02:00} as if it were UTC. All of those now throw, and every caller has to treat a
 * throw as "refuse the package", never as "no embargo".</p>
 */
public final class SafEmbargoDateParser {

    /**
     * {@code yyyy-MM-dd'T'HH[:mm[:ss[.fff]]]['Z']}, the four full ISO shapes of {@code DCDate} plus the
     * fractional seconds its prefix matching used to swallow. Everything is UTC, which is what the trailing
     * {@code Z} says and what {@code DCDate} assumed for the shapes without it.
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
     * {@code yyyy-M-d} with unpadded month and day. {@code SimpleDateFormat} accepted {@code 2027-5-1} and
     * meant 1 May 2027 by it, without any roll-over, so it is accepted here too - strictly, unlike
     * {@code DCDate}: {@code 2027-2-30} is still rejected.
     */
    private static final DateTimeFormatter UNPADDED_DATE = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH)
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
     * @return the embargo end day, never {@code null}
     * @throws DateTimeParseException if the value is none of the accepted shapes. It is never a licence to
     *                                skip the embargo: a caller that cannot read the date does not know
     *                                whether the item is embargoed, and has to refuse it.
     */
    public static LocalDate parseEmbargoEndDay(String value) {
        String trimmed = StringUtils.trimToEmpty(value);

        try {
            // yyyy-MM-dd, the shape everything written by DSpace itself has
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException notAnIsoDay) {
            // one of the older shapes, or garbage - decided below
        }

        try {
            return LocalDate.parse(trimmed, LEGACY_TIMESTAMP);
        } catch (DateTimeParseException notAnIsoTimestamp) {
            // ditto
        }

        try {
            // a month is a period; its embargo ends on its first day, as DCDate.toDate() reported it
            return YearMonth.parse(trimmed).atDay(1);
        } catch (DateTimeParseException notAYearMonth) {
            // ditto
        }

        try {
            // and a year on 1 January, for the same reason
            return Year.parse(trimmed).atDay(1);
        } catch (DateTimeParseException notAYear) {
            // ditto
        }

        try {
            return LocalDate.parse(trimmed, UNPADDED_DATE);
        } catch (DateTimeParseException notAnUnpaddedDay) {
            throw new DateTimeParseException("Unparseable embargo end date '" + value + "', expected "
                    + ACCEPTED_FORMATS, trimmed, notAnUnpaddedDay.getErrorIndex());
        }
    }
}
