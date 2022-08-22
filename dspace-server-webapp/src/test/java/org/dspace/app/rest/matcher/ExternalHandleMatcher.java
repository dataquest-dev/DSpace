package org.dspace.app.rest.matcher;

import org.dspace.core.Constants;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.UUID;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

public class ExternalHandleMatcher {

    private ExternalHandleMatcher() {
    }

    public static Matcher<? super Object> matchProperties(String name, UUID uuid, String handle, int type) {
        return allOf(
                hasJsonPath("$.uuid", is(uuid.toString())),
                hasJsonPath("$.name", is(name)),
                hasJsonPath("$.handle", is(handle)),
                hasJsonPath("$.type", is(Constants.typeText[type].toLowerCase())),
                hasJsonPath("$.metadata", Matchers.allOf(
                        MetadataMatcher.matchMetadata("dc.title", name)
                ))
        );
    }
}
