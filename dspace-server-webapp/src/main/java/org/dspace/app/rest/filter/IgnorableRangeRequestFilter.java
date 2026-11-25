/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.filter;

import static org.dspace.app.rest.utils.HttpHeadersInitializer.IGNORE_RANGE;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpHeaders;

/**
 * Lets a controller hide the Range header, e.g. when If-Range no longer matches.
 * Spring reads Range only after the controller has returned, so ignoring it is not enough.
 *
 * @see org.dspace.app.rest.utils.HttpHeadersInitializer#IGNORE_RANGE
 */
public class IgnorableRangeRequestFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //noop
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && httpRequest.getHeader(HttpHeaders.RANGE) != null) {
            chain.doFilter(new IgnorableRangeRequest(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        //noop
    }

    /**
     * Hides the Range header once the request has been marked with {@code IGNORE_RANGE}.
     */
    private static class IgnorableRangeRequest extends HttpServletRequestWrapper {

        IgnorableRangeRequest(HttpServletRequest request) {
            super(request);
        }

        private boolean rangeIgnored() {
            return Boolean.TRUE.equals(getAttribute(IGNORE_RANGE));
        }

        private boolean hides(String headerName) {
            return HttpHeaders.RANGE.equalsIgnoreCase(headerName) && rangeIgnored();
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
            if (!rangeIgnored()) {
                return super.getHeaderNames();
            }
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(this::hides);
            return Collections.enumeration(names);
        }
    }
}
