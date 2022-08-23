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

    public static Matcher<? super Object> matchProperties(String url, String title, String repository, String submitDate, String reporteMail, String subprefix, String handle) {
        return allOf(
                hasJsonPath("$.url", is(url)),
                hasJsonPath("$.title", is(title)),
                hasJsonPath("$.repository", is(repository)),
                hasJsonPath("$.submitdate", is(submitDate)),
                hasJsonPath("$.reportemail", is(reporteMail)),
                hasJsonPath("$.subprefix", is(subprefix)),
                hasJsonPath("$.handle", is(handle))
        );
    }
}
