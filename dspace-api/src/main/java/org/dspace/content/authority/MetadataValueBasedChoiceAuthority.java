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

    @Override
    public String getLabel(String key, String locale) {
        if (StringUtils.isBlank(key)) {
            return "Unknown";
        }

        Context context = ContextUtil.obtainCurrentRequestContext();
        boolean isCliContext = (context == null);
        
        if (isCliContext) {
            try {
                context = new Context(Context.Mode.READ_ONLY);
                log.debug("Created new READ_ONLY context for CLI operations");
            } catch (Exception e) {
                log.error("Failed to create context for database operations", e);
                return key;
            }
        }

        try {
            String normalizedLocale = StringUtils.isBlank(locale) ? null : locale;
            List<MetadataValue> results = metadataValueService.findByAuthorityAndLanguage(context, key, normalizedLocale);

            if (!results.isEmpty()) {
                return results.get(0).getValue();
            }

            if (StringUtils.isNotBlank(normalizedLocale)) {
                List<MetadataValue> fallbackResults =
                        metadataValueService.findByAuthorityAndLanguage(context, key, null);
                if (!fallbackResults.isEmpty()) {
                    return fallbackResults.get(0).getValue();
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving label for authority key '{}'", key, e);
        } finally {
            if (isCliContext && context != null) {
                try {
                    context.abort();
                } catch (Exception e) {
                    log.warn("Error closing CLI context", e);
                }
            }
        }

        return key;
    }

    @Override
    public Choices getMatches(String query, int start, int limit, String locale) {
        if (StringUtils.isBlank(query)) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        Context context = ContextUtil.obtainCurrentRequestContext();
        boolean isCliContext = (context == null);
        
        if (isCliContext) {
            try {
                context = new Context(Context.Mode.READ_ONLY);
                log.debug("Created new READ_ONLY context for CLI operations");
            } catch (Exception e) {
                log.error("Failed to create context for database operations", e);
                return new Choices(Choices.CF_NOTFOUND);
            }
        }

        try {
            String normalizedLocale = StringUtils.isBlank(locale) ? null : locale;
            List<MetadataValue> allResults = new ArrayList<>();
            List<MetadataValue> authorityResults =
                    metadataValueService.findByAuthorityAndLanguage(context, query, normalizedLocale);
            allResults.addAll(authorityResults);

            Iterator<MetadataValue> valueResults = metadataValueService.findByValueLike(context, query);
            while (valueResults.hasNext()) {
                MetadataValue mv = valueResults.next();
                if (StringUtils.isNotBlank(mv.getAuthority()) &&
                        (StringUtils.isBlank(normalizedLocale) || normalizedLocale.equals(mv.getLanguage()))) {
                    allResults.add(mv);
                }
            }

            List<MetadataValue> uniqueResults = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();
            for (MetadataValue mv : allResults) {
                String compositeKey = mv.getAuthority() + "\u0000" + mv.getValue();
                if (seenKeys.add(compositeKey)) {
                    uniqueResults.add(mv);
                }
            }

            int fromIndex = Math.max(0, start);
            int toIndex = limit > 0 ? Math.min(uniqueResults.size(), fromIndex + limit) : uniqueResults.size();

            if (fromIndex > uniqueResults.size()) {
                return new Choices(Choices.CF_NOTFOUND);
            }

            List<MetadataValue> paginated = uniqueResults.subList(fromIndex, toIndex);
            List<Choice> choices = new ArrayList<>();
            int defaultSelected = -1;

            for (int i = 0; i < paginated.size(); i++) {
                MetadataValue mv = paginated.get(i);
                choices.add(new Choice(mv.getAuthority(), mv.getValue(), mv.getValue()));
                if (query.equalsIgnoreCase(mv.getValue()) && defaultSelected == -1) {
                    defaultSelected = i;
                }
            }

            return new Choices(choices.toArray(new Choice[0]), start, uniqueResults.size(),
                    choices.isEmpty() ? Choices.CF_NOTFOUND : Choices.CF_AMBIGUOUS,
                    toIndex < uniqueResults.size(), defaultSelected);

        } catch (Exception e) {
            log.error("Error getting matches for query '{}'", query, e);
            return new Choices(Choices.CF_NOTFOUND);
        } finally {
            if (isCliContext && context != null) {
                try {
                    context.abort();
                } catch (Exception e) {
                    log.warn("Error closing CLI context", e);
                }
            }
        }
    }

    @Override
    public Choices getBestMatch(String text, String locale) {
        if (StringUtils.isBlank(text)) {
            return new Choices(Choices.CF_NOTFOUND);
        }

        Context context = ContextUtil.obtainCurrentRequestContext();
        boolean isCliContext = (context == null);
        
        if (isCliContext) {
            try {
                context = new Context(Context.Mode.READ_ONLY);
                log.debug("Created new READ_ONLY context for CLI operations");
            } catch (Exception e) {
                log.error("Failed to create context for database operations", e);
                return new Choices(Choices.CF_NOTFOUND);
            }
        }

        try {
            Iterator<MetadataValue> valueResults = metadataValueService.findByValueLike(context, text);
            while (valueResults.hasNext()) {
                MetadataValue mv = valueResults.next();
                if (text.equalsIgnoreCase(mv.getValue()) && StringUtils.isNotBlank(mv.getAuthority())) {
                    return new Choices(new Choice[] { new Choice(mv.getAuthority(), mv.getValue(), mv.getValue()) },
                            0, 1, Choices.CF_ACCEPTED, false, 0);
                }
            }
        } catch (Exception e) {
            log.error("Error getting best match for text '{}'", text, e);
        } finally {
            if (isCliContext && context != null) {
                try {
                    context.abort();
                } catch (Exception e) {
                    log.warn("Error closing CLI context", e);
                }
            }
        }

        return new Choices(Choices.CF_NOTFOUND);
    }
}