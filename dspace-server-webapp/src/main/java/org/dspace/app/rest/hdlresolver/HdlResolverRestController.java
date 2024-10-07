/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.hdlresolver;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.handle.HandlePlugin;
import org.dspace.handle.hdlresolver.HdlResolverDTO;
import org.dspace.handle.hdlresolver.HdlResolverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This controller is public and is useful for handle resolving,
 * whether a target handle identifier will be resolved into the
 * corresponding URL (if found), otherwise will respond a null string.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.it)
 *
 */
@RestController
@ConditionalOnProperty("handle.remote-resolver.enabled")
@RequestMapping(path = "/{hdlService:hdlresolver|resolve|listhandles|listprefixes}/")
public class HdlResolverRestController {
    static final String HDL_RESOLVER = "/hdlresolver/";
    static final String RESOLVE = "/resolve/";
    static final String LISTHANDLES = "/listhandles/";
    static final String LISTPREFIXES = "/listprefixes/";

    private static final Logger log = LogManager.getLogger();

    @Autowired
    private HdlResolverService hdlResolverService;

    @GetMapping(
        value = "**",
        produces = "application/json;charset=UTF-8"
    )
    public ResponseEntity<String> handleController(HttpServletRequest request, @PathVariable String hdlService) {
        if (HDL_RESOLVER.contains(hdlService) || RESOLVE.contains(hdlService)) {
            return resolveHandle(request, hdlService);
        } else if (LISTHANDLES.contains(hdlService)) {
            return this.listHandles(
                    request,
                    Optional.ofNullable(request.getRequestURI().split(LISTHANDLES))
                        .filter(split -> split.length > 1)
                        .map(splitted -> splitted[1])
                        .orElse(null)
            );
        } else if (LISTPREFIXES.contains(hdlService)) {
            return this.listPrefixes(request);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * REST GET Method used to find and retrieve the URL of a target Handle. It
     * should return only one item, if found, else a null body value.
     * </br>
     * Generate an <code>HttpStatus.BAD_REQUEST</code> 400 Error if the handle used
     * in path isn't valid.
     * </br>
     * The response type will be (<code>application/json;charset=UTF-8</code>)
     * a string representing an array-like list of handles:
     * </br>
     * Example:
     *      <ul>
     *          <li>Request: GET - http://{dspace.url}/hdlresolver/handleIdExample/1</li>
     *          <li>Response: 200 - ["http://localhost/handle/hanldeIdExample1"]
     *      </ul>
     *
     * @param request {@code HttpServletRequest}
     * @return One element List or <code>null</code> with status 200 - <code>HttpStatus.OK</code>
     *         or 400 - <code>HttpStatus.BAD_REQUEST</code> error
     */
    public ResponseEntity<String> resolveHandle(HttpServletRequest request, String hdlService) {
        HdlResolverDTO handleResolver =
                this.hdlResolverService.resolveBy(
                        request.getRequestURI(),
                        MessageFormat.format("{0}/{1}/", request.getContextPath(), hdlService)
                );
        if (!handleResolver.isValid()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } else {
            return new ResponseEntity<>(this.resolveToMtd(request, handleResolver), HttpStatus.OK);
        }
    }

    /**
     * REST GET Method used to list all available prefixes for handles. It
     * should return a list of prefixes, at least the default one.
     * </br>
     * The response type will be (<code>application/json;charset=UTF-8</code>)
     * a string representing an array-like list of handles:
     * </br>
     * Example:
     *      <ul>
     *          <li>Request: GET - http://{dspace.url}/listprefixes</li>
     *          <li>Response: 200 - ["123456789","prefix1","prefix2"]
     *      </ul>
     *
     * @param request {@code HttpServletRequest}
     * @return List of valid prefixes with status 200 <code>HttpStatus.OK</code>
     */
    public ResponseEntity<String> listPrefixes(HttpServletRequest request) {
        return new ResponseEntity<>(this.mapAsJson(this.hdlResolverService.listPrefixes()), HttpStatus.OK);
    }

    /**
     * REST GET Method used to list all available handles starting with target prefix.
     * It should return a list of handles.
     * </br>
     * If the controller is disabled `handle.hide.listhandles = true`,
     * then 400 - <code>HttpStatus.NOT_FOUND</code> is returned.
     * </br>
     * If the requested prefix is blank,
     * then 404 - <code>HttpStatus.BAD_REQUEST</code> is returned.
     * </br>
     * The response type will be (<code>application/json;charset=UTF-8</code>)
     * a string representing an array-like list of handles:
     * </br>
     * Example:
     *      <ul>
     *          <li>Request: GET - http://{dspace.url}/listhandles/prefix</li>
     *          <li>Response: 200 - ["prefix/zero","prefix1/one","prefix2/two"]
     *      </ul>
     *
     * @param request {@code HttpServletRequest}
     * @param prefix {@code String} representing the prefix that will be searched
     * @return List of valid prefixes with status 200 <code>HttpStatus.OK</code>
     *         or status 400 <code>HttpStatus.NOT_FOUND</code> if disabled by properties
     *         or status 404 <code>HttpStatus.BAD_REQUEST</code> if blank prefix is requested.
     */
    public ResponseEntity<String> listHandles(HttpServletRequest request, @PathVariable String prefix) {
        if (!this.hdlResolverService.isListhandlesEnabled()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (StringUtils.isBlank(prefix)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(
            this.mapAsJson(this.hdlResolverService.listHandles(ContextUtil.obtainContext(request), prefix)),
            HttpStatus.OK
        );
    }

    /**
     * Maps the handle to url.
     *
     * @param request        HttpServletRequest
     * @param handleResolver HdlResolverDTO - Handle resolver
     * @return String if found, else null String.
     */
    private String resolveToURL(HttpServletRequest request, HdlResolverDTO handleResolver) {
        return this.hdlResolverService.resolveToURL(ContextUtil.obtainContext(request), handleResolver);
    }

    /**
     * Resolves the metadata based on the given request and handle resolver.
     *
     * @param request        the HTTP request containing the metadata parameter
     * @param handleResolver the handle resolver containing the handle
     * @return a JSON representation of the URL or the handle values map, or "null" in case of an error
     */
    private String resolveToMtd(HttpServletRequest request, HdlResolverDTO handleResolver) {
        String url = resolveToURL(request, handleResolver);
        String param = request.getParameter("metadata");
        if (StringUtils.isBlank(param)) {
            return mapAsJson(url);
        }
        String handle = handleResolver.getHandle();
        try {
            Map<String, String> map = HandlePlugin.getMapHandleValues(handle);
            map.put("URL", url);
            return mapAsJson(map);
        } catch (HandleException e) {
            log.error("Failed to resolve handle values for handle: " + handle, e);
        }
        return "null";
    }

    protected String mapAsJson(final String resolvedUrl) {
        String json = "null";
        if (StringUtils.isNotEmpty(resolvedUrl)) {
            json = mapAsJson(List.of(resolvedUrl));
        }
        return json;
    }

    protected String mapAsJson(final Map<String, String> resolvedMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = "null";
        try {
            if (resolvedMap != null && !resolvedMap.isEmpty()) {
                json = objectMapper.writeValueAsString(resolvedMap);
            }
        } catch (JsonProcessingException e) {
            log.error("Error during conversion of response!", e);
        }
        return json;
    }

    protected String mapAsJson(final List<String> jsonList) {
        String json = "null";
        if (jsonList != null && !jsonList.isEmpty()) {
            try {
                json = new ObjectMapper().writeValueAsString(jsonList);
            } catch (JsonProcessingException e) {
                log.error("Error during conversion of response!", e);
            }
        }
        return json;
    }
}
