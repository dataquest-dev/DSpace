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
 * Date-boundary and baseline behaviour of the VSB-TUO embargo synchronisation in {@link ItemUpdate}.
 *
 * <p>Every scenario models the customer workflow literally: a SAF archive is re-imported with
 * {@code ItemUpdate -s SAFDIR -d dc.rights.access -d dc.date.embargoend -a dc.rights.access
 * -a dc.date.embargoend}, which is what makes {@code processArchive} call {@code syncEmbargoPolicies}.</p>
 *
 * <p>The binding rules under test (VSB-TUO embargo specification):</p>
 * <ul>
 *   <li>the resulting {@code Anonymous}/{@code READ} policy on every ORIGINAL bitstream starts at
 *       {@code dc.date.embargoend + 1 day} at <em>midnight UTC</em>, because {@code dc.date.embargoend}
 *       is the inclusive last day of the embargo;</li>
 *   <li>there is always exactly one such policy - never zero (the file would answer HTTP 401) and never
 *       two (an undated one would silently neutralise the embargo);</li>
 *   <li>the policy is normalised to {@code rpType=TYPE_CUSTOM} and {@code rpName="embargo"};</li>
 *   <li>an embargo end date that already lies in the past is a <em>publication</em>, not a deletion.</li>
 * </ul>
 *
 * <p>All dates are derived from {@code LocalDate.now(ZoneOffset.UTC)}, never hard-coded, so the suite cannot
 * become a time bomb (lesson of PR #1359) and cannot straddle the UTC/Europe-Dublin day boundary.</p>
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
     * Row 1 of the specification: a future {@code dc.date.embargoend} closes the file and leaves exactly one
     * normalised {@code Anonymous}/{@code READ} policy starting the day after the embargo end date.
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
     * Row 2 of the specification: {@code dc.date.embargoend} is the <em>inclusive</em> last day of the embargo.
     * When it equals today the file must still be closed today and open only tomorrow, so the policy start date
     * is tomorrow - the "+1 day" has to be applied before, not after, any past/future comparison.
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
     * Row 3 of the specification: an embargo that ended yesterday is expired, which means the file is published.
     * The policy must survive with a start date of today, be date-valid and let anonymous visitors in.
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
     * Main regression test - the exact record observed on dspace7-test.vsb.cz (item
     * 4dde91f3-7078-4241-9938-9b8488623bb1: {@code dc.date.embargoend} in the past,
     * {@code dc.rights.access=openAccess}, yet HTTP 401 on all 18 bitstreams).
     *
     * <p>The priming run with a future date is mandatory: it is what replaces the inherited collection default
     * with a single dated policy, so that the following past-date run has exactly one policy left to destroy.</p>
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

        // (b) the state the customer confirmed as working: an embargo with a future end date
        runItemUpdate(item, dublinCore(item, "embargoedAccess", futureEnd.toString()));
        item = context.reloadEntity(item);
        bitstream = context.reloadEntity(bitstream);
        dump("STEP B - after itemupdate with FUTURE dc.date.embargoend=" + futureEnd, bitstream);
        assertFalse("fixture precondition: while embargoed until " + futureEnd + " the ORIGINAL bitstream must"
                        + " not be publicly readable." + diagnostics,
                anonymousCanRead(bitstream));

        // (c) the operator lets the embargo expire: past end date, item declared openAccess
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
     * Same as {@link #pastEmbargoEndWithOpenAccessOpensAccess()} but the item keeps
     * {@code dc.rights.access=embargoedAccess}. An expired embargo publishes the file regardless: the access
     * right only names the licence regime, the end date decides when it lapses.
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
     * The policy start date must be built as {@code Date.from(day.atStartOfDay(ZoneOffset.UTC).toInstant())},
     * i.e. midnight UTC of a calendar day, and never midnight in the JVM time zone as produced by
     * {@code Calendar.getInstance()} or {@code java.sql.Date.valueOf(LocalDate)}.
     *
     * <p>The harness pins the JVM to Europe/Dublin, which is UTC+1 during Irish Summer Time, so the two
     * candidates differ by exactly one hour. The embargo end date is therefore anchored to the next 1 July -
     * still computed from today, so fully dynamic - which is guaranteed to fall inside Irish Summer Time and
     * makes the difference observable all year round.</p>
     *
     * <p>{@code syncEmbargoPolicies} is invoked directly rather than through {@code processArchive} on purpose:
     * {@code processArchive} ends with {@code context.uncacheEntity(item)}, which evicts the bitstream policies,
     * so they would come back from the day-granular {@code @Temporal(DATE)} column as a {@code java.sql.Date}
     * and the exact instant could no longer be inspected.</p>
     */
    @Test
    public void startDateIsUtcMidnightNotServerZone() throws Exception {
        LocalDate embargoEnd = nextIrishSummerTimeDay();
        LocalDate expectedStartDay = embargoEnd.plusDays(1);

        Date expectedUtcMidnight = Date.from(expectedStartDay.atStartOfDay(ZoneOffset.UTC).toInstant());
        Date serverZoneMidnight = Date.from(expectedStartDay.atStartOfDay(ZoneId.systemDefault()).toInstant());
        assertNotEquals("fixture precondition: the JVM time zone (" + ZoneId.systemDefault() + ") must differ from"
                        + " UTC on " + expectedStartDay + ", otherwise this test cannot tell midnight UTC apart"
                        + " from midnight in the server zone. The harness pins Europe/Dublin in"
                        + " AbstractDSpaceIntegrationTest.",
                expectedUtcMidnight, serverZoneMidnight);

        Item item = createItem("UTC midnight embargo",
                "rights", "access", "embargoedAccess",
                "date", "embargoend", embargoEnd.toString());
        Bitstream bitstream = createOriginalBitstream(item, "utc-midnight.pdf");
        dump("STEP A - fresh SAF import, before any itemupdate", bitstream);
        assertFreshImportBaseline(bitstream);
        Integer importedPolicyId = anonymousReadPolicies(bitstream).get(0).getID();

        context.turnOffAuthorisationSystem();
        try {
            new ItemUpdate().syncEmbargoPolicies(context, item);
        } finally {
            context.restoreAuthSystemState();
        }
        dump("STEP B - after syncEmbargoPolicies with dc.date.embargoend=" + embargoEnd, bitstream);

        ResourcePolicy policy = assertExactlyOneAnonymousReadPolicy("dc.date.embargoend=" + embargoEnd, bitstream);
        assertNotNull("resource policy #" + policy.getID() + " must carry a start date." + diagnostics,
                policy.getStartDate());
        assertEquals("the inherited Anonymous/READ policy must be MUTATED in place, not deleted and recreated:"
                        + " a policy may never be removed before its replacement is stored, otherwise a failure"
                        + " between the two leaves the file with zero policies (HTTP 401)." + diagnostics,
                importedPolicyId, policy.getID());

        long actualMillis = policy.getStartDate().getTime();
        assertEquals("dc.date.embargoend=" + embargoEnd + " must yield a start date of exactly midnight UTC on "
                        + expectedStartDay + " (epochMillis=" + expectedUtcMidnight.getTime() + "). Midnight in"
                        + " the server time zone " + ZoneId.systemDefault() + " would be epochMillis="
                        + serverZoneMidnight.getTime() + ", which is what Calendar.getInstance() or"
                        + " java.sql.Date.valueOf(LocalDate) produce. Actual epochMillis=" + actualMillis + " ("
                        + new Date(actualMillis).toInstant().atZone(ZoneOffset.UTC) + ")." + diagnostics,
                expectedUtcMidnight.getTime(), actualMillis);

        assertFalse("dc.date.embargoend=" + embargoEnd + " lies in the future, so an anonymous visitor must NOT"
                        + " be able to download the ORIGINAL bitstream." + diagnostics,
                anonymousCanRead(bitstream));
        assertEquals("resource policy #" + policy.getID() + " must be normalised to rpType="
                        + ResourcePolicy.TYPE_CUSTOM + "." + diagnostics,
                ResourcePolicy.TYPE_CUSTOM, policy.getRpType());
        assertEquals("resource policy #" + policy.getID() + " must be normalised to rpName=\""
                        + EMBARGO_POLICY_NAME + "\"." + diagnostics,
                EMBARGO_POLICY_NAME, policy.getRpName());
    }

    /**
     * Row 12 of the specification: several {@code dc.date.embargoend} values are a data error the operator has
     * to see, but the run still completes and uses the first value.
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

    // -----------------------------------------------------------------------------------------------
    // legacy dc.date.embargoend shapes
    // -----------------------------------------------------------------------------------------------

    /**
     * The value shapes {@code DCDate} used to accept still have to work here, and have to mean the same day
     * here as on the import path. {@code DCDate.toDate()} reported the <em>first</em> instant of a year or a
     * month, so {@code 2099} has always meant 1 January 2099 and {@code 2027-05} 1 May 2027, never the end of
     * the period; a timestamp was truncated to its UTC day.
     *
     * <p>The same SAF package is first fed to {@code dspace import} and later re-fed to {@code dspace
     * itemupdate}, so a disagreement between the two tools is a file one of them closes and the other opens.
     * The import-side mirror of this test is {@code EmbargoImportIT#testYearOnlyEmbargoEndIsFirstOfJanuary}
     * and its two neighbours.</p>
     */
    @Test
    public void legacyEmbargoEndShapesKeepTheirDcDateDay() throws Exception {
        int nextYear = utcToday().getYear() + 1;
        LocalDate tomorrow = utcToday().plusDays(1);

        // a bare year is 1 January of it - not 31 December, which would extend embargoes operators live with
        assertLegacyEmbargoEndClosesTheFileUntil(String.valueOf(nextYear), LocalDate.of(nextYear, 1, 1));
        // a bare month is the 1st of it, for the same reason
        assertLegacyEmbargoEndClosesTheFileUntil(nextYear + "-05", LocalDate.of(nextYear, 5, 1));
        // the shape every DSpace export writes; the time of day is dropped, the UTC day is the last closed day
        assertLegacyEmbargoEndClosesTheFileUntil(tomorrow + "T00:00:00Z", tomorrow);
    }

    /**
     * A legacy shape whose day lies in the <em>past</em> publishes the file, and that is a decision, not an
     * accident. {@code 2020} says the embargo ended in 2020, so the files are public - exactly as a written
     * out {@code 2020-01-01} would be. Until the {@code DCDate} shapes were read again, such a value threw and
     * the item was refused; that refusal was a side effect of strict parsing and not what the metadata says.
     *
     * <p>This is the one test in the class that watches a file being opened by a legacy value, so it asserts
     * the whole outcome: one immediately effective policy, an anonymous visitor who really gets the file, and
     * a run that reports no problem ({@link #runItemUpdate} checks the last part).</p>
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
     * Reading the {@code DCDate} shapes is not the same as swallowing what {@code DCDate} swallowed.
     * {@code SimpleDateFormat} took {@code 2099garbage} for the year 2099, and {@code DCDate} ignored a numeric
     * UTC offset instead of applying it - both move an embargo boundary silently. They are refused, and a
     * refusal has to leave every policy exactly where it was and fail the run: an operator scripting
     * {@code itemupdate} sees nothing but the exit code.
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
     * One legacy value that has to close the file until exactly the day {@code DCDate} mapped it to.
     *
     * @param legacyValue        raw {@code dc.date.embargoend} as a legacy SAF package writes it
     * @param expectedEmbargoEnd last closed day {@code DCDate} mapped that value to; has to be in the future
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
     * One value that has to be refused: the policies of an embargoed file stay byte for byte what they were and
     * the run counts a failure, so {@code ItemUpdate.main()} exits non-zero.
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

    // -----------------------------------------------------------------------------------------------
    // assertions
    // -----------------------------------------------------------------------------------------------

    /**
     * A bitstream created by {@code BitstreamBuilder} inherits the collection DEFAULT_BITSTREAM_READ, which is
     * byte-for-byte the state a fresh SAF import leaves behind: one Anonymous/READ policy without a start date.
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
     * Zero policies means HTTP 401 on the file; more than one means an undated policy can coexist with the dated
     * one and silently neutralise the embargo. Exactly one is the only acceptable outcome.
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
     * The surviving policy has to be normalised: {@code TYPE_CUSTOM} (AuthorizeServiceImpl only honours custom
     * policies on not-yet-installed items), {@code rpName="embargo"} (the access condition name used by
     * access-conditions.xml, short enough for the 30 character column) and a start date of
     * {@code dc.date.embargoend + 1 day}.
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

    // -----------------------------------------------------------------------------------------------
    // policy inspection helpers
    // -----------------------------------------------------------------------------------------------

    /**
     * Answers the only question that matters: may a not-logged-in visitor download the file?
     *
     * <p>The authorisation state is a stack, not a flag, and the builders as well as {@code processArchive} push
     * and pop around themselves, so the depth at assertion time is not guaranteed to be zero. The stack is
     * therefore drained (otherwise {@code AuthorizeServiceImpl.authorize} short-circuits and every read looks
     * allowed) and restored afterwards. The current user is cleared as well, because {@code setUp} leaves the
     * test EPerson logged in.</p>
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
     * Every resource policy id of the bitstream, whatever the action. Comparing ids and not counts is the point:
     * a policy deleted and immediately re-created keeps the count but loses its identity.
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
     * {@code ResourcePolicy.startDate} is mapped as {@code @Temporal(DATE)}, so once it has been round-tripped
     * through the database it comes back as a day-granular {@code java.sql.Date}. Compare calendar days, never
     * {@code Date} instances, across the UTC/Europe-Dublin boundary.
     */
    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    // -----------------------------------------------------------------------------------------------
    // fixture helpers
    // -----------------------------------------------------------------------------------------------

    /** Calendar "today" in UTC - the specification does all embargo arithmetic in UTC calendar days. */
    private LocalDate utcToday() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * The next 1 July strictly after today, computed dynamically. Ireland observes Irish Summer Time (UTC+1) on
     * that date every year, which is what makes midnight UTC and midnight in the server zone distinguishable.
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
     * Equivalent of {@code ItemUpdate -s SAFDIR -d dc.rights.access -d dc.date.embargoend -a dc.rights.access
     * -a dc.date.embargoend}: an update whose target fields contain an embargo field, which is exactly what
     * makes {@code processArchive} call {@code syncEmbargoPolicies}.
     *
     * <p>{@code ItemUpdate.main} is deliberately not used - it ends in {@code System.exit} and would kill the
     * failsafe JVM.</p>
     *
     * <p>Every scenario in this class is one {@code itemupdate} is supposed to carry out, so the helper also
     * asserts the exit code the run would have produced: {@code embargoSyncFailures} is the only thing
     * {@code main()} turns into a non-zero status, and a refusal that keeps the status at 0 is a silent
     * failure for the operator's script.</p>
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
     * @param expectedEmbargoSyncFailures number of embargo problems the run has to count; anything but 0 means
     *                                    {@code ItemUpdate.main()} would exit with 1
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
