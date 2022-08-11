package org.dspace.app.rest;

import org.dspace.services.ConfigurationService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This will be the entry point for the api/eperson/groups endpoint with additional paths to it
 */
@RestController
@RequestMapping("/api/help-desk")
public class HelpDeskController {

    @Autowired
    private ConfigurationService configurationService;

    @GetMapping
    public ResponseEntity<String> getHelpDeskMail() {
        JSONObject jsonHelpDesk = new JSONObject();

        String helpDeskMail = this.configurationService.getProperty("lr.help.mail");
        if (ObjectUtils.isEmpty(helpDeskMail)) {
            new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        jsonHelpDesk.put("mail", helpDeskMail);
        return new ResponseEntity<>(jsonHelpDesk.toString(), HttpStatus.OK);
    }
}
