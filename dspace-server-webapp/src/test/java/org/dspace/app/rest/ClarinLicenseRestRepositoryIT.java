package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.converter.ClarinLicenseConverter;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.DSpaceConverter;
import org.dspace.app.rest.matcher.ClarinLicenseLabelMatcher;
import org.dspace.app.rest.matcher.ClarinLicenseMatcher;
import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.ClarinLicenseBuilder;
import org.dspace.builder.ClarinLicenseLabelBuilder;
import org.dspace.builder.WorkspaceItemBuilder;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.jsonpath.JsonPath.read;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinLicenseRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    ClarinLicenseService clarinLicenseService;

    @Autowired
    ClarinLicenseLabelService clarinLicenseLabelService;

    @Autowired
    ClarinLicenseConverter clarinLicenseConverter;

    ClarinLicense firstCLicense;
    ClarinLicense secondCLicense;

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
        clarinLicenseLabelService.update(context, firstCLicenseLabel);

        secondCLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        secondCLicenseLabel.setLabel("CCC");
        secondCLicenseLabel.setExtended(true);
        secondCLicenseLabel.setTitle("CLL Title2");
        clarinLicenseLabelService.update(context, secondCLicenseLabel);

        thirdCLicenseLabel = ClarinLicenseLabelBuilder.createClarinLicenseLabel(context).build();
        thirdCLicenseLabel.setLabel("DBC");
        thirdCLicenseLabel.setExtended(false);
        thirdCLicenseLabel.setTitle("CLL Title3");
        clarinLicenseLabelService.update(context, thirdCLicenseLabel);

        // create ClarinLicenses
        firstCLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        firstCLicense.setConfirmation(0);
        firstCLicense.setDefinition("CL Definition1");
        firstCLicense.setRequiredInfo("CL Req1");
        // add ClarinLicenseLabels to the ClarinLicense
        HashSet<ClarinLicenseLabel> firstClarinLicenseLabels = new HashSet<>();
        firstClarinLicenseLabels.add(firstCLicenseLabel);
//        firstClarinLicenseLabels.add(secondCLicenseLabel);
        firstClarinLicenseLabels.add(thirdCLicenseLabel);
        firstCLicense.setLicenseLabels(firstClarinLicenseLabels);
        clarinLicenseService.update(context, firstCLicense);

        secondCLicense = ClarinLicenseBuilder.createClarinLicense(context).build();
        secondCLicense.setConfirmation(1);
        secondCLicense.setDefinition("CL Definition2");
        secondCLicense.setRequiredInfo("CL Req2");
        // add ClarinLicenseLabels to the ClarinLicense
        HashSet<ClarinLicenseLabel> secondClarinLicenseLabels = new HashSet<>();
        secondClarinLicenseLabels.add(thirdCLicenseLabel);
        secondClarinLicenseLabels.add(firstCLicenseLabel);
        secondCLicense.setLicenseLabels(secondClarinLicenseLabels);
        clarinLicenseService.update(context, secondCLicense);

        context.restoreAuthSystemState();
    }

    @Test
    public void clarinLicensesAndLicenseLabelsAreInitialized() throws Exception {
        Assert.assertNotNull(firstCLicense);
        Assert.assertNotNull(secondCLicense);
        Assert.assertNotNull(firstCLicenseLabel);
        Assert.assertNotNull(secondCLicenseLabel);
        Assert.assertNotNull(thirdCLicenseLabel);
        Assert.assertNotNull(firstCLicense.getLicenseLabels());
        Assert.assertNotNull(secondCLicense.getLicenseLabels());
    }

    @Test
    public void findAll() throws Exception {
        getClient().perform(get("/api/core/clarinlicenses"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$._embedded.clarinlicenses", Matchers.hasItem(
                        ClarinLicenseMatcher.matchClarinLicense(firstCLicense))
                ))
                .andExpect(jsonPath("$._embedded.clarinlicenses", Matchers.hasItem(
                        ClarinLicenseMatcher.matchClarinLicense(secondCLicense))
                ))
                .andExpect(jsonPath("$._embedded.clarinlicenses[0].clarinLicenseLabel", Matchers.is(
                        ClarinLicenseLabelMatcher.matchClarinLicenseLabel(
                                Objects.requireNonNull(getNonExtendedLicenseLabel(firstCLicense.getLicenseLabels()))))
                ))
                .andExpect(jsonPath("$._embedded.clarinlicenses[0].extendedClarinLicenseLabels",
                        Matchers.hasItem(
                                ClarinLicenseLabelMatcher.matchClarinLicenseLabel(
                                        Objects.requireNonNull(getExtendedLicenseLabels(
                                                firstCLicense.getLicenseLabels())))
                                )))
                .andExpect(jsonPath("$._links.self.href",
                        Matchers.containsString("/api/core/clarinlicenses")))
        ;
    }

    @Test
    public void create() throws Exception {
        ClarinLicenseRest clarinLicenseRest = new ClarinLicenseRest();
        clarinLicenseRest.setName("name");
        clarinLicenseRest.setBitstreams(0);
        clarinLicenseRest.setConfirmation(4);
        clarinLicenseRest.setRequiredInfo("Not required");
        clarinLicenseRest.setDefinition("definition");
        clarinLicenseConverter.setExtendedClarinLicenseLabels(clarinLicenseRest, firstCLicense.getLicenseLabels(),
                Projection.DEFAULT);
        clarinLicenseConverter.setClarinLicenseLabel(clarinLicenseRest, firstCLicense.getLicenseLabels(),
                Projection.DEFAULT);

        // id of created clarin license
        AtomicReference<Integer> idRef = new AtomicReference<>();
        String authTokenAdmin = getAuthToken(admin.getEmail(), password);
        try {
            getClient(authTokenAdmin).perform(post("/api/core/clarinlicenses")
                            .content(new ObjectMapper().writeValueAsBytes(clarinLicenseRest))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is(clarinLicenseRest.getName())))
                    .andExpect(jsonPath("$.definition",
                            is(clarinLicenseRest.getDefinition())))
                    .andExpect(jsonPath("$.confirmation",
                            is(clarinLicenseRest.getConfirmation())))
                    .andExpect(jsonPath("$.requiredInfo",
                            is(clarinLicenseRest.getRequiredInfo())))
                    .andExpect(jsonPath("$.bitstreams",
                            is(clarinLicenseRest.getBitstreams())))
                    .andExpect(jsonPath("$.type",
                            is(ClarinLicenseRest.NAME)))

                    .andExpect(jsonPath("$.clarinLicenseLabel.label",
                            is(clarinLicenseRest.getClarinLicenseLabel().getLabel())))
                    .andExpect(jsonPath("$.clarinLicenseLabel.title",
                            is(clarinLicenseRest.getClarinLicenseLabel().getTitle())))
                    .andExpect(jsonPath("$.clarinLicenseLabel.extended",
                            is(clarinLicenseRest.getClarinLicenseLabel().isExtended())))
                    .andExpect(jsonPath("$.clarinLicenseLabel.type",
                            is(ClarinLicenseLabelRest.NAME)))

                    .andExpect(jsonPath("$.extendedClarinLicenseLabels[0].label",
                            is(clarinLicenseRest.getExtendedClarinLicenseLabels().get(0).getLabel())))
                    .andExpect(jsonPath("$.extendedClarinLicenseLabels[0].title",
                            is(clarinLicenseRest.getExtendedClarinLicenseLabels().get(0).getTitle())))
                    .andExpect(jsonPath("$.extendedClarinLicenseLabels[0].extended",
                            is(clarinLicenseRest.getExtendedClarinLicenseLabels().get(0).isExtended())))
                    .andExpect(jsonPath("$.extendedClarinLicenseLabels[0].type",
                            is(ClarinLicenseLabelRest.NAME)))
                    .andDo(result -> idRef.set(read(result.getResponse().getContentAsString(),
                            "$.id")));
        } finally {
            if (Objects.nonNull(idRef.get())) {
                // remove created clarin license
                ClarinLicenseBuilder.deleteClarinLicense(idRef.get());
            }
        }
    }

    private ClarinLicenseLabel getNonExtendedLicenseLabel(List<ClarinLicenseLabel> clarinLicenseLabelList) {
        for (ClarinLicenseLabel clarinLicenseLabel : clarinLicenseLabelList) {
            if (clarinLicenseLabel.isExtended()) {
                continue;
            }
            return clarinLicenseLabel;
        }
        return null;
    }

    private ClarinLicenseLabel getExtendedLicenseLabels(List<ClarinLicenseLabel> clarinLicenseLabelList) {
        for (ClarinLicenseLabel clarinLicenseLabel : clarinLicenseLabelList) {
            if (!clarinLicenseLabel.isExtended()) {
                continue;
            }
            return clarinLicenseLabel;
        }
        return null;
    }


}
