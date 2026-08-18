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

    /** The only supported way of changing access to a bitstream this tool refuses to touch. */
    private static final String BULK_ACCESS_CONTROL_HINT =
        "Use the 'dspace bulk-access-control' script to grant or restore access to it.";

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
     * Exit code of a run. Embargo problems are reported per item and never abort the run, but they must not be
     * reported as success either: an operator scripting {@code itemupdate} only ever sees the exit code.
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
                            // The catch below only prints the exception, and a printed exception is an exit
                            // code of 0. An embargo synchronisation that died half way has already re-dated
                            // the surviving Anonymous READ policy of a bitstream - the duplicate deletion
                            // that follows it is the last thing to run - and context.complete() commits that
                            // state. Counting the failure is what stops a published file from being reported
                            // as a successful run.
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
     * Bring the {@code Anonymous}/{@code READ} resource policies of the ORIGINAL bitstreams in line with the
     * embargo metadata of the item ({@code dc.rights.access}, {@code dc.date.embargoend}).
     *
     * <p>The order of the three phases below is the whole point of this method. A policy whose start date lies
     * in the future only postpones access and is therefore harmless, but deleting a policy - or writing a start
     * date that has already passed - is a publishing operation. So every objection is raised first, the target
     * state is computed second, and only a run that got that far may touch a single policy. Nothing is deleted
     * before its replacement has been stored, which is why the existing policy is mutated rather than replaced:
     * a failure between a delete and a create would leave the file with no policy at all, i.e. HTTP 401.</p>
     *
     * <p>Only a {@code dc.date.embargoend} that is actually present is an instruction. Its absence means the
     * SAF package says nothing about the embargo of this item, and the policies are left exactly as they are -
     * an embargo this tool never set is never lifted by it. A file is opened by writing a
     * {@code dc.date.embargoend} that lies in the past.</p>
     *
     * @param context DSpace context
     * @param item    item that has just been updated from the SAF archive
     * @throws SQLException       if a database error occurs
     * @throws AuthorizeException if the policy update is not permitted
     */
    protected void syncEmbargoPolicies(Context context, Item item) throws SQLException, AuthorizeException {
        // --- phase 1: objections -------------------------------------------------------------------------
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
                // restrictedAccess, metadataOnlyAccess or a value we do not know. A single such value blocks the
                // whole item even next to an openAccess one: contradictory metadata is never resolved towards
                // disclosure, and an unknown access right is not an invitation to guess.
                prWarn("Item " + itemLabel(item) + " carries " + EMBARGO_FIELD_RIGHTS_ACCESS + "='"
                           + accessRight.getValue() + "', which is not an embargo access right ("
                           + OPEN_ACCESS + ", " + EMBARGOED_ACCESS + "), its bitstream policies are left"
                           + " untouched.");
                return;
            }
        }

        // --- phase 2: validate and compute the target state ----------------------------------------------
        List<MetadataValue> embargoEndDates = itemService.getMetadata(item, "dc", "date", "embargoend", Item.ANY);

        if (embargoEndDates.isEmpty()) {
            if (hasEmbargoedAccess) {
                prWarn("Item " + itemLabel(item) + " has " + EMBARGO_FIELD_RIGHTS_ACCESS + "=" + EMBARGOED_ACCESS
                           + " but no " + EMBARGO_FIELD_DATE_END + ". Cannot set an embargo without an end date,"
                           + " its bitstream policies are left untouched.");
                embargoSyncFailures++;
                return;
            }
            // A missing dc.date.embargoend is no instruction at all, and it is never read as "lift the
            // embargo". The embargo of an item may well have been set outside this tool - the submission
            // access condition and dspace bulk-access-control both write exactly the policy that would be
            // reopened here (Anonymous/READ, TYPE_CUSTOM, rpName "embargo") - while syncEmbargoPolicies runs
            // for every item of a batch whose -a/-d fields mention an embargo field. A single SAF package
            // without the field would therefore publish every embargoed file of that batch.
            // The supported way to open a file is a dc.date.embargoend that lies in the past.
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
            // Strict parsing on purpose: DCDate rolls 2026-02-30 over into 2026-03-02 and would turn a typo
            // into a real embargo date. The shapes DCDate accepted are still read, and read as the same day,
            // so the SAF packages of this repository keep working - see SafEmbargoDateParser. The import side
            // uses the same parser, or the same package would mean two different days in the two tools.
            embargoEndDay = SafEmbargoDateParser.parseEmbargoEndDay(embargoEndDateStr);
        } catch (DateTimeParseException e) {
            prErr("Invalid " + EMBARGO_FIELD_DATE_END + " '" + embargoEndDateStr + "' on item "
                      + itemLabel(item) + ", expected " + SafEmbargoDateParser.ACCEPTED_FORMATS + ". Its"
                      + " bitstream policies are left untouched.");
            embargoSyncFailures++;
            return;
        }

        // dc.date.embargoend is the inclusive last day of the embargo, so access starts the day after, at
        // midnight UTC. Calendar.getInstance() would use the server time zone and shift that boundary.
        LocalDate accessStartDay = embargoEndDay.plusDays(1);
        Date accessStartDate = Date.from(accessStartDay.atStartOfDay(ZoneOffset.UTC).toInstant());

        if (!accessStartDay.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            // An expired embargo is a publication, not a deletion. The real - already passed - start date is
            // written, which makes the policy effective immediately.
            pr("Embargo of item " + itemLabel(item) + " already expired on " + embargoEndDay
                   + ", its ORIGINAL bitstreams are public since " + accessStartDay + ".");
        }

        // --- phase 3: mutate -----------------------------------------------------------------------------
        applyEmbargoToItemBitstreams(context, item, accessStartDate);
    }

    /**
     * Write the target embargo state onto the {@code Anonymous}/{@code READ} policy of every ORIGINAL bitstream
     * of the item.
     *
     * <p>Exactly one such policy is left behind per bitstream: a second, undated one would silently defeat the
     * embargo. A policy is never created - a bitstream without an {@code Anonymous}/{@code READ} policy was not
     * public, and inventing one would widen access instead of re-dating it.</p>
     *
     * @param context   DSpace context
     * @param item      item whose ORIGINAL bitstreams are synchronised
     * @param startDate day the files become publicly readable, never {@code null}; a day in the past makes the
     *                  policy effective immediately
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

        for (Bundle bundle : item.getBundles(Constants.CONTENT_BUNDLE_NAME)) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                // Deliberately located by (group, action) and not by rpName: policies written by earlier
                // versions of this tool carry "Standard Embargo" or "Special Case Embargo" and have to be
                // adopted and normalised instead of being left behind next to a new one.
                List<ResourcePolicy> anonymousReadPolicies = new ArrayList<>();
                for (ResourcePolicy policy : resourcePolicyService.find(context, bitstream, Constants.READ)) {
                    if (anonymousGroup.equals(policy.getGroup())) {
                        anonymousReadPolicies.add(policy);
                    }
                }

                if (anonymousReadPolicies.isEmpty()) {
                    prErr("Bitstream '" + bitstream.getName() + "' (" + bitstream.getID() + ") of item "
                              + itemLabel(item) + " has no " + Group.ANONYMOUS + " READ policy, so there is"
                              + " nothing to re-date and its embargo could not be synchronised. No policy is"
                              + " created: that would grant access nobody ever granted. "
                              + BULK_ACCESS_CONTROL_HINT);
                    embargoSyncFailures++;
                    continue;
                }

                ResourcePolicy survivor = selectSurvivorPolicy(anonymousReadPolicies);
                survivor.setStartDate(startDate);
                survivor.setRpType(ResourcePolicy.TYPE_CUSTOM);
                survivor.setRpName(SafEmbargoConstants.EMBARGO_POLICY_NAME);
                resourcePolicyService.update(context, survivor);

                // Only now, with the replacement safely stored, may the duplicates go. Reference identity is
                // used on purpose: ResourcePolicy.equals compares values, which the lines above just changed.
                for (ResourcePolicy policy : anonymousReadPolicies) {
                    if (policy != survivor) {
                        resourcePolicyService.delete(context, policy);
                    }
                }
            }
        }
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

