/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.apache.commons.lang.StringUtils.contains;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.removeEnd;
import static org.dspace.handle.external.ExternalHandleConstants.MAGIC_BEAN;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.validator.routines.EmailValidator;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCDate;
import org.dspace.core.Context;
import org.dspace.handle.HandlePlugin;
import org.dspace.handle.external.Handle;
import org.dspace.handle.external.HandleRest;
import org.dspace.handle.service.HandleClarinService;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REST Controller which manages the `external/Handle` which has the URL with the `@magicLindat` string
 */
@RestController
@RequestMapping("/api/services")
public class ExternalHandleRestRepository {

    private static final Logger log = LoggerFactory.getLogger(ExternalHandleRestRepository.class);

    // the handle.url column is varchar(8192) in the H2 schema, PostgreSQL does not limit it
    private static final int MAX_MAGIC_URL_LENGTH = 8192;

    private static final String UPDATE_ERROR = "Cannot update the external handle.";

    private final String EXTERNAL_HANDLE_ENDPOINT_FIND_ALL = "handles/magic";
    private final String EXTERNAL_HANDLE_ENDPOINT_SHORTEN = "handles";
    private final String EXTERNAL_HANDLE_ENDPOINT_UPDATE = "handles";

    private String prefix;

    @Autowired
    private HandleClarinService handleClarinService;

    @Autowired
    private HandleService handleService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private RandomStringGenerator randomStringGenerator;

    /**
     * Get all Handles with url which contains the `@magicLindat` string then convert them to the `external/Handle`
     * and return in the List.
     */
    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_FIND_ALL, method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<List<HandleRest>> getHandles(HttpServletResponse response,
                                                       HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);

        // get all external handles - they have `@magicLindat` string in the url
        List<org.dspace.handle.Handle> magicHandles = this.handleClarinService.findAllExternalHandles(context);

        // create the external handles from the handles with magic URL
        List<Handle> externalHandles = this.handleClarinService.convertHandleWithMagicToExternalHandle(magicHandles);

        // convert external Handles to Handle Rest objects
        List<HandleRest> externalHandlesRest =
                this.handleClarinService.convertExternalHandleToHandleRest(externalHandles);

        return new ResponseEntity<>(new ArrayList<>(externalHandlesRest), HttpStatus.OK);
    }

    /**
     * From the Handle url create some `external/Handle` with short /handle/URL
     * @param handle
     * @param response
     * @param request
     * @return
     */
    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_SHORTEN, method = RequestMethod.POST,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<Object> shortenHandle(@RequestBody Handle handle, HttpServletResponse response,
                                                   HttpServletRequest request) {
        if (validateHandle(handle)) {
            try {
                Context context = ContextUtil.obtainContext(request);

                if (Objects.isNull(context)) {
                    return new ResponseEntity<>("Context is null", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                handle.submitdate = new DCDate(new Date()).toString();
                String subprefix = (isNotBlank(handle.subprefix)) ? handle.subprefix + "-" : "";
                String magicURL = handle.generateMagicUrl();
                if (magicURL.length() > MAX_MAGIC_URL_LENGTH) {
                    return invalidShortenRequest();
                }
                String hdl = createHandle(subprefix, magicURL, context);
                if (Objects.isNull(hdl)) {
                    return new ResponseEntity<>("Failed to create the shortened handle.",
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
                context.complete();

                return new ResponseEntity<>(new Handle(hdl, magicURL), HttpStatus.OK);
            } catch (SQLException | AuthorizeException e) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return invalidShortenRequest();
    }

    private ResponseEntity<Object> invalidShortenRequest() {
        return new ResponseEntity<>(configurationService.getProperty("shortener.post.error",
                "Cannot create handle because some parameter is null or the URL is not valid"),
                HttpStatus.BAD_REQUEST);
    }


    // change only the URL
    @RequestMapping(value = EXTERNAL_HANDLE_ENDPOINT_UPDATE, method = RequestMethod.PUT,
            produces = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseEntity<Object> updateHandle(@RequestBody Handle updatedHandle, HttpServletResponse response,
                                                   HttpServletRequest request) {
        Context context = ContextUtil.obtainContext(request);
        if (Objects.isNull(context)) {
            return new ResponseEntity<>("Context is null.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (validHandleUrl(updatedHandle.url)
                && isNotBlank(updatedHandle.getHandle()) && isNotBlank(updatedHandle.getToken())) {
            try {
                // remove canonical prefix from handle string because the handle is stored in the DB without
                // the canonical prefix
                String canonicalPrefix = HandlePlugin.getCanonicalHandlePrefix();
                String oldHandleStr = Objects.isNull(canonicalPrefix) ? updatedHandle.getHandle() :
                        updatedHandle.getHandle().replace(canonicalPrefix, "");

                // load Handle object from the DB
                org.dspace.handle.Handle handleEntity =
                        this.handleClarinService.findByHandleAndMagicToken(context, oldHandleStr,
                                updatedHandle.getToken());

                if (Objects.isNull(handleEntity)) {
                    return new ResponseEntity<>("Cannot find the handle in the database.",
                            HttpStatus.NOT_FOUND);
                }

                String oldHandleMagicUrl = handleEntity.getUrl();
                String hdl = handleEntity.getHandle();

                // create externalHandle based on the handle and the URL with the `@magicLindat` string
                Handle oldExternalHandle = new Handle(hdl, oldHandleMagicUrl);

                log.info("Handle [{}] changed url from \"{}\" to \"{}\".", hdl, oldExternalHandle.url,
                        updatedHandle.url);
                oldExternalHandle.url  = updatedHandle.url;

                // this has the new url and a new token
                String newMagicUrl = oldExternalHandle.generateMagicUrl();
                if (newMagicUrl.length() > MAX_MAGIC_URL_LENGTH) {
                    return new ResponseEntity<>(UPDATE_ERROR, HttpStatus.BAD_REQUEST);
                }

                // set the new magic url as url on the entity object
                handleEntity.setUrl(newMagicUrl);

                // update handle in the DB
                context.turnOffAuthorisationSystem();
                try {
                    this.handleClarinService.save(context, handleEntity);
                } finally {
                    context.restoreAuthSystemState();
                }
                context.commit();

                // return updated external handle
                return new ResponseEntity<>(new Handle(hdl, newMagicUrl), HttpStatus.OK);
            } catch (SQLException | AuthorizeException e) {
                log.error(UPDATE_ERROR, e);
                return new ResponseEntity<>(UPDATE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return new ResponseEntity<>(UPDATE_ERROR, HttpStatus.BAD_REQUEST);
    }

    /**
     * Create new handle string with the size of `4` characters
     *
     * @param subprefix of the handle `<PREFIX>/<SUBPREFIX>-handle` e.g. `123/PMLTQ-54785632
     * @param url URL with `@magicLindat` string
     * @param context DSpace object context
     * @return shortened Handle string e.g. `5478`
     */
    private String createHandle(String subprefix, String url, Context context) throws SQLException, AuthorizeException {
        int attempts = 100;
        String handle;
        this.loadPrefix();

        for (int attempt = 0; attempt < attempts; attempt++) {
            // generate short handle
            String rnd = randomStringGenerator.generate(4).toUpperCase();
            handle = prefix + "/" + subprefix + rnd;
            // check if exists, create and return if not
            if (handleClarinService.findByHandle(context, handle) == null) {
                context.turnOffAuthorisationSystem();
                try {
                    handleClarinService.createExternalHandle(context, handle, url);
                } finally {
                    context.restoreAuthSystemState();
                }
                return handle;
            }
        }
        log.error("Cannot create handle, all attempts failed.");
        return null;
    }

    private boolean validateHandle(Handle handle) {
        if (!validHandleUrl(handle.url)) {
            return false;
        }

        // handle parameters cannot be blank
        if (isBlank(handle.url) ||
                isBlank(handle.title) ||
                isBlank(handle.reportemail)) {
            return false;
        }

        if (!EmailValidator.getInstance().isValid(handle.reportemail)) {
            return false;
        }

        // the delimiter in a part shifts the parts of the stored magic URL, including the destination URL
        return Stream.of(handle.title, handle.reportemail, handle.datasetName, handle.datasetVersion, handle.query)
                .noneMatch(part -> contains(part, MAGIC_BEAN));
    }

    private boolean validHandleUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        if (url.contains(MAGIC_BEAN)) {
            return false;
        }
        try {
            // java.net.URI rejects characters that browsers parse differently (e.g. a backslash in the authority)
            // and has a host only for an ASCII host name, so an IDN host must be sent in its punycode form
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || isBlank(uri.getHost())) {
                return false;
            }
            // host names are case-insensitive and may end with the DNS root dot
            final String host = removeEnd(uri.getHost(), ".");
            //whitelist host
            if (matchesAnyOf(host, "shortener.post.host.whitelist.regexps", Pattern.CASE_INSENSITIVE)) {
                return true;
            }
            //blacklist url
            if (matchesAnyOf(url, "shortener.post.url.blacklist.regexps", 0)) {
                return false;
            }
            //blacklist host
            if (matchesAnyOf(host, "shortener.post.host.blacklist.regexps", Pattern.CASE_INSENSITIVE)) {
                return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }
        return true;
    }

    private boolean matchesAnyOf(String tested, String configPropertyWithPatterns, int flags) {
        final String[] patterns = this.configurationService.getArrayProperty(configPropertyWithPatterns);
        if (Objects.isNull(patterns)) {
            return false;
        }

        final String pattern = String.join(";", patterns);

        String[] list = pattern.split(";");
        for (String regexp : list) {
            if (Pattern.compile(regexp.trim(), flags).matcher(tested).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load prefix from the configuration just once.
     */
    private void loadPrefix() {
        if (Objects.nonNull(this.prefix)) {
            return;
        }

        prefix = this.configurationService.getProperty("shortener.handle.prefix", "123456789");
    }

}
