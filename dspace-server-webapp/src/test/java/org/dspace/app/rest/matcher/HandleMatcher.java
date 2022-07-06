package org.dspace.app.rest.matcher;

import org.dspace.content.DSpaceObject;
import org.dspace.handle.Handle;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

import org.dspace.content.MetadataValue;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class HandleMatcher {

    private HandleMatcher() { }

    public static Matcher<? super Object> matchHandle(Handle handle) {
        return allOf(
                hasJsonPath("$.handle", is(handle.getHandle())),
                hasJsonPath("$.dso", is(handle.getDSpaceObject())),
                hasJsonPath("$.resourceTypeID", is(handle.getResourceTypeId())),
                hasJsonPath("$._embedded.field", Matchers.not(Matchers.empty())),
                hasJsonPath("$._links.field.href", Matchers.containsString("/api/core/handles")),
                hasJsonPath("$._links.self.href", Matchers.containsString("/api/core/handles"))
        );
    }

    public static Matcher<? super Object> matchHandleByKeys(String handle, DSpaceObject dso, Integer resourceTypeID) {
        return allOf(
                hasJsonPath("$.handle", is(handle)),
                hasJsonPath("$.dso", is(dso)),
                hasJsonPath("$.resourceTypeID", is(resourceTypeID)),
                hasJsonPath("$._embedded.field", Matchers.not(Matchers.empty())),
                hasJsonPath("$._links.field.href", Matchers.containsString("/api/core/handles")),
                hasJsonPath("$._links.self.href", Matchers.containsString("/api/core/handles"))
        );
    }

}
