/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.authorization;

import com.github.jsonldjava.utils.Obj;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.dspace.app.rest.exception.DownloadTokenExpiredException;
import org.dspace.app.rest.exception.MissingLicenseAgreementException;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseServiceImpl;
import org.dspace.content.factory.ClarinServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.clarin.ClarinLicenseResourceUserAllowanceService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.utils.DSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

@Component
public class AuthorizationBitstreamUtils {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationBitstreamUtils.class);

    @Autowired
    ClarinLicenseService clarinLicenseService;
    @Autowired
    ClarinLicenseResourceUserAllowanceService clarinLicenseResourceUserAllowanceService;
    @Autowired
    private Utils utils;
    @Autowired
    BitstreamService bitstreamService;

    /**
     * Check if the current user is authorized to download the bitstream in the three steps:
     * 1. If the current user is submitter of the item where the current bitstream is -> the user is authorized.
     * 2. If the request contains token which is verified -> the user is authorized.
     * 3. If the bitstream license requires confirmation every time or the user didn't fill in required
     *    metadata for the bitstream's license -> the user is not authorized.
     * @param context
     * @param baseObjectRest
     * @return
     * @throws SQLException
     */
    public boolean authorizeBitstream(Context context, BaseObjectRest baseObjectRest) throws SQLException,
            AuthorizeException {
        // Load the current user
        EPerson currentUser = context.getCurrentUser();
        boolean userExists = Objects.nonNull(currentUser);

        // Load the current user ID or if the user do not exist set ID to null
        UUID userID = null; // user not logged in
        if (userExists) {
            userID = currentUser.getID();
        }

        if (!(baseObjectRest instanceof BitstreamRest)) {
            return false;
        }

        String resourceID = String.valueOf(baseObjectRest.getId());

        // Get DSpaceObject based on the BaseObjectRest
        Bitstream bitstream = (Bitstream) utils.getDSpaceAPIObjectFromRest(context, baseObjectRest);
        if (Objects.isNull(bitstream)) {
            return false;
        }

        // 1. If the current user is submitter of the item where the current bitstream is -> the user is authorized.
        if (userIsSubmitter(context, bitstream, currentUser, userID)) {
            return true;
        }

        // 2. If the request contains token which is verified -> the user is authorized.
        if (isTokenVerified(resourceID)) {
            return true;
        }

        // 3. If the bitstream license requires confirmation every time or the user didn't fill in required
        // metadata for the bitstream's license -> the user is not authorized.
        return isUserAllowedToAccessTheResource(userID, resourceID);
    }

    private boolean userIsSubmitter(Context context, Bitstream bitstream, EPerson currentUser, UUID userID) {
        try {
            // Load Bitstream's Item, the Item contains the Bitstream
            Item item = (Item) bitstreamService.getParentObject(context, bitstream);

            // If the Item is submitted by the current user, the submitter is always authorized to access his own
            // bitstream
            EPerson submitter = null;
            if (Objects.nonNull(item)) {
                submitter = item.getSubmitter();
            }

            if (Objects.nonNull(submitter) || Objects.nonNull(userID)) {
                if (Objects.nonNull(currentUser) &&
                        StringUtils.equals(submitter.getID().toString(), userID.toString())) {
                    return true;
                }
            }
        } catch (SQLException sqle) {
            log.error("Failed to get parent object for bitstream", sqle);
            return false;
        } catch (ClassCastException ex) {
            // parent object is not an Item
            // special bitstreams e.g. images of community/collection
            return false;
        }

        return false;
    }

    private boolean isTokenVerified(String resourceID) throws DownloadTokenExpiredException {
        // Load the current request.
        HttpServletRequest request = new DSpace().getRequestService().getCurrentRequest()
                .getHttpServletRequest();


        if (Objects.isNull(request)) {
            return false;
        }

        // Load the token from the request
        String dtoken = null;
        try{
            dtoken = request.getParameter("dtoken");
        }catch(IllegalStateException e){
            //If the dspace kernel is null (eg. when we get here from OAI)
        }catch(Exception e){
        }

        if (StringUtils.isBlank(dtoken)) {
            return false;
        }

        boolean tokenFound = clarinLicenseResourceUserAllowanceService.verifyToken(resourceID, dtoken);
        // Check token
        if(tokenFound) { // database token match with url token
            return true;
        } else {
            throw new DownloadTokenExpiredException("The download token is invalid or expires.");
        }
    }

    private boolean isUserAllowedToAccessTheResource(UUID userID, String resourceID)
            throws MissingLicenseAgreementException {
        boolean allowed = clarinLicenseResourceUserAllowanceService
                .isUserAllowedToAccessTheResource(userID, resourceID);

        if (!allowed) {
            throw new MissingLicenseAgreementException("Missing license agreement!");
        }
        return true;
    }

}
