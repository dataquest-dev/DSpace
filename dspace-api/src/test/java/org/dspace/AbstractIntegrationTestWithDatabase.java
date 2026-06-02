/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authority.AuthoritySearchService;
import org.dspace.authority.MockAuthoritySolrServiceImpl;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.EPersonBuilder;
import org.dspace.content.Community;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.discovery.MockSolrSearchCore;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.kernel.ServiceManager;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.statistics.MockSolrLoggerServiceImpl;
import org.dspace.statistics.MockSolrStatisticsCore;
import org.dspace.statistics.SolrStatisticsCore;
import org.dspace.storage.rdbms.DatabaseUtils;
import org.jdom2.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Abstract Test class that will initialize the in-memory database
 */
public class AbstractIntegrationTestWithDatabase extends AbstractDSpaceIntegrationTest {
    /**
     * log4j category
     */
    private static final Logger log = LogManager
        .getLogger(AbstractIntegrationTestWithDatabase.class);

    /**
     * Context mock object to use in the tests.
     */
    protected Context context;

    /**
     * EPerson mock object to use in the tests.
     */
    protected EPerson eperson;

    /**
     * EPerson mock object in the Administrators group to use in the tests.
     */
    protected EPerson admin;

    /**
     * The password of our test eperson
     */
    protected String password = "mySuperS3cretP4ssW0rd";

    /**
     * The test Parent Community
     */
    protected Community parentCommunity = null;

    /**
     * This method will be run before the first test as per @BeforeClass. It will
     * initialize shared resources required for all tests of this class.
     * <p>
     * NOTE: Per JUnit, "The @BeforeClass methods of superclasses will be run before those the current class."
     * http://junit.org/apidocs/org/junit/BeforeClass.html
     * <p>
     * This method builds on the initialization in AbstractDSpaceIntegrationTest, and
     * initializes the in-memory database for tests that need it.
     */
    @BeforeClass
    public static void initDatabase() {
        try {
            // Update/Initialize the database to latest version (via Flyway)
            DatabaseUtils.updateDatabase();
        } catch (SQLException se) {
            log.error("Error initializing database", se);
            fail("Error initializing database: " + se.getMessage()
                     + (se.getCause() == null ? "" : ": " + se.getCause().getMessage()));
        }
    }

    /**
     * This method will be run before every test as per @Before. It will
     * initialize resources required for each individual unit test.
     *
     * Other methods can be annotated with @Before here or in subclasses
     * but no execution order is guaranteed
     */
    @Before
    public void setUp() throws Exception {
        try {
            //Start a new context
            context = new Context(Context.Mode.READ_WRITE);
            context.turnOffAuthorisationSystem();

            //Find our global test EPerson account. If it doesn't exist, create it.
            EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
            eperson = ePersonService.findByEmail(context, "test@email.com");
            if (eperson == null) {
                // Create test EPerson for usage in all tests
                log.info("Creating Test EPerson (email=test@email.com) for Integration Tests");
                eperson = EPersonBuilder.createEPerson(context)
                                        .withNameInMetadata("first", "last")
                                        .withEmail("test@email.com")
                                        .withCanLogin(true)
                                        .withLanguage(I18nUtil.getDefaultLocale().getLanguage())
                                        .withPassword(password)
                                        .build();
            }
            // Set our global test EPerson as the current user in DSpace
            context.setCurrentUser(eperson);

            // If our Anonymous/Administrator groups aren't initialized, initialize them as well
            EPersonServiceFactory.getInstance().getGroupService().initDefaultGroupNames(context);

            admin = ePersonService.findByEmail(context, "admin@email.com");
            if (admin == null) {
                // Create test Administrator for usage in all tests
                log.info("Creating Test Admin EPerson (email=admin@email.com) for Integration Tests");
                admin = EPersonBuilder.createEPerson(context)
                                      .withNameInMetadata("first (admin)", "last (admin)")
                                      .withEmail("admin@email.com")
                                      .withCanLogin(true)
                                      .withLanguage(I18nUtil.getDefaultLocale().getLanguage())
                                      .withPassword(password)
                                      .build();

                // Add Test Administrator to the ADMIN group in test database
                GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
                Group adminGroup = groupService.findByName(context, Group.ADMIN);
                groupService.addMember(context, adminGroup, admin);
            }

            context.restoreAuthSystemState();
        } catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
            fail("SQL Error on AbstractUnitTest init()");
        }
    }

    /**
     * This method will be run after every test as per @After. It will
     * clean resources initialized by the @Before methods.
     *
     * Other methods can be annotated with @After here or in subclasses
     * but no execution order is guaranteed.
     *
     * @throws java.lang.Exception passed through.
     */
    @After
    public void destroy() throws Exception {
        // Cleanup our global context object
        try {
            // Builders/cleanupContext commit transactions through Hibernate. We have observed a rare,
            // CI-only intermittent ConcurrentModificationException thrown from
            // ResourceRegistryStandardImpl#releaseResources (a HashMap.forEach over the JDBC resource
            // registry) while committing here. That registry is per-Hibernate-Session and explicitly NOT
            // thread-safe, so the CME means a second thread is touching the same Session concurrently
            // during cleanup. The exact offending thread has not yet been identified (it does not
            // reproduce locally), so on CME we (a) capture a full thread dump to pinpoint the colliding
            // thread the next time it happens in CI, and (b) retry the cleanup so an already-passed test
            // is not failed by this teardown race. See dumpAllThreadsOnCme().
            final int maxCleanupAttempts = 3;
            boolean cleanupComplete = false;
            for (int cleanupAttempt = 1; cleanupAttempt <= maxCleanupAttempts; cleanupAttempt++) {
                try {
                    AbstractBuilder.cleanupObjects();
                    parentCommunity = null;
                    cleanupContext();
                    cleanupComplete = true;
                    break;
                } catch (ConcurrentModificationException cme) {
                    // Capture a full thread dump the instant the CME is caught, so we can see which OTHER
                    // thread is concurrently inside JDBC/Hibernate code on the same (non-thread-safe) session.
                    dumpAllThreadsOnCme(cme, cleanupAttempt);
                    log.warn("Transient Hibernate CME during @After cleanup (concurrent access to the "
                            + "per-session JDBC resource registry), attempt {}/{}; aborting context, capturing a "
                            + "thread dump and retrying cleanup.", cleanupAttempt, maxCleanupAttempts, cme);
                    if (context != null && context.isValid()) {
                        context.abort();
                    }
                    context = null;
                    parentCommunity = null;

                    if (cleanupAttempt < maxCleanupAttempts) {
                        context = new Context(Context.Mode.READ_WRITE);
                        context.turnOffAuthorisationSystem();
                    }
                }
            }

            if (!cleanupComplete) {
                throw new IllegalStateException("Unable to complete @After DB cleanup after retries.");
            }

            ServiceManager serviceManager = DSpaceServicesFactory.getInstance().getServiceManager();
            // Clear the search core.
            MockSolrSearchCore searchService = serviceManager
                    .getServiceByName(null, MockSolrSearchCore.class);
            searchService.reset();
            // Clear the statistics core.
            serviceManager
                    .getServiceByName(SolrStatisticsCore.class.getName(), MockSolrStatisticsCore.class)
                    .reset();

            MockSolrLoggerServiceImpl statisticsService = serviceManager
                    .getServiceByName("solrLoggerService", MockSolrLoggerServiceImpl.class);
            statisticsService.reset();

            MockAuthoritySolrServiceImpl authorityService = serviceManager
                    .getServiceByName(AuthoritySearchService.class.getName(), MockAuthoritySolrServiceImpl.class);
            authorityService.reset();

            // Reload our ConfigurationService (to reset configs to defaults again)
            DSpaceServicesFactory.getInstance().getConfigurationService().reloadConfig();

            AbstractBuilder.cleanupBuilderCache();

            // NOTE: we explicitly do NOT destroy our default eperson & admin as they
            // are cached and reused for all tests. This speeds up all tests.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Counter to give each captured dump a unique file name.
    private static final AtomicInteger CME_DUMP_COUNTER = new AtomicInteger(0);

    /**
     * Diagnostic helper: when a ConcurrentModificationException is caught during @After cleanup, dump the
     * stack traces of ALL live threads to a file under target/cme-dumps/ (archived as a CI artifact). The
     * CME is thrown on the test thread while another thread concurrently mutates the same Hibernate
     * session's (non-thread-safe) JDBC ResourceRegistry; this dump is meant to reveal that other thread so
     * the underlying concurrency bug can be fixed at its source.
     */
    private static void dumpAllThreadsOnCme(ConcurrentModificationException cme, int attempt) {
        try {
            File dir = new File("target/cme-dumps");
            dir.mkdirs();
            int idx = CME_DUMP_COUNTER.incrementAndGet();
            File out = new File(dir, "cme-" + System.currentTimeMillis() + "-" + idx + "-attempt" + attempt + ".txt");
            try (PrintWriter pw = new PrintWriter(out, "UTF-8")) {
                pw.println("===== ConcurrentModificationException caught during @After cleanup =====");
                pw.println("Caught on thread: " + Thread.currentThread().getName());
                pw.println("Attempt: " + attempt);
                pw.println();
                pw.println("----- CME stack -----");
                cme.printStackTrace(pw);
                pw.println();
                pw.println("----- ALL THREAD STACKS at moment of CME -----");
                for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                    Thread t = e.getKey();
                    pw.println();
                    pw.println("\"" + t.getName() + "\" id=" + t.getId() + " state=" + t.getState()
                            + " daemon=" + t.isDaemon());
                    for (StackTraceElement ste : e.getValue()) {
                        pw.println("\tat " + ste);
                    }
                }
            }
            log.error("CME thread dump written to {}", out.getAbsolutePath());
        } catch (Exception dumpEx) {
            log.error("Failed to write CME thread dump", dumpEx);
        }
    }

    /**
     * Utility method to cleanup a created Context object (to save memory).
     * This can also be used by individual tests to cleanup context objects they create.
     * @throws java.sql.SQLException passed through.
     */
    protected void cleanupContext() throws SQLException {
        // If context still valid, flush all database changes and close it
        if (context != null && context.isValid()) {
            context.complete();
        }

        // Cleanup Context object by setting it to null
        if (context != null) {
            context = null;
        }
    }

    /**
     * Execute the given command and return the exit code.
     *
     * @param args the args to use for the script.
     * @return the status, 0 if success, non-zero otherwise.
     * @throws Exception if there's an error cleaning up after running the command.
     */
    public int runDSpaceScript(String... args) throws Exception {
        try {
            // Load up the ScriptLauncher's configuration
            Document commandConfigs = ScriptLauncher.getConfig(kernelImpl);

            // Check that there is at least one argument (if not display command options)
            if (args.length < 1) {
                log.error("You must provide at least one command argument");
            }

            // Look up command in the configuration, and execute.
            TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
            int status =  ScriptLauncher.handleScript(args, commandConfigs, testDSpaceRunnableHandler, kernelImpl);
            if (testDSpaceRunnableHandler.getException() != null) {
                throw testDSpaceRunnableHandler.getException();
            } else {
                return status;
            }
        } finally {
            if (!context.isValid()) {
                setUp();
            }
        }
    }
}
