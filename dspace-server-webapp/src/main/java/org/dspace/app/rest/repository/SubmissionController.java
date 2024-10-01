package org.dspace.app.rest.repository;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.ClarinAutoRegistrationController;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.RestAddressableModel;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.services.ConfigurationService;
import org.dspace.web.ContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;


@RestController
@RequestMapping("/api/" + RestAddressableModel.SUBMISSION)
public class SubmissionController {
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(SubmissionController.class);

    @Autowired
    WorkspaceItemService workspaceItemService;

    @Autowired
    ConfigurationService configurationService;

    @PreAuthorize("hasPermission(#wsoId, 'WORKSPACEITEM', 'WRITE')")
    @RequestMapping(method = RequestMethod.GET, value = "share")
    public ResponseEntity generateShareLink(@RequestParam(name = "workspaceitemid", required = false) Integer wsoId,
                                          HttpServletRequest request) throws SQLException, AuthorizeException {

        Context context = ContextUtil.obtainContext(request);
        // Check the context is not null
        // TODO log
        if (context == null) {
            return null;
        }
        // Get workspace item from ID
        WorkspaceItem wsi = workspaceItemService.find(context, wsoId);
        // Check the wsi does exist
        // TODO log
        if (wsi == null) {
            return null;
        }

        // Generate a share link
        String shareToken = generateShareToken();

        // Update workspace item with share link
        wsi.setShareToken(shareToken);
        workspaceItemService.update(context, wsi);
        // Send email to submitter with share link
        // Get submitter email
        EPerson currentUser = context.getCurrentUser();
        // TODO log
        if (currentUser == null) {
            return null;
        }
        String shareLink = sendShareLinkEmail(context, wsi, currentUser);
        if (StringUtils.isEmpty(shareLink)) {
            // TODO log and return something
        }

        // Send share link in response
        return ResponseEntity.ok().body(shareLink);
    }

    private static String generateShareToken() {
        // UUID generates a 36-char string with hyphens, so we can strip them to get a 32-char string
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    private String sendShareLinkEmail(Context context, WorkspaceItem wsi, EPerson currentUser) {
        // Get the UI URL from the configuration
        String uiUrl = configurationService.getProperty("dspace.ui.url");
        String helpDeskEmail = configurationService.getProperty("lr.help.mail", "");
        String helpDeskPhoneNum = configurationService.getProperty("lr.help.phone", "");
        String dspaceName = configurationService.getProperty("dspace.name", "");
        String dspaceNameShort = configurationService.getProperty("dspace.name.short", "");
        // Get submitter email
        String email = currentUser.getEmail();
        // Compose the url with the share token. The user will be redirected to the UI.
        String shareTokenUrl = uiUrl + "/share-submission?id= " + wsi.getID() + "&share_token=" + wsi.getShareToken();
        try {
            Locale locale = context.getCurrentLocale();
            Email bean = Email.getEmail(I18nUtil.getEmailFilename(locale, "share_submission"));
            bean.addArgument(shareTokenUrl);
            bean.addArgument(helpDeskEmail);
            bean.addArgument(helpDeskPhoneNum);
            bean.addArgument(dspaceNameShort);
            bean.addArgument(dspaceName);
            bean.addArgument(uiUrl);
            bean.addRecipient(email);
            bean.send();
        } catch (MessagingException | IOException e) {
            log.error("Unable send the email because: " + e.getMessage());
            return null;
        }
        return shareTokenUrl;
    }
}
