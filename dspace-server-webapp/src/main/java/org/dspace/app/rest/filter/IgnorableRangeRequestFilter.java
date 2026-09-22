/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * A Servlet Filter that wraps requests carrying a Range header, so that a controller can take that
 * header back. Spring only reads Range once the controller has returned, so a controller which
 * decides the range no longer applies - an If-Range that stopped matching - has to hide the header
 * rather than ignore it.
 *
 * @see org.dspace.app.rest.utils.HttpHeadersInitializer
 */
public class IgnorableRangeRequestFilter implements Filter {

    private static final String RANGE = "Range";
    private static final String IGNORE_RANGE = IgnorableRangeRequestFilter.class.getName() + ".ignoreRange";

    /**
     * Serve the rest of this request as if the client had never sent a Range header.
     *
     * @param request the request being handled
     */
    public static void ignoreRange(HttpServletRequest request) {
        request.setAttribute(IGNORE_RANGE, Boolean.TRUE);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //noop
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest && ((HttpServletRequest) request).getHeader(RANGE) != null) {
            chain.doFilter(new IgnorableRangeRequest((HttpServletRequest) request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        //noop
    }

    /**
     * Hides the Range header once {@link #ignoreRange(HttpServletRequest)} has been called.
     */
    private static class IgnorableRangeRequest extends HttpServletRequestWrapper {

        IgnorableRangeRequest(HttpServletRequest request) {
            super(request);
        }

        private boolean hides(String headerName) {
            return RANGE.equalsIgnoreCase(headerName) && Boolean.TRUE.equals(getAttribute(IGNORE_RANGE));
        }

        @Override
        public String getHeader(String name) {
            return hides(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return hides(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(this::hides);
            return Collections.enumeration(names);
        }
    }
}
