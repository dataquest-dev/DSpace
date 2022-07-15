/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.matcher;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

import org.dspace.handle.Handle;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;


/**
 * Utility class to construct a Matcher for a handle.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
public class HandleMatcher {

    private HandleMatcher() { }

    public static Matcher<? super Object> matchHandle(Handle handle) {
        return allOf(
                hasJsonPath("$.handle", is(handle.getHandle())),
                hasJsonPath("$.resourceTypeID", is(handle.getResourceTypeId())),
                hasJsonPath("$._embedded.dspaceObject", Matchers.not(Matchers.empty())),
                hasJsonPath("$._links.dspaceObject.href", Matchers.containsString("/api/core/handles")),
                hasJsonPath("$._links.self.href", Matchers.containsString("/api/core/handles"))
        );
    }

    public static Matcher<? super Object> matchHandleByKeys(Handle handle) {
        return allOf(
                hasJsonPath("$.handle", is(handle.getHandle())),
                hasJsonPath("$.resourceTypeID", is(handle.getResourceTypeId())),
                hasJsonPath("$._embedded.dspaceObject", Matchers.not(Matchers.empty())),
                hasJsonPath("$._links.dspaceObject.href", Matchers.containsString("/api/core/handles")),
                hasJsonPath("$._links.self.href", Matchers.containsString("/api/core/handles"))
        );
    }

}
