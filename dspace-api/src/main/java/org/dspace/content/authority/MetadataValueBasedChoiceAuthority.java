/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataValueService;
import org.dspace.core.Context;
import org.dspace.web.ContextUtil;

/**
 * ChoiceAuthority implementation that retrieves labels from metadata values table.
 * Fixed LazyInitializationException by managing context persistence for CLI operations.
 * @author Michaela Stefancova at (dspace at dataquest.sk)
 */
public class MetadataValueBasedChoiceAuthority implements ChoiceAuthority {

    private static final Logger log = LogManager.getLogger(MetadataValueBasedChoiceAuthority.class);

    private MetadataValueService metadataValueService = ContentServiceFactory.getInstance().getMetadataValueService();

    private String pluginInstanceName;

    @Override
    public String getPluginInstanceName() {
        return pluginInstanceName;
    }

    @Override
    public void setPluginInstanceName(String name) {
        this.pluginInstanceName = name;
    }

    /**
     * Simple wrapper for context and cleanup flag
     */
    private static class ContextWrapper {
        final Context context;
        final boolean needsCleanup;

        ContextWrapper(Context context, boolean needsCleanup) {
            this.context = context;
            this.needsCleanup = needsCleanup;
        }
    }

    /**
     * Gets or creates a context for database operations
     */
    private ContextWrapper getOrCreateContext() {
        Context context = ContextUtil.obtainCurrentRequestContext();
        if (context != null) {
            return new ContextWrapper(context, false);
        }

        try {
            context = new Context(Context.Mode.READ_ONLY);
            log.debug("Created new READ_ONLY context for CLI operations");
            return new ContextWrapper(context, true);
        } catch (Exception e) {
            log.error("Failed to create context for database operations", e);
            return new ContextWrapper(null, false);
        }
    }

    @Override
    public String getLabel(String key, String locale) {
        if (StringUtils.isBlank(key)) {
            return "Unknown";
        }

        ContextWrapper contextWrapper = getOrCreateContext();
        if (contextWrapper.context == null) {
            return key;
        }

        try {
            String normalizedLocale = StringUtils.isBlank(locale) ? null : locale;
            List<MetadataValue> results = metadataValueService.findByAuthorityAndLanguage(
                    contextWrapper.context, key, normalizedLocale);

            if (!results.isEmpty()) {
                // Extract value immediately to avoid lazy loading later
                return results.get(0).getValue();
            }

            if (StringUtils.isNotBlank(normalizedLocale)) {
                List<MetadataValue> fallbackResults =
                        metadataValueService.findByAuthorityAndLanguage(contextWrapper.context, key, null);
                if (!fallbackResults.isEmpty()) {
                    // Extract value immediately to avoid lazy loading later
                    return fallbackResults.get(0).getValue();
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving label for authority key '{}'", key, e);
        }

        return key;
    }

    @Override
    public Choices getMatches(String query, int start, int limit, String locale) {
        if (StringUtils.isBlank(query)) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        ContextWrapper contextWrapper = getOrCreateContext();
        if (contextWrapper.context == null) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        try {
            String normalizedLocale = StringUtils.isBlank(locale) ? null : locale;
            int fromIndex = Math.max(0, start);
            int maxNeeded = limit > 0 ? fromIndex + limit + 1 : Integer.MAX_VALUE;

            // Use simple data structure to avoid lazy loading issues
            List<Choice> uniqueResults = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();

            // Process authority results first
            List<MetadataValue> authorityResults = metadataValueService
                    .findByAuthorityAndLanguage(contextWrapper.context, query, normalizedLocale);
            for (MetadataValue mv : authorityResults) {
                if (uniqueResults.size() >= maxNeeded) {
                    break;
                }
                // Extract values immediately to avoid lazy loading later
                String authority = mv.getAuthority();
                String value = mv.getValue();
                // "\u0000" is the null character used as a safe separator between two values.
                String compositeKey = authority + "\u0000" + value;
                if (seenKeys.add(compositeKey)) {
                    uniqueResults.add(new Choice(authority, value, value));
                }
            }

            // Process value-like results only if we need more
            if (uniqueResults.size() < maxNeeded) {
                Iterator<MetadataValue> valueResults =
                        metadataValueService.findByValueLike(contextWrapper.context, query);
                while (valueResults.hasNext() && uniqueResults.size() < maxNeeded) {
                    MetadataValue mv = valueResults.next();
                    String authority = mv.getAuthority();
                    String value = mv.getValue();
                    String language = mv.getLanguage();
                    if (StringUtils.isNotBlank(authority) &&
                            (StringUtils.isBlank(normalizedLocale) ||
                                    normalizedLocale.equals(language))) {
                        String compositeKey = authority + "\u0000" + value;
                        if (seenKeys.add(compositeKey)) {
                            uniqueResults.add(new Choice(authority, value, value));
                        }
                    }
                }
            }

            if (fromIndex > uniqueResults.size()) {
                return new Choices(Choices.CF_NOTFOUND);
            }

            int toIndex = limit > 0 ? Math.min(uniqueResults.size(), fromIndex + limit) : uniqueResults.size();

            List<Choice> paginated = uniqueResults.subList(fromIndex, toIndex);
            int defaultSelected = -1;

            for (int i = 0; i < paginated.size(); i++) {
                Choice choice = paginated.get(i);
                if (query.equalsIgnoreCase(choice.value) && defaultSelected == -1) {
                    defaultSelected = i;
                }
            }

            return new Choices(paginated.toArray(new Choice[0]), fromIndex, uniqueResults.size(),
                    paginated.isEmpty() ? Choices.CF_NOTFOUND : Choices.CF_AMBIGUOUS,
                    toIndex < uniqueResults.size(), defaultSelected);

        } catch (Exception e) {
            log.error("Error getting matches for query '{}'", query, e);
            return new Choices(Choices.CF_NOTFOUND);
        }
    }

    @Override
    public Choices getBestMatch(String text, String locale) {
        if (StringUtils.isBlank(text)) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        ContextWrapper contextWrapper = getOrCreateContext();
        if (contextWrapper.context == null) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        try {
            Iterator<MetadataValue> valueResults = metadataValueService
                    .findByValueLike(contextWrapper.context, text);
            while (valueResults.hasNext()) {
                MetadataValue mv = valueResults.next();
                // Extract values immediately to avoid lazy loading later
                String authority = mv.getAuthority();
                String value = mv.getValue();
                if (text.equalsIgnoreCase(value) && StringUtils.isNotBlank(authority)) {
                    return new Choices(new Choice[] { new Choice(authority, value, value) },
                            0, 1, Choices.CF_ACCEPTED, false, 0);
                }
            }
        } catch (Exception e) {
            log.error("Error getting best match for text '{}'", text, e);
        }

        return new Choices(Choices.CF_NOTFOUND);
    }
}