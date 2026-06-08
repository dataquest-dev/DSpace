/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TEST DIAGNOSTIC (temporary) for the rare Hibernate {@code ConcurrentModificationException} thrown from
 * {@code ResourceRegistryStandardImpl.releaseResources} during {@code @After} cleanup
 * (see {@link AbstractIntegrationTestWithDatabase#destroy()}).
 *
 * <p>That CME provably requires a SECOND thread to mutate the test thread's per-session, non-thread-safe JDBC
 * resource registry while the test thread commits/rolls back. A live thread-dump of a running IT JVM shows that
 * NO legitimate background thread ever touches Hibernate (every persistent thread is Solr, HTTP-client, Jetty or
 * a JVM thread). Therefore <b>any</b> non-test thread caught executing inside Hibernate JDBC / session code is,
 * by definition, the culprit.</p>
 *
 * <p>This monitor is a JVM-wide background sampler. Every {@link #SAMPLE_INTERVAL_MS} ms it snapshots all thread
 * stacks and records (de-duplicated) any non-test, non-monitor thread found inside
 * {@code org.hibernate.resource.jdbc}, {@code org.hibernate.engine.jdbc} or {@code org.hibernate.internal.SessionImpl}.
 * Records are flushed to {@code target/cme-dumps/} on a captured CME and at JVM shutdown. It is a pure observer:
 * it never touches Hibernate, never throws into test code, and never changes behaviour. Delete once the culprit
 * thread has been identified and fixed at its source.</p>
 */
public final class HibernateConcurrencyMonitor {

    private static final long SAMPLE_INTERVAL_MS = 20;

    /** Thread ids of legitimate test threads (the JUnit thread(s)) to ignore. */
    private static final Set<Long> TEST_THREAD_IDS = ConcurrentHashMap.newKeySet();

    /** De-duplicated culprit fingerprints: key -> formatted record. */
    private static final Map<String, String> CULPRITS = new ConcurrentHashMap<>();

    private static volatile boolean started;

    private HibernateConcurrencyMonitor() {
    }

    /** Start the monitor exactly once per JVM (fork). Safe to call from every test's setUp. */
    public static synchronized void startOnce() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(HibernateConcurrencyMonitor::loop, "hibernate-concurrency-monitor");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> flush("jvm-shutdown"), "hibernate-concurrency-flush"));
    }

    /** Mark the current thread as a legitimate test thread, so its (normal) Hibernate use is ignored. */
    public static void markTestThread() {
        TEST_THREAD_IDS.add(Thread.currentThread().getId());
    }

    private static void loop() {
        final long monitorId = Thread.currentThread().getId();
        while (true) {
            try {
                Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
                for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
                    Thread th = e.getKey();
                    if (th.getId() == monitorId || TEST_THREAD_IDS.contains(th.getId())) {
                        continue;
                    }
                    if (touchesHibernateJdbc(e.getValue())) {
                        record(th, e.getValue());
                    }
                }
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable ignore) {
                // A diagnostic must never die from a transient error (e.g. a thread terminating mid-snapshot).
            }
        }
    }

    private static boolean touchesHibernateJdbc(StackTraceElement[] stack) {
        for (StackTraceElement f : stack) {
            String c = f.getClassName();
            if (c.startsWith("org.hibernate.resource.jdbc")
                    || c.startsWith("org.hibernate.engine.jdbc")
                    || c.startsWith("org.hibernate.internal.SessionImpl")) {
                return true;
            }
        }
        return false;
    }

    private static void record(Thread th, StackTraceElement[] stack) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(stack.length, 25);
        for (int i = 0; i < n; i++) {
            sb.append("\tat ").append(stack[i]).append('\n');
        }
        String stackText = sb.toString();
        String key = th.getName() + "|" + Integer.toHexString(stackText.hashCode());
        CULPRITS.putIfAbsent(key, "\"" + th.getName() + "\" id=" + th.getId()
                + " daemon=" + th.isDaemon() + " state=" + th.getState()
                + " group=" + (th.getThreadGroup() == null ? "?" : th.getThreadGroup().getName()) + "\n" + stackText);
    }

    /** Write all captured culprit fingerprints to target/cme-dumps/ (no-op if none were caught). */
    public static void flush(String reason) {
        if (CULPRITS.isEmpty()) {
            return;
        }
        try {
            File dir = new File("target/cme-dumps");
            dir.mkdirs();
            File out = new File(dir, "hibernate-concurrency-" + System.currentTimeMillis() + "-" + reason + ".txt");
            try (PrintWriter pw = new PrintWriter(out, "UTF-8")) {
                pw.println("===== Non-test threads caught INSIDE Hibernate JDBC / session code =====");
                pw.println("reason=" + reason + " distinctFingerprints=" + CULPRITS.size());
                pw.println("Baseline: NO legitimate background thread touches Hibernate, so each entry below is a");
                pw.println("suspect for the @After ConcurrentModificationException (concurrent access to the test");
                pw.println("thread's non-thread-safe per-session JDBC ResourceRegistry).");
                pw.println();
                for (String rec : CULPRITS.values()) {
                    pw.println(rec);
                    pw.println("------------------------------------------------------------");
                }
            }
        } catch (Exception ignore) {
            // best-effort diagnostic
        }
    }
}
