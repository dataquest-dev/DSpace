/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.requestitem.service.RequestItemService;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.AuthorizationBitstreamUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.RequestService;
import org.dspace.services.model.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Methods of this class are used on PreAuthorize annotations to decide whether a request-a-copy access
 * token may authorize a bitstream download.
 * <p>
 * CLARIN gates every restricted download behind its own licence flow. Vanilla DSpace 9 added a second,
 * independent way in: {@code BitstreamRestController.retrieve} accepted a request-a-copy access token as
 * proof of authorization on its own. A non-null token therefore streamed the content past the resource
 * policies <i>and</i> past the CLARIN licence gate.
 * <p>
 * Request-a-copy stays enabled (owner decision O-8): a token still grants access to a file the caller has
 * no READ policy for, but only after the same CLARIN licence check a normal download goes through. That
 * check is not re-implemented here - {@link AuthorizationBitstreamUtils#authorizeBitstream} is the single
 * implementation, and it is the very method
 * {@code AuthorizeServiceImpl.authorizeAction} calls for a download without a token. Wiring the token path
 * into it is what keeps the {@code dtoken} licence flow and the {@code accessToken} flow in agreement.
 *
 * @author Milan Majchrak (milan.majchrak at dataquest.sk)
 */
@Component(value = "clarinBitstreamAccessTokenSecurity")
public class ClarinBitstreamAccessTokenSecurityBean {

    private static final Logger log = LogManager.getLogger(ClarinBitstreamAccessTokenSecurityBean.class);

    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private RequestItemService requestItemService;
    @Autowired
    private AuthorizationBitstreamUtils authorizationBitstreamUtils;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private RequestService requestService;

    /**
     * Check whether the supplied request-a-copy access token authorizes downloading the given bitstream.
     * <p>
     * The token is accepted only when <b>both</b> hold:
     * <ol>
     *     <li>request-a-copy is enabled and the token is valid for this bitstream (accepted request, not
     *     expired, right bitstream) - {@link RequestItemService#authorizeAccessByAccessToken};</li>
     *     <li>the CLARIN licence gate lets the current user have the bitstream -
     *     {@link AuthorizationBitstreamUtils#authorizeBitstream}.</li>
     * </ol>
     * A caller holding a valid token for a bitstream behind a CLARIN licence they have not agreed to gets
     * {@code false} here, so Spring Security answers 401/403 exactly as it does for a normal download that
     * the licence gate refuses, and the UI can send the user to the licence page.
     *
     * @param uuid bitstream ID from the request path
     * @param accessToken request-a-copy access token from the request, may be null
     * @return true only if the token is valid AND the CLARIN licence gate passes
     */
    public boolean canDownloadWithAccessToken(UUID uuid, String accessToken) {
        if (uuid == null || StringUtils.isBlank(accessToken)) {
            return false;
        }

        // If request-a-copy is switched off, no access token authorizes anything.
        if (configurationService.getProperty("request.item.type") == null) {
            return false;
        }

        Request currentRequest = requestService.getCurrentRequest();
        if (currentRequest == null || currentRequest.getHttpServletRequest() == null) {
            return false;
        }
        Context context = ContextUtil.obtainContext(currentRequest.getHttpServletRequest());
        if (context == null) {
            return false;
        }

        try {
            Bitstream bitstream = bitstreamService.find(context, uuid);
            if (bitstream == null || bitstream.isDeleted()) {
                // Let the REST layer answer 404; a token must not turn a missing bitstream into a 200.
                return false;
            }

            // 1. The access token itself must be valid for this bitstream.
            requestItemService.authorizeAccessByAccessToken(context, bitstream, accessToken);

            // 2. And the CLARIN licence gate must pass, the same call a download without a token makes
            //    through AuthorizeServiceImpl.authorizeAction. Throws MissingLicenseAgreementException or
            //    DownloadTokenExpiredException (both AuthorizeException) when the licence is not satisfied.
            authorizationBitstreamUtils.authorizeBitstream(context, bitstream);

            return true;
        } catch (AuthorizeException e) {
            log.debug("Access token did not authorize download of bitstream {}: {}", uuid, e.getMessage());
            return false;
        } catch (SQLException e) {
            log.error("Failed to check the access token for bitstream " + uuid, e);
            return false;
        }
    }
}
