/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.rdbms;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

/**
 * Flyway callback which, before validation, recognizes migrations that were applied under a
 * different version than the one they carry in this codebase.
 * <P>
 * Migrations are sometimes renumbered after they were released, e.g. when they are backported
 * to an older branch. A database migrated with the other numbering then has the migration
 * recorded under a version this codebase does not know ("not resolved locally"), while the same
 * file appears here as never applied, and "database migrate ignored" would run it a second time.
 * <P>
 * An applied migration that is missing locally is treated as renumbered when exactly one
 * not-yet-applied local migration has the same checksum, description and type, and a lower
 * version than the highest version recorded in the database. Its history row is then moved to
 * the local version and script. A lower version only, so that the highest version in the
 * database never grows and plain "migrate" keeps running every newer migration.
 */
public class RenumberedMigrationUpdater implements Callback {

    private static final Logger log = LogManager.getLogger(RenumberedMigrationUpdater.class);

    @Override
    public String getCallbackName() {
        // Return class name only (not prepended by package)
        return RenumberedMigrationUpdater.class.getSimpleName();
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event.equals(Event.BEFORE_VALIDATE);
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        MigrationInfo[] migrations = Flyway.configure(context.getConfiguration().getClassLoader())
                                           .configuration(context.getConfiguration()).load().info().all();

        MigrationVersion highestApplied = MigrationVersion.EMPTY;
        List<MigrationInfo> missing = new ArrayList<>();
        List<MigrationInfo> notApplied = new ArrayList<>();
        for (MigrationInfo migration : migrations) {
            if (migration.getVersion() == null) {
                continue;
            }
            if (migration.getState().isApplied() && migration.getVersion().isNewerThan(highestApplied)) {
                highestApplied = migration.getVersion();
            }
            // FUTURE_SUCCESS: missing locally and newer than every local migration.
            if (migration.getState() == MigrationState.MISSING_SUCCESS
                || migration.getState() == MigrationState.FUTURE_SUCCESS) {
                missing.add(migration);
            } else if (migration.getState().isResolved() && !migration.getState().isApplied()) {
                notApplied.add(migration);
            }
        }

        String sql = "UPDATE " + context.getConfiguration().getTable()
            + " SET version = ?, script = ? WHERE installed_rank = ?";
        for (MigrationInfo applied : missing) {
            List<MigrationInfo> matches = new ArrayList<>();
            for (MigrationInfo local : notApplied) {
                if (Objects.equals(local.getChecksum(), applied.getChecksum())
                    && local.getDescription().equals(applied.getDescription())
                    && local.getType() == applied.getType()
                    && highestApplied.isNewerThan(local.getVersion())) {
                    matches.add(local);
                }
            }
            if (matches.size() != 1) {
                continue;
            }
            MigrationInfo local = matches.get(0);
            try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
                statement.setString(1, local.getVersion().getVersion());
                statement.setString(2, local.getScript());
                statement.setInt(3, applied.getInstalledRank());
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Could not record migration " + applied.getVersion()
                                               + " under version " + local.getVersion(), e);
            }
            notApplied.remove(local);
            log.info("Migration {} ({}) was applied as version {}; recorded it under version {}.",
                     local.getScript(), local.getDescription(), applied.getVersion(), local.getVersion());
        }
    }
}
