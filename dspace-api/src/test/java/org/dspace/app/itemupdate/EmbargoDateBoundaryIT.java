/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.builder.MetadataFieldBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Date-boundary behaviour of the embargo synchronisation in {@link ItemUpdate}: which calendar day the
 * resulting {@code Anonymous}/{@code READ} policy starts on, that exactly one such policy is left behind, and
 * that an embargo end date already in the past opens the file. All dates are derived from
 * {@code LocalDate.now(ZoneOffset.UTC)} so the suite cannot expire.
 */
public class EmbargoDateBoundaryIT extends AbstractIntegrationTestWithDatabase {

    /** Expected normalised policy name. Must stay within the 30 character {@code ResourcePolicy.rpname} column. */
    private static final String EMBARGO_POLICY_NAME = "embargo";

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    private final ResourcePolicyService resourcePolicyService =
            AuthorizeServiceFactory.getInstance().getResourcePolicyService();
    private final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private final MetadataSchemaService metadataSchemaService =
            ContentServiceFactory.getInstance().getMetadataSchemaService();
    private final MetadataFieldService metadataFieldService =
            ContentServiceFactory.getInstance().getMetadataFieldService();

    /** Human readable trace of every policy state observed during a test; appended to failure messages. */
    private final StringBuilder diagnostics = new StringBuilder();

    private Collection collection;
    private Group anonymousGroup;
    private Path tempDir;
    private String previousHandlePrefix;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .withName("Parent Community")
                .build();
        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .withName("Collection")
                .build();

        // Neither field exists in the test metadata registry; AddMetadataAction would fail without them.
        ensureMetadataFieldExists("rights", "access");
        ensureMetadataFieldExists("date", "embargoend");

        anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        previousHandlePrefix = ItemUpdate.HANDLE_PREFIX;
        ItemUpdate.HANDLE_PREFIX = handleService.getCanonicalPrefix();

        context.restoreAuthSystemState();

        tempDir = Files.createTempDirectory("embargoDateBoundaryIT");
    }

    @After
    @Override
    public void destroy() throws Exception {
        // HANDLE_PREFIX is a mutable public static; leaking it would poison other test classes.
        ItemUpdate.HANDLE_PREFIX = previousHandlePrefix;
        if (tempDir != null) {
            PathUtils.deleteDirectory(tempDir);
        }
        super.destroy();
    }

    /**
     * Verifies that a future {@code dc.date.embargoend} closes the file and leaves one normalised
     * {@code Anonymous}/{@code READ} policy starting the day after the embargo end date.
     */
    @Test
    public void futureEmbargoEndBlocksAccess() throws Exception {
        LocalDate embargoEnd = utcToday().plusMonths(6);
        LocalDate expectedStartDay = embargoEnd.plusDays(1);

        Item item = createItem("Future embargo thesis");
        Bitstream bitstream = createOriginalBitstream(item, "future.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, "embargoedAccess", embargoEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + embargoEnd, bitstream);

        assertEmbargoEndStored(item, embargoEnd.toString());

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("future embargo end " + embargoEnd, bitstream);
        assertNormalisedEmbargoPolicy("future embargo end " + embargoEnd, policy, expectedStartDay);

        assertFalse("dc.date.embargoend=" + embargoEnd + " lies in the future, so resource policy #"
                        + policy.getID() + " must not be date-valid yet." + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertFalse("dc.date.embargoend=" + embargoEnd + " lies in the future, so an anonymous visitor must NOT"
                        + " be able to download the ORIGINAL bitstream." + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that {@code dc.date.embargoend} is the inclusive last day: an end date of today keeps the file
     * closed today and opens it tomorrow.
     */
    @Test
    public void embargoEndTodayStillBlocksToday() throws Exception {
        LocalDate embargoEnd = utcToday();
        LocalDate expectedStartDay = embargoEnd.plusDays(1);

        Item item = createItem("Embargo ending today");
        Bitstream bitstream = createOriginalBitstream(item, "today.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, "embargoedAccess", embargoEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with dc.date.embargoend=TODAY=" + embargoEnd, bitstream);

        assertEmbargoEndStored(item, embargoEnd.toString());

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("embargo ending today " + embargoEnd, bitstream);
        assertNormalisedEmbargoPolicy("embargo ending today " + embargoEnd, policy, expectedStartDay);

        assertFalse("dc.date.embargoend=" + embargoEnd + " is TODAY and the last day of an embargo is inclusive,"
                        + " so resource policy #" + policy.getID() + " must start tomorrow (" + expectedStartDay
                        + ") and must not be date-valid yet." + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertFalse("dc.date.embargoend=" + embargoEnd + " is TODAY, so the ORIGINAL bitstream must still be"
                        + " closed for anonymous visitors today and open only from " + expectedStartDay + "."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an embargo which ended yesterday publishes the file, with the policy surviving and starting
     * today.
     */
    @Test
    public void embargoEndYesterdayOpensAccess() throws Exception {
        LocalDate embargoEnd = utcToday().minusDays(1);
        LocalDate expectedStartDay = embargoEnd.plusDays(1);

        // No dc.rights.access at all - the specification treats a missing value exactly like openAccess.
        Item item = createItem("Embargo ended yesterday");
        Bitstream bitstream = createOriginalBitstream(item, "yesterday.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, null, embargoEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with dc.date.embargoend=YESTERDAY=" + embargoEnd, bitstream);

        assertEmbargoEndStored(item, embargoEnd.toString());

        ResourcePolicy policy =
                assertExactlyOneAnonymousReadPolicy("embargo ended yesterday " + embargoEnd, bitstream);
        assertNormalisedEmbargoPolicy("embargo ended yesterday " + embargoEnd, policy, expectedStartDay);

        assertTrue("dc.date.embargoend=" + embargoEnd + " expired yesterday, so resource policy #" + policy.getID()
                        + " (start=" + policy.getStartDate() + ") must already be date-valid." + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertTrue("An embargo that ended yesterday publishes the file, so the ORIGINAL bitstream must be"
                        + " readable by anonymous visitors." + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that an expired {@code dc.date.embargoend} with {@code dc.rights.access=openAccess} publishes the
     * file. The priming run replaces the inherited collection default with a single dated policy.
     */
    @Test
    public void pastEmbargoEndWithOpenAccessOpensAccess() throws Exception {
        LocalDate futureEnd = utcToday().plusYears(1);
        LocalDate pastEnd = utcToday().minusMonths(1);
        LocalDate expectedStartDay = pastEnd.plusDays(1);

        Item item = createItem("VSB-TUO thesis published after embargo");
        Bitstream bitstream = createOriginalBitstream(item, "thesis.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        // priming run: an embargo with a future end date
        runItemUpdate(item, dublinCore(item, "embargoedAccess", futureEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + futureEnd, bitstream);
        assertFalse("fixture precondition: while embargoed until " + futureEnd + " the ORIGINAL bitstream must"
                        + " not be publicly readable." + diagnostics,
                anonymousCanRead(bitstream));

        // the embargo expires: past end date, item declared openAccess
        runItemUpdate(item, dublinCore(item, "openAccess", pastEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C - after itemupdate with PAST dc.date.embargoend=" + pastEnd
                + " and dc.rights.access=openAccess", bitstream);

        assertEmbargoEndStored(item, pastEnd.toString());
        assertEquals("itemupdate did not store dc.rights.access=openAccess." + diagnostics,
                "openAccess", firstMetadataValue(item, "rights", "access"));

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("expired embargo " + pastEnd
                + " with dc.rights.access=openAccess", bitstream);
        assertNormalisedEmbargoPolicy("expired embargo " + pastEnd, policy, expectedStartDay);

        assertTrue("Expired embargo (" + pastEnd + ") left resource policy #" + policy.getID()
                        + " not date-valid, so the file stays unreachable although dc.rights.access=openAccess."
                        + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertTrue("An expired embargo publishes the file. The ORIGINAL bitstream is still unreadable for"
                        + " anonymous visitors (HTTP 401) although dc.date.embargoend=" + pastEnd
                        + " has passed and dc.rights.access=openAccess." + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Same as {@link #pastEmbargoEndWithOpenAccessOpensAccess()} with {@code dc.rights.access=embargoedAccess}:
     * the access right names the licence regime, the end date decides when the embargo lapses.
     */
    @Test
    public void pastEmbargoEndWithEmbargoedAccessOpensAccess() throws Exception {
        LocalDate futureEnd = utcToday().plusYears(1);
        LocalDate pastEnd = utcToday().minusMonths(1);
        LocalDate expectedStartDay = pastEnd.plusDays(1);

        Item item = createItem("VSB-TUO thesis with lapsed embargoedAccess");
        Bitstream bitstream = createOriginalBitstream(item, "lapsed.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, "embargoedAccess", futureEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + futureEnd, bitstream);
        assertFalse("fixture precondition: while embargoed until " + futureEnd + " the ORIGINAL bitstream must"
                        + " not be publicly readable." + diagnostics,
                anonymousCanRead(bitstream));

        runItemUpdate(item, dublinCore(item, "embargoedAccess", pastEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C - after itemupdate with PAST dc.date.embargoend=" + pastEnd
                + " and dc.rights.access=embargoedAccess", bitstream);

        assertEmbargoEndStored(item, pastEnd.toString());

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("expired embargo " + pastEnd
                + " with dc.rights.access=embargoedAccess", bitstream);
        assertNormalisedEmbargoPolicy("expired embargo " + pastEnd, policy, expectedStartDay);

        assertTrue("Expired embargo (" + pastEnd + ") left resource policy #" + policy.getID()
                        + " not date-valid." + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertTrue("dc.rights.access=embargoedAccess must not keep an ALREADY EXPIRED embargo closed; the"
                        + " ORIGINAL bitstream must be readable by anonymous visitors after " + pastEnd + "."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that the start date is midnight UTC in session and comes back from the DATE column as the
     * calendar day {@code dc.date.embargoend + 1}, which is the day later requests are authorised against.
     */
    @Test
    public void startDateSurvivesTheDatabaseAsTheExpectedCalendarDay() throws Exception {
        LocalDate futureEnd = nextIrishSummerTimeDay();
        LocalDate futureStartDay = futureEnd.plusDays(1);
        assertNotEquals("fixture precondition: the JVM time zone (" + ZoneId.systemDefault() + ") must differ"
                        + " from UTC on " + futureStartDay + ", otherwise this test cannot tell midnight UTC"
                        + " apart from midnight in the server zone. The harness pins Europe/Dublin in"
                        + " AbstractDSpaceIntegrationTest.",
                Date.from(futureStartDay.atStartOfDay(ZoneOffset.UTC).toInstant()),
                Date.from(futureStartDay.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        // stored day still ahead: the file stays closed after the reload
        assertStoredStartDaySurvivesRoundTrip(futureEnd, false);

        // embargo ended yesterday: the stored day is today and the policy is in force
        assertStoredStartDaySurvivesRoundTrip(utcToday().minusDays(1), true);
    }

    /**
     * One leg of {@link #startDateSurvivesTheDatabaseAsTheExpectedCalendarDay()}. Calls
     * {@code syncEmbargoPolicies} directly because the in-session instant is asserted before the commit, and
     * {@code processArchive} ends with {@code context.uncacheEntity(item)}.
     *
     * @param embargoEnd       value of {@code dc.date.embargoend}
     * @param expectedReadable whether an anonymous visitor must be able to download the file once the policy
     *                         has been read back from the database
     */
    private void assertStoredStartDaySurvivesRoundTrip(LocalDate embargoEnd, boolean expectedReadable)
            throws Exception {
        LocalDate expectedStartDay = embargoEnd.plusDays(1);
        String leg = "dc.date.embargoend=" + embargoEnd;
        Date expectedUtcMidnight = Date.from(expectedStartDay.atStartOfDay(ZoneOffset.UTC).toInstant());
        Date serverZoneMidnight = Date.from(expectedStartDay.atStartOfDay(ZoneId.systemDefault()).toInstant());

        Item item = createItem("Round trip embargo " + embargoEnd,
                "rights", "access", "embargoedAccess",
                "date", "embargoend", embargoEnd.toString());
        Bitstream bitstream = createOriginalBitstream(item, "round-trip-" + embargoEnd + ".pdf");
        dump("STEP A [" + leg + "] - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);
        Integer importedPolicyId = anonymousReadPolicies(bitstream).get(0).getID();

        context.turnOffAuthorisationSystem();
        try {
            new ItemUpdate().syncEmbargoPolicies(context, item);
        } finally {
            context.restoreAuthSystemState();
        }
        dump("STEP B [" + leg + "] - after syncEmbargoPolicies, still inside the Hibernate session", bitstream);

        ResourcePolicy inSession = assertExactlyOneAnonymousReadPolicy(leg, bitstream);
        assertEquals("[" + leg + "] the inherited Anonymous/READ policy must be MUTATED in place, not deleted"
                        + " and recreated: a policy may never be removed before its replacement is stored,"
                        + " otherwise a failure between the two leaves the file with zero policies (HTTP 401)."
                        + diagnostics,
                importedPolicyId, inSession.getID());
        assertNotNull("[" + leg + "] resource policy #" + inSession.getID() + " must carry a start date."
                        + diagnostics,
                inSession.getStartDate());
        assertEquals(leg + " must yield a start date of exactly midnight UTC on " + expectedStartDay
                        + " (epochMillis=" + expectedUtcMidnight.getTime() + "). Midnight in the server time"
                        + " zone " + ZoneId.systemDefault() + " would be epochMillis="
                        + serverZoneMidnight.getTime() + ", which is what Calendar.getInstance() or"
                        + " java.sql.Date.valueOf(LocalDate) produce. Actual epochMillis="
                        + inSession.getStartDate().getTime() + " ("
                        + inSession.getStartDate().toInstant().atZone(ZoneOffset.UTC) + ")." + diagnostics,
                expectedUtcMidnight.getTime(), inSession.getStartDate().getTime());

        // Read the start date back out of the DATE column instead of out of Hibernate's memory - that is the
        // value the next request authorises against.
        context.commit();
        context.uncacheEntities();
        // Everything the test holds is detached by now, the fixture fields included.
        collection = context.reloadEntity(collection);
        anonymousGroup = context.reloadEntity(anonymousGroup);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C [" + leg + "] - after commit + uncacheEntities, read back from the database", bitstream);

        ResourcePolicy stored = assertExactlyOneAnonymousReadPolicy(leg + ", read back from the database",
                bitstream);
        assertNotSame("[" + leg + "] fixture precondition: the policy has to be read back from the database,"
                        + " but the identical instance came out of the Hibernate session, so this leg would"
                        + " prove nothing about the stored value." + diagnostics,
                inSession, stored);
        assertEquals("[" + leg + "] the policy id must survive the round trip." + diagnostics,
                importedPolicyId, stored.getID());
        assertEquals("[" + leg + "] resourcepolicy.start_date is a DATE column, so the calendar day is the"
                        + " only part of the start date that survives - and it is the part that decides"
                        + " access. Expected " + expectedStartDay + " (dc.date.embargoend + 1 day), stored "
                        + stored.getStartDate() + "." + diagnostics,
                expectedStartDay, toLocalDate(stored.getStartDate()));
        assertNull("[" + leg + "] this tool must never write an end date: a policy that expires by itself"
                        + " would close the file again on that day." + diagnostics,
                stored.getEndDate());
        assertNormalisedEmbargoPolicy(leg + ", read back from the database", stored, expectedStartDay);

        if (expectedReadable) {
            assertTrue("[" + leg + "] access starts on " + expectedStartDay + ", which is not after "
                            + utcToday() + ", so the policy read back from the database has to be date-valid."
                            + diagnostics,
                    resourcePolicyService.isDateValid(stored));
            assertTrue("[" + leg + "] the embargo has expired, so an anonymous visitor has to be able to"
                            + " download the ORIGINAL bitstream after the round trip." + diagnostics,
                    anonymousCanRead(bitstream));
        } else {
            assertFalse("[" + leg + "] access starts on " + expectedStartDay + ", which is still ahead of "
                            + utcToday() + ", so the policy read back from the database must not be date-valid."
                            + diagnostics,
                    resourcePolicyService.isDateValid(stored));
            assertFalse("[" + leg + "] the embargo is still running, so an anonymous visitor must NOT be able"
                            + " to download the ORIGINAL bitstream after the round trip." + diagnostics,
                    anonymousCanRead(bitstream));
        }
    }

    /**
     * Verifies that several {@code dc.date.embargoend} values are reported to the operator while the run
     * completes and follows the first value.
     */
    @Test
    public void multipleEmbargoEndValuesUsesFirst() throws Exception {
        LocalDate firstEnd = utcToday().plusDays(30);
        LocalDate secondEnd = utcToday().plusDays(400);
        LocalDate expectedStartDay = firstEnd.plusDays(1);

        Item item = createItem("Two embargo end dates");
        Bitstream bitstream = createOriginalBitstream(item, "two-dates.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        String consoleOutput = runItemUpdate(item,
                dublinCore(item, "embargoedAccess", firstEnd.toString(), secondEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with dc.date.embargoend=" + firstEnd + " AND " + secondEnd, bitstream);

        List<String> storedEndDates = metadataValues(item, "date", "embargoend");
        assertEquals("fixture precondition: itemupdate must have stored both dc.date.embargoend values, got "
                        + storedEndDates, 2, storedEndDates.size());
        assertEquals("fixture precondition: dublin_core.xml document order must be preserved, so the first stored"
                        + " dc.date.embargoend value is the first one written; got " + storedEndDates,
                firstEnd.toString(), storedEndDates.get(0));

        String lowerCaseOutput = consoleOutput.toLowerCase(Locale.ROOT);
        assertTrue("itemupdate must warn the operator that dc.date.embargoend carries more than one value and"
                        + " that only the first one is used. Console output was:\n" + consoleOutput,
                lowerCaseOutput.contains("multiple") && lowerCaseOutput.contains("embargoend"));

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("two dc.date.embargoend values ("
                + firstEnd + ", " + secondEnd + ")", bitstream);
        assertNormalisedEmbargoPolicy("two dc.date.embargoend values", policy, expectedStartDay);

        assertNotEquals("the SECOND dc.date.embargoend value (" + secondEnd + ") must be ignored, the embargo has"
                        + " to follow the first one (" + firstEnd + ")." + diagnostics,
                secondEnd.plusDays(1), toLocalDate(policy.getStartDate()));
        assertFalse("both dc.date.embargoend values lie in the future, so the ORIGINAL bitstream must not be"
                        + " publicly readable." + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that the value shapes {@code DCDate} accepted mean the same day here as on the import path: a
     * bare year or month is its first day, a timestamp is truncated to its UTC day.
     */
    @Test
    public void legacyEmbargoEndShapesKeepTheirDcDateDay() throws Exception {
        int nextYear = utcToday().getYear() + 1;
        LocalDate tomorrow = utcToday().plusDays(1);

        // a bare year is 1 January of it, not 31 December, which would extend the embargo
        assertLegacyEmbargoEndClosesTheFileUntil(String.valueOf(nextYear), LocalDate.of(nextYear, 1, 1));
        // a bare month is the 1st of it, for the same reason
        assertLegacyEmbargoEndClosesTheFileUntil(nextYear + "-05", LocalDate.of(nextYear, 5, 1));
        // the shape DSpace exports write; the time of day is dropped, the UTC day is the last closed day
        assertLegacyEmbargoEndClosesTheFileUntil(tomorrow + "T00:00:00Z", tomorrow);
    }

    /**
     * Verifies that a legacy shape whose day lies in the past publishes the file: one immediately effective
     * policy, an anonymous visitor who gets the file, and a run that reports no problem.
     */
    @Test
    public void legacyPastEmbargoEndPublishesOnPurpose() throws Exception {
        LocalDate primingEnd = utcToday().plusYears(1);
        int legacyPastYear = utcToday().getYear() - 5;
        String legacyValue = String.valueOf(legacyPastYear);
        LocalDate expectedEmbargoEnd = LocalDate.of(legacyPastYear, 1, 1);
        LocalDate expectedStartDay = expectedEmbargoEnd.plusDays(1);
        String scenario = "legacy dc.date.embargoend=" + legacyValue + " (DCDate day " + expectedEmbargoEnd + ")";

        Item item = createItem("Legacy year-only embargo end in the past");
        Bitstream bitstream = createOriginalBitstream(item, "legacy-past.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        // The priming run is what makes this a publication rather than a no-op: it leaves the single dated
        // policy that keeps the file closed, so opening it afterwards is a state change that can be observed.
        runItemUpdate(item, dublinCore(item, "embargoedAccess", primingEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after priming itemupdate with a FUTURE dc.date.embargoend=" + primingEnd, bitstream);
        assertFalse("fixture precondition: after the priming run with dc.date.embargoend=" + primingEnd
                        + " the file has to be closed, otherwise the legacy value below opens nothing."
                        + diagnostics,
                anonymousCanRead(bitstream));

        runItemUpdate(item, dublinCore(item, "openAccess", legacyValue));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP C - after itemupdate with " + scenario, bitstream);

        assertEmbargoEndStored(item, legacyValue);

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy(scenario, bitstream);
        assertNormalisedEmbargoPolicy(scenario, policy, expectedStartDay);

        assertTrue("dc.date.embargoend=" + legacyValue + " is the year " + legacyPastYear + ", i.e. an embargo"
                        + " that ended on " + expectedEmbargoEnd + ", so resource policy #" + policy.getID()
                        + " (start=" + policy.getStartDate() + ") has to be date-valid already." + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertTrue("An embargo that ended in " + legacyPastYear + " publishes the file. This is the intended"
                        + " reading of a bare year and not a parsing accident: the ORIGINAL bitstream has to be"
                        + " readable by anonymous visitors." + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Verifies that values only a lenient parser would accept are refused, leaving every policy where it was
     * and failing the run - both shapes would move an embargo boundary without saying so.
     */
    @Test
    public void unparseableLegacyLookalikeLeavesPoliciesUntouched() throws Exception {
        int nextYear = utcToday().getYear() + 1;

        // trailing garbage after a year that SimpleDateFormat used to ignore
        assertEmbargoEndIsRefused(nextYear + "garbage");
        // a numeric UTC offset: DCDate read this as midnight UTC, i.e. two hours off, and never said so
        assertEmbargoEndIsRefused(nextYear + "-05-01T00:00:00+02:00");
    }

    /**
     * One legacy value that has to close the file until the day {@code DCDate} mapped it to.
     *
     * @param legacyValue        raw {@code dc.date.embargoend} as a legacy SAF package writes it
     * @param expectedEmbargoEnd last closed day that value maps to; has to be in the future
     */
    private void assertLegacyEmbargoEndClosesTheFileUntil(String legacyValue, LocalDate expectedEmbargoEnd)
            throws Exception {
        LocalDate expectedStartDay = expectedEmbargoEnd.plusDays(1);
        String scenario = "legacy dc.date.embargoend=" + legacyValue + " (DCDate day " + expectedEmbargoEnd + ")";

        assertTrue("test bug [" + scenario + "]: the expected embargo end day has to lie in the future, or the"
                        + " scenario silently turns into the expired-embargo one.",
                expectedStartDay.isAfter(utcToday()));

        Item item = createItem("Legacy embargo end " + legacyValue);
        Bitstream bitstream = createOriginalBitstream(item, "legacy.pdf");
        dump("STEP A [" + legacyValue + "] - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, "embargoedAccess", legacyValue));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B [" + legacyValue + "] - after itemupdate", bitstream);

        assertEmbargoEndStored(item, legacyValue);

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy(scenario, bitstream);
        assertNormalisedEmbargoPolicy(scenario, policy, expectedStartDay);

        assertFalse("[" + scenario + "] resource policy #" + policy.getID() + " must not be date-valid yet."
                        + diagnostics,
                resourcePolicyService.isDateValid(policy));
        assertFalse("[" + scenario + "] the embargo ends on " + expectedEmbargoEnd + ", which is in the future,"
                        + " so an anonymous visitor must NOT be able to download the ORIGINAL bitstream."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * One value that has to be refused: the policies of an embargoed file stay as they were and the run counts
     * a failure.
     *
     * @param rejectedValue raw {@code dc.date.embargoend} that no accepted shape matches
     */
    private void assertEmbargoEndIsRefused(String rejectedValue) throws Exception {
        LocalDate primingEnd = utcToday().plusYears(1);
        LocalDate primingStartDay = primingEnd.plusDays(1);
        String scenario = "unparseable dc.date.embargoend=" + rejectedValue;

        Item item = createItem("Unparseable embargo end " + rejectedValue);
        Bitstream bitstream = createOriginalBitstream(item, "unparseable.pdf");
        assertFreshImportBaseline(bitstream);

        runItemUpdate(item, dublinCore(item, "embargoedAccess", primingEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP A [" + rejectedValue + "] - embargoed until " + primingEnd, bitstream);
        assertFalse("fixture precondition [" + scenario + "]: the file has to be closed before the unparseable"
                + " value is fed in." + diagnostics, anonymousCanRead(bitstream));

        Set<Integer> idsBefore = allPolicyIds(bitstream);

        String consoleOutput = runItemUpdate(item, dublinCore(item, "embargoedAccess", rejectedValue), 1);
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B [" + rejectedValue + "] - after itemupdate with the unparseable value", bitstream);

        assertEquals("[" + scenario + "] the set of resource policy ids changed, so policies were deleted and/or"
                        + " re-created although an unreadable date is no instruction at all. Console output"
                        + " was:\n" + consoleOutput + diagnostics,
                idsBefore, allPolicyIds(bitstream));

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy(scenario, bitstream);
        assertEquals("[" + scenario + "] the surviving policy was re-dated although the value could not be read."
                        + " Whatever the tool cannot parse it must not act on." + diagnostics,
                primingStartDay, toLocalDate(policy.getStartDate()));
        assertFalse("[" + scenario + "] the embargoed file became publicly readable after an unreadable"
                + " dc.date.embargoend." + diagnostics, anonymousCanRead(bitstream));
    }

    /**
     * Asserts the state a fresh SAF import leaves behind: one Anonymous/READ policy without a start date,
     * inherited from the collection DEFAULT_BITSTREAM_READ.
     */
    private void assertFreshImportBaseline(Bitstream bitstream) throws Exception {
        List<Group> defaultBitstreamReadGroups =
                authorizeService.getAuthorizedGroups(context, collection, Constants.DEFAULT_BITSTREAM_READ);
        assertTrue("fixture precondition: the collection must grant DEFAULT_BITSTREAM_READ to Anonymous,"
                        + " otherwise the imported bitstream does not model the customer's repository.",
                defaultBitstreamReadGroups.contains(anonymousGroup));

        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        assertEquals("fixture precondition: a freshly imported ORIGINAL bitstream must carry exactly one"
                        + " Anonymous/READ policy inherited from the collection default." + diagnostics,
                1, policies.size());
        assertNull("fixture precondition: the inherited Anonymous/READ policy must have no start date."
                        + diagnostics,
                policies.get(0).getStartDate());
        assertTrue("fixture precondition: a freshly imported ORIGINAL bitstream must be publicly readable."
                        + diagnostics,
                anonymousCanRead(bitstream));
    }

    /**
     * Asserts a single Anonymous/READ policy: zero leaves the file unreachable, more than one lets an undated
     * policy coexist with the dated one and neutralise the embargo.
     */
    private ResourcePolicy assertExactlyOneAnonymousReadPolicy(String scenario, Bitstream bitstream)
            throws Exception {
        List<ResourcePolicy> policies = anonymousReadPolicies(bitstream);
        assertEquals("After " + scenario + " the ORIGINAL bitstream must carry EXACTLY ONE Anonymous/READ policy."
                        + " Zero policies make the file unreachable (HTTP 401); more than one lets an undated"
                        + " policy neutralise the embargo. Found " + policies.size() + "." + diagnostics,
                1, policies.size());
        return policies.get(0);
    }

    /**
     * Asserts the normalised shape of the surviving policy: {@code TYPE_CUSTOM}, {@code rpName="embargo"} and a
     * start date of {@code dc.date.embargoend + 1 day}.
     */
    private void assertNormalisedEmbargoPolicy(String scenario, ResourcePolicy policy, LocalDate expectedStartDay) {
        assertNotNull("After " + scenario + " resource policy #" + policy.getID() + " must carry a start date."
                        + diagnostics,
                policy.getStartDate());
        assertEquals("After " + scenario + " resource policy #" + policy.getID() + " must start on the day AFTER"
                        + " dc.date.embargoend, because the end date is the inclusive last day of the embargo."
                        + diagnostics,
                expectedStartDay, toLocalDate(policy.getStartDate()));
        assertEquals("After " + scenario + " resource policy #" + policy.getID() + " must be normalised to"
                        + " rpType=" + ResourcePolicy.TYPE_CUSTOM + "; AuthorizeServiceImpl only honours custom"
                        + " policies on not-yet-installed items." + diagnostics,
                ResourcePolicy.TYPE_CUSTOM, policy.getRpType());
        assertEquals("After " + scenario + " resource policy #" + policy.getID() + " must be normalised to"
                        + " rpName=\"" + EMBARGO_POLICY_NAME + "\", replacing legacy names such as"
                        + " \"Standard Embargo\" or \"Special Case Embargo\"." + diagnostics,
                EMBARGO_POLICY_NAME, policy.getRpName());
    }

    private void assertEmbargoEndStored(Item item, String expectedEmbargoEnd) {
        assertEquals("itemupdate did not store dc.date.embargoend on the item, so the run never really reached it"
                        + " (ItemArchive.create may have failed to resolve it - processArchive swallows every"
                        + " per-item exception)." + diagnostics,
                expectedEmbargoEnd, firstMetadataValue(item, "date", "embargoend"));
    }

    /**
     * Tells whether a visitor who is not logged in may read the bitstream. The authorisation state is a stack
     * the builders push and pop, so it is drained first - otherwise every read looks allowed.
     */
    private boolean anonymousCanRead(Bitstream bitstream) throws Exception {
        EPerson savedUser = context.getCurrentUser();
        int popped = 0;
        while (context.ignoreAuthorization()) {
            context.restoreAuthSystemState();
            popped++;
        }
        context.setCurrentUser(null);
        try {
            return authorizeService.authorizeActionBoolean(context, bitstream, Constants.READ);
        } finally {
            context.setCurrentUser(savedUser);
            for (int i = 0; i < popped; i++) {
                context.turnOffAuthorisationSystem();
            }
        }
    }

    /**
     * Every resource policy id of the bitstream. Ids rather than counts, so a policy that was deleted and
     * re-created is visible.
     */
    private Set<Integer> allPolicyIds(Bitstream bitstream) throws Exception {
        Set<Integer> ids = new TreeSet<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream)) {
            ids.add(policy.getID());
        }
        return ids;
    }

    private List<ResourcePolicy> anonymousReadPolicies(Bitstream bitstream) throws Exception {
        return resourcePolicyService.find(context, bitstream, Constants.READ).stream()
                .filter(policy -> policy.getGroup() != null && anonymousGroup.equals(policy.getGroup()))
                .collect(Collectors.toList());
    }

    private void dump(String label, Bitstream bitstream) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator())
          .append("  === ").append(label).append(" ===").append(System.lineSeparator())
          .append("      bitstream=").append(bitstream.getID()).append(System.lineSeparator())
          .append("      anonymousCanRead=").append(anonymousCanRead(bitstream)).append(System.lineSeparator());

        List<ResourcePolicy> policies = resourcePolicyService.find(context, bitstream, Constants.READ);
        if (policies.isEmpty()) {
            sb.append("      <NO READ POLICIES AT ALL>").append(System.lineSeparator());
        }
        for (ResourcePolicy policy : policies) {
            sb.append(String.format("      id=%s group=%s action=%s rpType=%s rpName=%s start=%s end=%s valid=%s",
                            policy.getID(),
                            policy.getGroup() == null ? "<none>" : policy.getGroup().getName(),
                            Constants.actionText[policy.getAction()],
                            policy.getRpType(),
                            policy.getRpName(),
                            policy.getStartDate(),
                            policy.getEndDate(),
                            resourcePolicyService.isDateValid(policy)))
              .append(System.lineSeparator());
        }

        diagnostics.append(sb);
        System.out.print(sb);
    }

    /**
     * Calendar day of a start date. {@code ResourcePolicy.startDate} is mapped as {@code @Temporal(DATE)}, so
     * after a round trip through the database it comes back as a day-granular {@code java.sql.Date}.
     */
    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Calendar "today" in UTC - all embargo arithmetic is done in UTC calendar days. */
    private LocalDate utcToday() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * The next 1 July after today. Ireland is on UTC+1 then, which makes midnight UTC and midnight in the
     * harness time zone distinguishable.
     */
    private LocalDate nextIrishSummerTimeDay() {
        LocalDate today = utcToday();
        LocalDate candidate = LocalDate.of(today.getYear(), 7, 1);
        if (!candidate.isAfter(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }

    private void ensureMetadataFieldExists(String element, String qualifier) throws Exception {
        MetadataSchema dcSchema = metadataSchemaService.find(context, "dc");
        MetadataField existingField = metadataFieldService.findByElement(context, dcSchema, element, qualifier);
        if (existingField == null) {
            MetadataFieldBuilder.createMetadataField(context, dcSchema, element, qualifier, null).build();
        }
    }

    private Item createItem(String title, String... metadataTriples) throws Exception {
        context.turnOffAuthorisationSystem();
        ItemBuilder builder = ItemBuilder.createItem(context, collection).withTitle(title);
        for (int i = 0; i + 2 < metadataTriples.length; i += 3) {
            builder.withMetadata("dc", metadataTriples[i], metadataTriples[i + 1], metadataTriples[i + 2]);
        }
        Item item = builder.build();
        context.restoreAuthSystemState();
        return item;
    }

    private Bitstream createOriginalBitstream(Item item, String name) throws Exception {
        context.turnOffAuthorisationSystem();
        Bitstream bitstream = BitstreamBuilder.createBitstream(context, item,
                        new ByteArrayInputStream(("content-" + name).getBytes(StandardCharsets.UTF_8)))
                .withName(name)
                .withMimeType("text/plain")
                .build();
        context.restoreAuthSystemState();
        return bitstream;
    }

    private List<String> metadataValues(Item item, String element, String qualifier) {
        return itemService.getMetadata(item, "dc", element, qualifier, Item.ANY).stream()
                .map(MetadataValue::getValue)
                .collect(Collectors.toList());
    }

    private String firstMetadataValue(Item item, String element, String qualifier) {
        List<String> values = metadataValues(item, element, qualifier);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Runs itemupdate with both embargo fields as targets, the combination that triggers embargo
     * synchronisation. {@code main()} is not used because it ends in {@code System.exit}.
     *
     * @return everything {@code ItemUpdate.pr()} printed during the run; the stream is teed, so the output still
     *         reaches the failsafe output file as well.
     */
    private String runItemUpdate(Item item, String dublinCoreContent) throws Exception {
        return runItemUpdate(item, dublinCoreContent, 0);
    }

    /**
     * Same run, for the scenarios {@code itemupdate} has to refuse.
     *
     * @param expectedEmbargoSyncFailures number of embargo problems the run has to count; anything but 0 makes
     *                                    {@code ItemUpdate.main()} exit with 1
     */
    private String runItemUpdate(Item item, String dublinCoreContent, int expectedEmbargoSyncFailures)
            throws Exception {
        Path sourceRoot = Files.createDirectory(tempDir.resolve("saf-" + System.nanoTime()));
        // Without suppress_undo, processArchive writes an undo archive as a SIBLING of the source directory.
        Files.createFile(sourceRoot.resolve(ItemUpdate.SUPPRESS_UNDO_FILENAME));

        Path itemDir = Files.createDirectory(sourceRoot.resolve("item_000"));
        Files.writeString(itemDir.resolve("dublin_core.xml"), dublinCoreContent, StandardCharsets.UTF_8);

        ItemUpdate itemUpdate = new ItemUpdate();
        DeleteMetadataAction deleteAction =
                (DeleteMetadataAction) itemUpdate.actionMgr.getUpdateAction(DeleteMetadataAction.class);
        deleteAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        AddMetadataAction addAction =
                (AddMetadataAction) itemUpdate.actionMgr.getUpdateAction(AddMetadataAction.class);
        addAction.addTargetFields(new String[] { "dc.rights.access", "dc.date.embargoend" });

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, captured), true,
                StandardCharsets.UTF_8.name()));
        context.turnOffAuthorisationSystem();
        try {
            itemUpdate.processArchive(context, sourceRoot.toString(), null, null, true, false, true);
        } finally {
            context.restoreAuthSystemState();
            System.out.flush();
            System.setOut(originalOut);
        }

        context.uncacheEntity(item);
        String consoleOutput = captured.toString(StandardCharsets.UTF_8.name());

        assertEquals("wrong number of reported embargo synchronisation problems - ItemUpdate.main() would exit"
                        + " with " + ItemUpdate.exitStatus(0, itemUpdate.embargoSyncFailures) + " instead of "
                        + ItemUpdate.exitStatus(0, expectedEmbargoSyncFailures) + ", and the exit code is the"
                        + " only thing an operator scripting itemupdate ever sees. Console output was:\n"
                        + consoleOutput,
                expectedEmbargoSyncFailures, itemUpdate.embargoSyncFailures);

        return consoleOutput;
    }

    /**
     * Builds a SAF {@code dublin_core.xml}. {@code ItemArchive.create} resolves the item by
     * {@code dc.identifier.uri == ItemUpdate.HANDLE_PREFIX + handle}.
     *
     * @param rightsAccess    value for {@code dc.rights.access}, or {@code null} to omit the element entirely
     * @param embargoEndDates zero or more {@code dc.date.embargoend} values, emitted in the given order
     */
    private String dublinCore(Item item, String rightsAccess, String... embargoEndDates) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<dublin_core schema=\"dc\">\n")
          .append("    <dcvalue element=\"identifier\" qualifier=\"uri\">")
          .append(ItemUpdate.HANDLE_PREFIX).append(item.getHandle())
          .append("</dcvalue>\n");

        if (rightsAccess != null) {
            sb.append("    <dcvalue element=\"rights\" qualifier=\"access\">")
              .append(rightsAccess)
              .append("</dcvalue>\n");
        }

        for (String embargoEndDate : embargoEndDates) {
            if (embargoEndDate == null) {
                continue;
            }
            sb.append("    <dcvalue element=\"date\" qualifier=\"embargoend\">")
              // an empty XML element is dropped by the parser, a single space survives as a blank value
              .append(embargoEndDate.isEmpty() ? " " : embargoEndDate)
              .append("</dcvalue>\n");
        }

        sb.append("</dublin_core>");
        return sb.toString();
    }
}
