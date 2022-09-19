package org.dspace.app.rest;

import org.dspace.app.rest.matcher.ClarinLicenseLabelMatcher;
import org.dspace.app.rest.matcher.ClarinLicenseMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinLicenseLabelRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinLicenseLabelService clarinLicenseLabelService;

    ClarinLicenseLabel firstCLicenseLabel;
    ClarinLicenseLabel secondCLicenseLabel;
    ClarinLicenseLabel thirdCLicenseLabel;

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();
        // create LicenseLabels
        firstCLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        firstCLicenseLabel.setLabel("CC");
        firstCLicenseLabel.setExtended(true);
        firstCLicenseLabel.setTitle("CLL Title1");
        firstCLicenseLabel.setIcon(new byte[100]);
        clarinLicenseLabelService.update(context, firstCLicenseLabel);

        secondCLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        secondCLicenseLabel.setLabel("CCC");
        secondCLicenseLabel.setExtended(true);
        secondCLicenseLabel.setTitle("CLL Title2");
        secondCLicenseLabel.setIcon(new byte[200]);
        clarinLicenseLabelService.update(context, secondCLicenseLabel);

        thirdCLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        thirdCLicenseLabel.setLabel("DBC");
        thirdCLicenseLabel.setExtended(false);
        thirdCLicenseLabel.setTitle("CLL Title3");
        thirdCLicenseLabel.setIcon(new byte[300]);
        clarinLicenseLabelService.update(context, thirdCLicenseLabel);
        context.restoreAuthSystemState();
    }

    @Test
    public void clarinLicenseLabelsAreInitialized() throws Exception {
        Assert.assertNotNull(firstCLicenseLabel);
        Assert.assertNotNull(secondCLicenseLabel);
        Assert.assertNotNull(thirdCLicenseLabel);
    }

    @Test
    public void findAll() throws Exception {
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        getClient(authTokenAdmin).perform(get("/api/core/clarinlicenselabels"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._embedded.clarinlicenselabels", Matchers.hasItem(
                        ClarinLicenseLabelMatcher.matchClarinLicenseLabel(firstCLicenseLabel))
                ))
                .andExpect(jsonPath("$._embedded.clarinlicenselabels", Matchers.hasItem(
                        ClarinLicenseLabelMatcher.matchClarinLicenseLabel(secondCLicenseLabel))
                ))
                .andExpect(jsonPath("$._embedded.clarinlicenselabels", Matchers.hasItem(
                        ClarinLicenseLabelMatcher.matchClarinLicenseLabel(thirdCLicenseLabel))
                ))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString("/api/core/clarinlicenselabels")))
        ;
    }

}
