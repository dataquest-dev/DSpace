/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.orcid.script;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataValue;
import org.dspace.content.authority.Choices;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

/**
 * Script that assigns ORCID-based authority values to dc.contributor.author metadata
 * by matching author names found in dc.identifier.orcid metadata entries.
 * The script always overwrites existing authority values to keep data up-to-date.
 * @author Matus Kasak (dspace at dataquest.sk)
 */
public class OrcidAuthorityAssign
        extends DSpaceRunnable<OrcidAuthorityAssignScriptConfiguration<OrcidAuthorityAssign>> {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Pattern ORCID_PATTERN =
            Pattern.compile("(\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX])");

    private ConfigurationService configurationService;
    private MetadataFieldService metadataFieldService;
    private MetadataValueService metadataValueService;

    private String orcidUrlPrefix;
    private Context context;

    @Override
    public void setup() throws ParseException {
        this.metadataFieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        this.metadataValueService = ContentServiceFactory.getInstance().getMetadataValueService();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        String domainUrl = configurationService.getProperty("orcid.domain-url");
        if (StringUtils.isBlank(domainUrl)) {
            throw new IllegalStateException("Configuration property 'orcid.domain-url' is not set.");
        }
        this.orcidUrlPrefix = domainUrl.trim().endsWith("/") ? domainUrl.trim() : domainUrl.trim() + "/";
    }

    @Override
    public void internalRun() throws Exception {
        context = new Context();
        assignCurrentUserInContext();

        try {
            context.turnOffAuthorisationSystem();
            performAuthorityAssignment();
            context.complete();
        } catch (Exception e) {
            handler.handleException(e);
            context.abort();
        } finally {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Set the authority of dc.contributor.author metadata
     * based on matching author names in dc.identifier.orcid.
     */
    private void performAuthorityAssignment() throws SQLException, IOException, AuthorizeException {
        // Build the author-name-to-ORCID map from dc.identifier.orcid
        MetadataField orcidField = metadataFieldService.findByElement(context, "dc", "identifier", "orcid");
        if (orcidField == null) {
            handler.logError("Metadata field dc.identifier.orcid not found in the registry. Aborting.");
            return;
        }

        List<MetadataValue> orcidValues = metadataValueService.findByField(context, orcidField);
        handler.logInfo("Found " + orcidValues.size() + " dc.identifier.orcid metadata entries.");

        // Map: normalized author name -> ORCID ID
        Map<String, String> authorNameToOrcid = new HashMap<>();

        for (MetadataValue orcidMv : orcidValues) {
            String rawValue = orcidMv.getValue();
            if (StringUtils.isBlank(rawValue)) {
                continue;
            }

            // Extract the ORCID ID from the value
            Matcher matcher = ORCID_PATTERN.matcher(rawValue);
            if (!matcher.find()) {
                handler.logWarning("Could not extract ORCID ID from value: " + rawValue);
                continue;
            }
            String orcidId = matcher.group(1);

            // The author name is everything before the ORCID ID, trimmed
            String authorName = rawValue.substring(0, matcher.start()).trim();
            if (StringUtils.isBlank(authorName)) {
                handler.logWarning("Could not extract author name from value: " + rawValue);
                continue;
            }

            String normalizedName = normalizeAuthorName(authorName);
            // If there's a duplicate author name with different ORCID
            if (authorNameToOrcid.containsKey(normalizedName)
                    && !authorNameToOrcid.get(normalizedName).equals(orcidId)) {
                handler.logWarning("Duplicate author name '" + authorName
                        + "' with different ORCIDs: " + authorNameToOrcid.get(normalizedName)
                        + " vs " + orcidId + ". Using the latest.");
            }
            authorNameToOrcid.put(normalizedName, orcidId);
        }

        handler.logInfo("Built lookup map with " + authorNameToOrcid.size() + " unique author-ORCID mappings.");

        if (authorNameToOrcid.isEmpty()) {
            handler.logInfo("No author-ORCID mappings found. Nothing to do.");
            return;
        }

        // Load all dc.contributor.author values
        MetadataField authorField = metadataFieldService.findByElement(context, "dc", "contributor", "author");
        if (authorField == null) {
            handler.logError("Metadata field dc.contributor.author not found in the registry. Aborting.");
            return;
        }

        List<MetadataValue> authorValues = metadataValueService.findByField(context, authorField);
        handler.logInfo("Found " + authorValues.size() + " dc.contributor.author metadata entries to check.");

        // Match and update
        int updated = 0;
        int batchSize = 50;

        for (MetadataValue authorMv : authorValues) {
            String authorValue = authorMv.getValue();
            if (StringUtils.isBlank(authorValue)) {
                continue;
            }

            String normalizedAuthor = normalizeAuthorName(authorValue);
            String orcidId = authorNameToOrcid.get(normalizedAuthor);

            if (orcidId != null) {
                String authorityValue = orcidUrlPrefix + orcidId;
                authorMv.setAuthority(authorityValue);
                authorMv.setConfidence(Choices.CF_ACCEPTED);
                metadataValueService.update(context, authorMv, true);
                updated++;

                // Evict processed entities from the Hibernate session in batches
                // to keep memory bounded.
                if (updated % batchSize == 0) {
                    context.uncacheEntity(authorMv);
                    handler.logInfo("Progress: " + updated + " authors updated so far...");
                }
            }
        }

        context.commit();

        handler.logInfo("Authority assignment complete. Updated: " + updated
                + ", Total author entries checked: " + authorValues.size());
    }

    /**
     * Normalize an author name for matching purposes.
     */
    private String normalizeAuthorName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT).replace(",", "").replaceAll("\\s+", " ");
    }

    /**
     * Assigns the current user to the context.
     */
    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OrcidAuthorityAssignScriptConfiguration<OrcidAuthorityAssign> getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("orcid-authority-assign",
                OrcidAuthorityAssignScriptConfiguration.class);
    }
}
