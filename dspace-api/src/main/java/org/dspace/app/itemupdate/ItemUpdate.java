/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemupdate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.util.SafEmbargoConstants;
import org.dspace.app.util.SafEmbargoDateParser;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;

/**
 * Provides some batch editing capabilities for items in DSpace.
 * <ul>
 *   <li>Metadata fields - Add, Delete</li>
 *   <li>Bitstreams - Add, Delete</li>
 * </ul>
 *
 * <p>
 * The design has been for compatibility with
 * {@link org.dspace.app.itemimport.service.ItemImportService}
 * in the use of the DSpace archive format which is used to
 * specify changes on a per item basis.  The directory names
 * to correspond to each item are arbitrary and will only be
 * used for logging purposes.  The reference to the item is
 * from a required {@code dc.identifier} with the item handle to be
 * included in the {@code dublin_core.xml} (or similar metadata) file.
 *
 * <p>
 * Any combination of these actions is permitted in a single run of this class.
 * The order of actions is important when used in combination.
 * It is the responsibility of the calling class (here, {@code ItemUpdate})
 * to register {@link UpdateAction} classes in the order which they are
 * to be performed.
 *
 * <p>
 * It is unfortunate that so much code needs to be borrowed from
 * {@link org.dspace.app.itemimport.service.ItemImportService} as it is not
 * reusable in private methods, etc.  Some of this has been placed into the
 * {@link MetadataUtilities} class for possible reuse elsewhere.
 *
 * @author W. Hays based on a conceptual design by R. Rodgers
 */
public class ItemUpdate {

    public static final String SUPPRESS_UNDO_FILENAME = "suppress_undo";

    public static final String CONTENTS_FILE = "contents";
    public static final String DELETE_CONTENTS_FILE = "delete_contents";

    public static String HANDLE_PREFIX = null;
    public static final Map<String, String> filterAliases = new HashMap<>();

    public static boolean verbose = false;

    protected static final EPersonService epersonService = EPersonServiceFactory.getInstance().getEPersonService();
    protected static final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected static final HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected static final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    protected static final ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
        .getResourcePolicyService();

    /** API logging; the operator console output is written by {@link #pr(String)} and its siblings. */
    private static final Logger log = LogManager.getLogger(ItemUpdate.class);

    private static final String EMBARGO_FIELD_RIGHTS_ACCESS = "dc.rights.access";
    private static final String EMBARGO_FIELD_DATE_END = "dc.date.embargoend";
    private static final String OPEN_ACCESS = "openAccess";
    private static final String EMBARGOED_ACCESS = "embargoedAccess";

    /** The only supported way of changing access to an ORIGINAL bitstream this tool refuses to touch. */
    private static final String BULK_ACCESS_CONTROL_HINT =
        "Use the 'dspace bulk-access-control' script to grant or restore access to it.";

    /** Derived bundles are out of reach of bulk-access-control, which walks the ORIGINAL bundle only. */
    private static final String DERIVATIVE_ACCESS_HINT =
        "Change its access in the administrative UI, or let 'dspace filter-media' recreate it from the"
            + " source file.";

    static {
        filterAliases.put("ORIGINAL", "org.dspace.app.itemupdate.OriginalBitstreamFilter");
        filterAliases
            .put("ORIGINAL_AND_DERIVATIVES", "org.dspace.app.itemupdate.OriginalWithDerivativesBitstreamFilter");
        filterAliases.put("TEXT", "org.dspace.app.itemupdate.DerivativeTextBitstreamFilter");
        filterAliases.put("THUMBNAIL", "org.dspace.app.itemupdate.ThumbnailBitstreamFilter");
    }

    // File listing filter to check for folders
    static FilenameFilter directoryFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String n) {
            File f = new File(dir.getAbsolutePath() + File.separatorChar + n);
            return f.isDirectory();
        }
    };

    // File listing filter to check for files (not directories)
    static FilenameFilter fileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String n) {
            File f = new File(dir.getAbsolutePath() + File.separatorChar + n);
            return (f.isFile());
        }
    };

    // instance variables
    protected ActionManager actionMgr = new ActionManager();
    protected List<String> undoActionList = new ArrayList<>();
    protected String eperson;
    /** Bitstreams and items whose embargo could not be synchronised; a non-zero count fails the run. */
    protected int embargoSyncFailures = 0;

    /**
     * @param argv the command line arguments given
     */
    public static void main(String[] argv) {
        // create an options object and populate it
        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        //processing basis for determining items
        //item-specific changes with metadata in source directory with dublin_core.xml files
        options.addOption("s", "source", true, "root directory of source dspace archive ");

        //actions  on items
        options.addOption("a", "addmetadata", true,
                          "add metadata specified for each item; multiples separated by semicolon ';'");
        options.addOption("d", "deletemetadata", true, "delete metadata specified for each item");

        options.addOption("A", "addbitstreams", false, "add bitstreams as specified for each item");

        // extra work to get optional argument
        Option delBitstreamOption = new Option("D", "deletebitstreams", true,
                                               "delete bitstreams as specified for each item");
        delBitstreamOption.setOptionalArg(true);
        delBitstreamOption.setArgName("BitstreamFilter");
        options.addOption(delBitstreamOption);

        //other params
        options.addOption("e", "eperson", true, "email of eperson doing the update");
        options.addOption("i", "itemfield", true,
                          "optional metadata field that containing item identifier; default is dc.identifier.uri");
        options.addOption("F", "filter-properties", true, "filter class name; only for deleting bitstream");
        options.addOption("v", "verbose", false, "verbose logging");

        //special run states
        options.addOption("t", "test", false, "test run - do not actually import items");
        options.addOption("P", "provenance", false, "suppress altering provenance field for bitstream changes");
        options.addOption("h", "help", false, "help");

        int status = 0;
        boolean isTest = false;
        boolean alterProvenance = true;
        String itemField = null;
        String metadataIndexName = null;
        boolean syncEmbargoPolicies = false;

        Context context = null;
        ItemUpdate iu = new ItemUpdate();

        try {
            CommandLine line = parser.parse(options, argv);

            if (line.hasOption('h')) {
                HelpFormatter myhelp = new HelpFormatter();
                myhelp.printHelp("ItemUpdate", options);
                pr("");
                pr("Examples:");
                pr("  adding metadata:     ItemUpdate -e jsmith@mit.edu -s sourcedir -a dc.contributor -a dc.subject ");
                pr("  deleting metadata:   ItemUpdate -e jsmith@mit.edu -s sourcedir -d dc.description.other");
                pr("  adding bitstreams:   ItemUpdate -e jsmith@mit.edu -s sourcedir -A -i dc.identifier");
                pr("  deleting bitstreams: ItemUpdate -e jsmith@mit.edu -s sourcedir -D ORIGINAL ");
                pr("");

                System.exit(0);
            }

            if (line.hasOption('v')) {
                verbose = true;
            }


            if (line.hasOption('P')) {
                alterProvenance = false;
                pr("Suppressing changes to Provenance field option");
            }

            iu.eperson = line.getOptionValue('e'); // db ID or email

            if (!line.hasOption('s')) { // item specific changes from archive dir
                pr("Missing source archive option");
                System.exit(1);
            }
            String sourcedir = line.getOptionValue('s');

            if (line.hasOption('t')) { //test
                isTest = true;
                pr("**Test Run** - not actually updating items.");

            }

            if (line.hasOption('i')) {
                itemField = line.getOptionValue('i');
            }

            if (line.hasOption('d')) {
                String[] targetFields = line.getOptionValues('d');
                syncEmbargoPolicies = syncEmbargoPolicies || containsEmbargoField(targetFields);

                DeleteMetadataAction delMetadataAction = (DeleteMetadataAction) iu.actionMgr
                    .getUpdateAction(DeleteMetadataAction.class);
                delMetadataAction.addTargetFields(targetFields);

                //undo is an add
                for (String field : targetFields) {
                    iu.undoActionList.add(" -a " + field + " ");
                }

                pr("Delete metadata for fields: ");
                for (String s : targetFields) {
                    pr("    " + s);
                }
            }

            if (line.hasOption('a')) {
                String[] targetFields = line.getOptionValues('a');
                syncEmbargoPolicies = syncEmbargoPolicies || containsEmbargoField(targetFields);

                AddMetadataAction addMetadataAction = (AddMetadataAction) iu.actionMgr
                    .getUpdateAction(AddMetadataAction.class);
                addMetadataAction.addTargetFields(targetFields);

                //undo is a delete followed by an add of a replace record for target fields
                for (String field : targetFields) {
                    iu.undoActionList.add(" -d " + field + " ");
                }

                for (String field : targetFields) {
                    iu.undoActionList.add(" -a " + field + " ");
                }

                pr("Add metadata for fields: ");
                for (String s : targetFields) {
                    pr("    " + s);
                }
            }

            if (line.hasOption('D')) { // undo not supported
                pr("Delete bitstreams ");

                String[] filterNames = line.getOptionValues('D');
                if ((filterNames != null) && (filterNames.length > 1)) {
                    pr("Error: Only one filter can be a used at a time.");
                    System.exit(1);
                }

                String filterName = line.getOptionValue('D');
                pr("Filter argument: " + filterName);

                if (filterName == null) { // indicates using delete_contents files
                    DeleteBitstreamsAction delAction = (DeleteBitstreamsAction) iu.actionMgr
                        .getUpdateAction(DeleteBitstreamsAction.class);
                    delAction.setAlterProvenance(alterProvenance);
                } else {
                    // check if param is on ALIAS list
                    String filterClassname = filterAliases.get(filterName);

                    if (filterClassname == null) {
                        filterClassname = filterName;
                    }

                    BitstreamFilter filter = null;

                    try {
                        Class<?> cfilter = Class.forName(filterClassname);
                        pr("BitstreamFilter class to instantiate: " + cfilter.toString());

                        filter = (BitstreamFilter) cfilter.getDeclaredConstructor()
                                .newInstance();  //unfortunate cast, an erasure consequence
                    } catch (Exception e) {
                        pr("Error:  Failure instantiating bitstream filter class: " + filterClassname);
                        System.exit(1);
                    }

                    String filterPropertiesName = line.getOptionValue('F');
                    if (filterPropertiesName != null) { //not always required
                        try {
                            // TODO try multiple relative locations, e.g. source dir
                            if (!filterPropertiesName.startsWith("/")) {
                                filterPropertiesName = sourcedir + File.separator + filterPropertiesName;
                            }

                            filter.initProperties(filterPropertiesName);
                        } catch (Exception e) {
                            pr("Error:  Failure finding properties file for bitstream filter class: " +
                                   filterPropertiesName);
                            System.exit(1);
                        }
                    }

                    DeleteBitstreamsByFilterAction delAction =
                        (DeleteBitstreamsByFilterAction) iu.actionMgr
                            .getUpdateAction(DeleteBitstreamsByFilterAction.class);
                    delAction.setAlterProvenance(alterProvenance);
                    delAction.setBitstreamFilter(filter);
                    //undo not supported
                }
            }

            if (line.hasOption('A')) {
                pr("Add bitstreams ");
                AddBitstreamsAction addAction = (AddBitstreamsAction) iu.actionMgr
                    .getUpdateAction(AddBitstreamsAction.class);
                addAction.setAlterProvenance(alterProvenance);

                iu.undoActionList.add(" -D ");  // delete_contents file will be written, no arg required
            }

            if (!iu.actionMgr.hasActions()) {
                pr("Error - an action must be specified");
                System.exit(1);
            } else {
                pr("Actions to be performed: ");

                for (UpdateAction ua : iu.actionMgr) {
                    pr("    " + ua.getClass().getName());
                }
            }

            pr("ItemUpdate - initializing run on " + (new Date()).toString());

            context = new Context(Context.Mode.BATCH_EDIT);
            iu.setEPerson(context, iu.eperson);
            context.turnOffAuthorisationSystem();

            HANDLE_PREFIX = handleService.getCanonicalPrefix();

            iu.processArchive(context, sourcedir, itemField, metadataIndexName, alterProvenance, isTest,
                              syncEmbargoPolicies);

            context.complete();  // complete all transactions
        } catch (Exception e) {
            if (context != null && context.isValid()) {
                context.abort();
            }
            e.printStackTrace();
            pr(e.toString());
            status = 1;
        } finally {
            context.restoreAuthSystemState();
        }

        status = exitStatus(status, iu.embargoSyncFailures);

        if (isTest) {
            pr("***End of Test Run***");
        } else {
            pr("End.");

        }
        System.exit(status);
    }

    /**
     * Exit code of a run. Embargo problems are reported per item without aborting the run, but a run that
     * reported them must not exit as a success: a script around this tool sees the exit code only.
     *
     * @param status              exit code the run has produced so far
     * @param embargoSyncFailures number of bitstreams/items whose embargo could not be synchronised
     * @return {@code 1} when anything went wrong, the unchanged status otherwise
     */
    protected static int exitStatus(int status, int embargoSyncFailures) {
        if (embargoSyncFailures > 0) {
            prErr(embargoSyncFailures + " embargo synchronisation problem(s) reported above.");
            return 1;
        }
        return status;
    }

    /**
     * process an archive
     *
     * @param context           DSpace Context
     * @param sourceDirPath     source path
     * @param itemField         item field
     * @param metadataIndexName index name
     * @param alterProvenance   whether to alter provenance
     * @param isTest            test flag
     * @throws Exception if error
     */
    protected void processArchive(Context context, String sourceDirPath, String itemField,
                                  String metadataIndexName, boolean alterProvenance, boolean isTest)
        throws Exception {
        processArchive(context, sourceDirPath, itemField, metadataIndexName, alterProvenance, isTest, false);
    }

    /**
     * process an archive
     *
     * @param context             DSpace Context
     * @param sourceDirPath       source path
     * @param itemField           item field
     * @param metadataIndexName   index name
     * @param alterProvenance     whether to alter provenance
     * @param isTest              test flag
     * @param syncEmbargoPolicies if true, synchronize bitstream embargo resource policies with the embargo metadata
     *                            (dc.rights.access, dc.date.embargoend) after the SAF update is applied
     * @throws Exception if error
     */
    protected void processArchive(Context context, String sourceDirPath, String itemField,
                                  String metadataIndexName, boolean alterProvenance, boolean isTest,
                                  boolean syncEmbargoPolicies)
        throws Exception {
        // open and process the source directory
        File sourceDir = new File(sourceDirPath);

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            pr("Error, cannot open archive source directory " + sourceDirPath);
            throw new Exception("error with archive source directory " + sourceDirPath);
        }

        String[] dircontents = sourceDir.list(directoryFilter);  //just the names, not the path
        Arrays.sort(dircontents);

        //Undo is suppressed to prevent undo of undo
        boolean suppressUndo = false;
        File fSuppressUndo = new File(sourceDir, SUPPRESS_UNDO_FILENAME);
        if (fSuppressUndo.exists()) {
            suppressUndo = true;
        }

        File undoDir = null;  //sibling directory of source archive

        if (!suppressUndo && !isTest) {
            undoDir = initUndoArchive(sourceDir);
        }

        int itemCount = 0;
        int successItemCount = 0;

        for (String dirname : dircontents) {
            itemCount++;
            pr("");
            pr("processing item " + dirname);

            try {
                ItemArchive itarch = ItemArchive.create(context, new File(sourceDir, dirname), itemField);

                for (UpdateAction action : actionMgr) {
                    pr("action: " + action.getClass().getName());
                    action.execute(context, itarch, isTest, suppressUndo);
                    if (!isTest && !suppressUndo) {
                        itarch.writeUndo(undoDir);
                    }
                }
                if (!isTest) {
                    Item item = itarch.getItem();
                    if (syncEmbargoPolicies) {
                        try {
                            this.syncEmbargoPolicies(context, item);
                        } catch (Exception embargoFailure) {
                            // The catch below only prints the exception, which would leave the run exiting 0.
                            // Synchronisation is idempotent, so re-running the SAF package repairs the item.
                            embargoSyncFailures++;
                            throw embargoFailure;
                        }
                    }
                    itemService.update(context, item);  //need to update before commit
                    context.uncacheEntity(item);
                }
                ItemUpdate.pr("Item " + dirname + " completed");
                successItemCount++;
            } catch (Exception e) {
                pr("Exception processing item " + dirname + ": " + e.toString());
                e.printStackTrace();
            }
        }

        if (!suppressUndo && !isTest) {
            StringBuilder sb = new StringBuilder("dsrun org.dspace.app.itemupdate.ItemUpdate ");
            sb.append(" -e ").append(this.eperson);
            sb.append(" -s ").append(undoDir);

            if (itemField != null) {
                sb.append(" -i ").append(itemField);
            }

            if (!alterProvenance) {
                sb.append(" -P ");
            }
            if (isTest) {
                sb.append(" -t ");
            }

            for (String actionOption : undoActionList) {
                sb.append(actionOption);
            }

            PrintWriter pw = null;
            try {
                File cmdFile = new File(undoDir.getParent(), undoDir.getName() + "_command.sh");
                pw = new PrintWriter(new BufferedWriter(new FileWriter(cmdFile)));
                pw.println(sb.toString());
            } finally {
                pw.close();
            }
        }

        pr("");
        pr("Done processing.  Successful items: " + successItemCount + " of " + itemCount + " items in source archive");
        pr("");
    }


    /**
     * to avoid overwriting the undo source tree on repeated processing
     * sequence numbers are added and checked
     *
     * @param sourceDir - the original source directory
     * @return the directory of the undo archive
     * @throws FileNotFoundException if file doesn't exist
     * @throws IOException           if IO error
     */
    protected File initUndoArchive(File sourceDir)
        throws FileNotFoundException, IOException {
        File parentDir = sourceDir.getCanonicalFile().getParentFile();
        if (parentDir == null) {
            throw new FileNotFoundException(
                "Parent directory of archive directory not found; unable to write UndoArchive; no processing " +
                    "performed");
        }

        String sourceDirName = sourceDir.getName();
        int seqNo = 1;

        File undoDir = new File(parentDir, "undo_" + sourceDirName + "_" + seqNo);
        while (undoDir.exists()) {
            undoDir = new File(parentDir, "undo_" + sourceDirName + "_" + ++seqNo); //increment
        }

        // create root directory
        if (!undoDir.mkdir()) {
            pr("ERROR creating  Undo Archive directory " + undoDir.getCanonicalPath());
            throw new IOException("ERROR creating  Undo Archive directory " + undoDir.getCanonicalPath());
        }

        //Undo is suppressed to prevent undo of undo
        File fSuppressUndo = new File(undoDir, ItemUpdate.SUPPRESS_UNDO_FILENAME);
        try {
            fSuppressUndo.createNewFile();
        } catch (IOException e) {
            pr("ERROR creating Suppress Undo File " + e.toString());
            throw e;
        }
        return undoDir;
    }

    //private void write

    /**
     * Set EPerson doing import
     *
     * @param context DSpace Context
     * @param eperson EPerson obj
     * @throws Exception if error
     */
    protected void setEPerson(Context context, String eperson)
        throws Exception {
        if (eperson == null) {
            pr("Error - an eperson to do the importing must be specified");
            pr(" (run with -h flag for details)");
            throw new Exception("EPerson not specified.");
        }

        EPerson myEPerson = null;

        if (eperson.indexOf('@') != -1) {
            // @ sign, must be an email
            myEPerson = epersonService.findByEmail(context, eperson);
        } else {
            myEPerson = epersonService.find(context, UUID.fromString(eperson));
        }

        if (myEPerson == null) {
            pr("Error, eperson cannot be found: " + eperson);
            throw new Exception("Invalid EPerson");
        }

        context.setCurrentUser(myEPerson);
    }

    /**
     * poor man's logging
     * As with ItemImport, API logging goes through log4j to the DSpace.log files
     * whereas the batch logging goes to the console to be captured there.
     *
     * @param s String
     */
    static void pr(String s) {
        log.info(s);
        System.out.println(s);
    }

    /**
     * report something the operator has to look at which does not stop the run
     *
     * @param s String
     */
    static void prWarn(String s) {
        log.warn(s);
        System.out.println("WARNING: " + s);
    }

    /**
     * report something that made this tool refuse to do what it was asked to do
     *
     * @param s String
     */
    static void prErr(String s) {
        log.error(s);
        System.out.println("ERROR: " + s);
    }

    /**
     * print if verbose flag is set
     *
     * @param s String
     */
    static void prv(String s) {
        if (verbose) {
            System.out.println(s);
        }
    }

    protected static boolean containsEmbargoField(String[] targetFields) {
        if (targetFields == null) {
            return false;
        }

        for (String field : targetFields) {
            if (field == null) {
                continue;
            }
            String normalized = field.trim();
            if (EMBARGO_FIELD_RIGHTS_ACCESS.equals(normalized)
                    || EMBARGO_FIELD_DATE_END.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bring the {@code Anonymous}/{@code READ} resource policies of the item bitstreams in line with the
     * embargo metadata of the item ({@code dc.rights.access}, {@code dc.date.embargoend}). The metadata is
     * validated before any policy is touched, and the surviving policy is re-dated instead of being replaced,
     * so that a failure cannot leave a bitstream without any policy.
     *
     * @param context DSpace context
     * @param item    item that has just been updated from the SAF archive
     * @throws SQLException       if a database error occurs
     * @throws AuthorizeException if the policy update is not permitted
     */
    protected void syncEmbargoPolicies(Context context, Item item) throws SQLException, AuthorizeException {
        if (item.isWithdrawn()) {
            prWarn("Item " + itemLabel(item) + " is withdrawn, its bitstream policies are left untouched."
                       + " A withdrawn item must never regain a READ policy, or the takedown would undo itself"
                       + " as soon as the embargo lapses.");
            return;
        }

        if (!item.isArchived()) {
            prWarn("Item " + itemLabel(item) + " is not archived (workspace or workflow submission),"
                       + " its bitstream policies are left untouched.");
            return;
        }

        List<MetadataValue> accessRights = itemService.getMetadata(item, "dc", "rights", "access", Item.ANY);
        boolean hasEmbargoedAccess = false;
        for (MetadataValue accessRight : accessRights) {
            String value = StringUtils.trimToEmpty(accessRight.getValue());
            if (EMBARGOED_ACCESS.equals(value)) {
                hasEmbargoedAccess = true;
            } else if (!OPEN_ACCESS.equals(value)) {
                // restrictedAccess, metadataOnlyAccess or an unknown value blocks the whole item, even next
                // to an openAccess one: contradictory metadata is not resolved towards disclosure.
                prWarn("Item " + itemLabel(item) + " carries " + EMBARGO_FIELD_RIGHTS_ACCESS + "='"
                           + accessRight.getValue() + "', which is not an embargo access right ("
                           + OPEN_ACCESS + ", " + EMBARGOED_ACCESS + "), its bitstream policies are left"
                           + " untouched.");
                return;
            }
        }

        List<MetadataValue> embargoEndDates = itemService.getMetadata(item, "dc", "date", "embargoend", Item.ANY);

        if (embargoEndDates.isEmpty()) {
            if (hasEmbargoedAccess) {
                prWarn("Item " + itemLabel(item) + " has " + EMBARGO_FIELD_RIGHTS_ACCESS + "=" + EMBARGOED_ACCESS
                           + " but no " + EMBARGO_FIELD_DATE_END + ". Cannot set an embargo without an end date,"
                           + " its bitstream policies are left untouched.");
                embargoSyncFailures++;
                return;
            }
            // A missing dc.date.embargoend is no instruction, not a request to lift the embargo: it may have
            // been set outside this tool, and this method runs for every item of the batch.
            pr("Item " + itemLabel(item) + " has no " + EMBARGO_FIELD_DATE_END + ", resource policies left"
                   + " untouched. To end an embargo, set " + EMBARGO_FIELD_DATE_END + " to a date in the past.");
            return;
        }

        if (embargoEndDates.size() > 1) {
            prWarn("Multiple " + EMBARGO_FIELD_DATE_END + " values found. Using first value only.");
        }

        String embargoEndDateStr = embargoEndDates.get(0).getValue();
        if (StringUtils.isBlank(embargoEndDateStr)) {
            prErr(EMBARGO_FIELD_DATE_END + " is empty on item " + itemLabel(item) + ", its bitstream policies"
                      + " are left untouched.");
            embargoSyncFailures++;
            return;
        }

        LocalDate embargoEndDay;
        try {
            // Strict parsing: DCDate rolls 2026-02-30 over into 2026-03-02 and would turn a typo into a real
            // embargo date. The import path uses the same parser, so a package means one day in both tools.
            embargoEndDay = SafEmbargoDateParser.parseEmbargoEndDay(embargoEndDateStr);
        } catch (DateTimeParseException e) {
            prErr("Invalid " + EMBARGO_FIELD_DATE_END + " '" + embargoEndDateStr + "' on item "
                      + itemLabel(item) + ", expected " + SafEmbargoDateParser.ACCEPTED_FORMATS + ". Its"
                      + " bitstream policies are left untouched.");
            embargoSyncFailures++;
            return;
        }

        // dc.date.embargoend is the last day of the embargo, so access starts the day after at midnight UTC:
        // startDate is mapped @Temporal(DATE) and the other DSpace embargo paths store that same day.
        LocalDate accessStartDay = embargoEndDay.plusDays(1);
        Date accessStartDate = Date.from(accessStartDay.atStartOfDay(ZoneOffset.UTC).toInstant());

        if (!accessStartDay.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            // An expired embargo is a publication, not a deletion: the start date that has already passed
            // makes the policy effective immediately.
            pr("Embargo of item " + itemLabel(item) + " already expired on " + embargoEndDay
                   + ", its bitstreams are public since " + accessStartDay + ".");
        }

        applyEmbargoToItemBitstreams(context, item, accessStartDate);
    }

    /**
     * Write the target embargo state onto the {@code Anonymous}/{@code READ} policy of every bitstream in
     * {@link SafEmbargoConstants#EMBARGOED_BUNDLE_NAMES}, leaving exactly one such policy per bitstream, as a
     * second undated one would defeat the embargo. Where there is no such policy none is created: the bitstream
     * was not public, and creating one would widen access instead of re-dating it. The embargo belongs to the
     * item, so the derived bundles get the same start date as the file they were derived from.
     *
     * @param context   DSpace context
     * @param item      item whose bitstreams are synchronised
     * @param startDate day the files become publicly readable; a day in the past takes effect immediately
     * @throws SQLException       if a database error occurs
     * @throws AuthorizeException if the policy update is not permitted
     */
    protected void applyEmbargoToItemBitstreams(Context context, Item item, Date startDate)
            throws SQLException, AuthorizeException {
        Group anonymousGroup = groupService.findByName(context, Group.ANONYMOUS);
        if (anonymousGroup == null) {
            prErr("Group '" + Group.ANONYMOUS + "' not found, no embargo policy was synchronised for item "
                      + itemLabel(item) + ".");
            embargoSyncFailures++;
            return;
        }

        for (String bundleName : SafEmbargoConstants.EMBARGOED_BUNDLE_NAMES) {
            for (Bundle bundle : item.getBundles(bundleName)) {
                for (Bitstream bitstream : bundle.getBitstreams()) {
                    applyEmbargoToBitstream(context, item, bundleName, bitstream, anonymousGroup, startDate);
                }
            }
        }
    }

    /**
     * Synchronise the {@code Anonymous}/{@code READ} policy of a single bitstream, reporting the bundle it
     * belongs to so that the operator sees which file of the item a problem is about.
     *
     * @param context        DSpace context
     * @param item           item the bitstream belongs to
     * @param bundleName     bundle the bitstream lives in
     * @param bitstream      bitstream to synchronise
     * @param anonymousGroup the {@code Anonymous} group
     * @param startDate      day the file becomes publicly readable
     * @throws SQLException       if a database error occurs
     * @throws AuthorizeException if the policy update is not permitted
     */
    protected void applyEmbargoToBitstream(Context context, Item item, String bundleName, Bitstream bitstream,
            Group anonymousGroup, Date startDate) throws SQLException, AuthorizeException {
        // Located by (group, action) rather than by rpName: policies written under earlier names have to be
        // adopted and normalised instead of being left behind next to a new one.
        List<ResourcePolicy> anonymousReadPolicies = new ArrayList<>();
        for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream, Constants.READ)) {
            if (anonymousGroup.equals(policy.getGroup())) {
                anonymousReadPolicies.add(policy);
            }
        }

        if (anonymousReadPolicies.isEmpty()) {
            prErr("Bitstream " + bitstreamLabel(bundleName, bitstream) + " of item " + itemLabel(item)
                      + " has no " + Group.ANONYMOUS + " READ policy, so there is nothing to re-date and its"
                      + " embargo could not be synchronised. No policy is created: that would grant access"
                      + " nobody ever granted. " + accessChangeHint(bundleName));
            embargoSyncFailures++;
            return;
        }

        // A policy with an end date is a lease, not an embargo: re-dating it would either close the file for
        // good or drop the end date, and either way decide access for the operator.
        ResourcePolicy leasePolicy = firstPolicyWithEndDate(anonymousReadPolicies);
        if (leasePolicy != null) {
            prErr("Bitstream " + bitstreamLabel(bundleName, bitstream) + " of item " + itemLabel(item)
                      + " has an " + Group.ANONYMOUS + " READ policy with an end date (policy #"
                      + leasePolicy.getID() + ", rpName='" + leasePolicy.getRpName() + "'), which this tool"
                      + " does not manage, so its embargo could not be synchronised and the bitstream is left"
                      + " untouched. " + accessChangeHint(bundleName));
            embargoSyncFailures++;
            return;
        }

        ResourcePolicy survivor = selectSurvivorPolicy(anonymousReadPolicies);
        survivor.setStartDate(startDate);
        survivor.setRpType(ResourcePolicy.TYPE_CUSTOM);
        survivor.setRpName(SafEmbargoConstants.EMBARGO_POLICY_NAME);
        resourcePolicyService.update(context, survivor);

        // The duplicates go only once the survivor is stored. Reference identity rather than equals():
        // ResourcePolicy.equals compares values, which the lines above just changed.
        for (ResourcePolicy policy : anonymousReadPolicies) {
            if (policy != survivor) {
                resourcePolicyService.delete(context, policy);
            }
        }
    }

    /**
     * Bundle, name and id of a bitstream, so that a report identifies exactly one file of the item.
     *
     * @param bundleName bundle the bitstream lives in
     * @param bitstream  bitstream to describe
     * @return label of the form {@code BUNDLE/name (uuid)}
     */
    protected String bitstreamLabel(String bundleName, Bitstream bitstream) {
        return bundleName + "/'" + bitstream.getName() + "' (" + bitstream.getID() + ")";
    }

    /**
     * How to change access to a bitstream this tool refuses to touch. {@code bulk-access-control} walks the
     * ORIGINAL bundle only, so it cannot be recommended for a derived one.
     *
     * @param bundleName bundle the bitstream lives in
     * @return the hint matching the bundle
     */
    protected String accessChangeHint(String bundleName) {
        return Constants.CONTENT_BUNDLE_NAME.equals(bundleName) ? BULK_ACCESS_CONTROL_HINT
            : DERIVATIVE_ACCESS_HINT;
    }

    /**
     * First policy of the list that expires by itself.
     *
     * @param anonymousReadPolicies the Anonymous READ policies of a single bitstream
     * @return a policy with a non-null end date, or {@code null} when none of them has one
     */
    protected ResourcePolicy firstPolicyWithEndDate(List<ResourcePolicy> anonymousReadPolicies) {
        for (ResourcePolicy policy : anonymousReadPolicies) {
            if (policy.getEndDate() != null) {
                return policy;
            }
        }
        return null;
    }

    /**
     * Pick the policy that has been in force the longest: the dated one with the oldest start date or, when none
     * of them is dated, the first one. Adopting the newest would resurrect an obsolete embargo date.
     *
     * @param anonymousReadPolicies the Anonymous READ policies of a single bitstream, never empty
     * @return the policy to be mutated into the embargo policy
     */
    protected ResourcePolicy selectSurvivorPolicy(List<ResourcePolicy> anonymousReadPolicies) {
        ResourcePolicy oldestDated = null;
        for (ResourcePolicy policy : anonymousReadPolicies) {
            if (policy.getStartDate() == null) {
                continue;
            }
            if (oldestDated == null || policy.getStartDate().before(oldestDated.getStartDate())) {
                oldestDated = policy;
            }
        }
        return oldestDated == null ? anonymousReadPolicies.get(0) : oldestDated;
    }

    /**
     * Handle of the item, or its UUID while it has none. Only used in operator messages.
     *
     * @param item item to describe
     * @return handle or UUID
     */
    protected static String itemLabel(Item item) {
        return item.getHandle() == null ? String.valueOf(item.getID()) : item.getHandle();
    }

} //end of class

