/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dspace.app.rest.converter.ClarinLicenseConverter;
import org.dspace.app.rest.converter.ClarinLicenseLabelConverter;
import org.dspace.app.rest.model.ClarinLicenseLabelRest;
import org.dspace.app.rest.model.ClarinLicenseRest;
import org.dspace.app.rest.projection.DefaultProjection;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.content.clarin.ClarinLicense;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.dspace.content.service.clarin.ClarinLicenseLabelService;
import org.dspace.content.service.clarin.ClarinLicenseService;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinLicenseImportControllerIT extends AbstractControllerIntegrationTest {

    private JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(true);

    @Autowired
    private ClarinLicenseLabelService clarinLicenseLabelService;

    @Autowired
    private ClarinLicenseService clarinLicenseService;
    @Autowired
    private ClarinLicenseConverter clarinLicenseConverter;

    @Autowired
    private ClarinLicenseLabelConverter clarinLicenseLabelConverter;

    private Dictionary<String, ClarinLicenseLabel> licenseLabelDictionary = new Hashtable<>();
    private Dictionary<String, ClarinLicense> licenseDictionary = new Hashtable<>();
    private Dictionary<Integer, Set<Integer>> extendedMappingDictionary = new Hashtable<>();


    @Test
    public void importLicensesTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JSONParser parser = new JSONParser();
        BufferedReader bufferReader = new BufferedReader(new FileReader("C:/DSpace-Clarin/jm.license_label.json"));

        Object obj;
        String line;
        ClarinLicenseLabel clarinLicenseLabel;
        JSONObject jsonObject;
        List<ClarinLicenseLabel> clarinLabels = new ArrayList<>();
        List<ClarinLicenseLabelRest> clarinLabelsRest = new ArrayList<>();
        while ((line = bufferReader.readLine()) != null) {
            obj = parser.parse(line);
            jsonObject = (JSONObject)obj;
            clarinLicenseLabel = new ClarinLicenseLabel();
            clarinLicenseLabel.setId(Integer.parseInt(jsonObject.get("label_id").toString()));
            clarinLicenseLabel.setLabel(jsonObject.get("label").toString());
            clarinLicenseLabel.setTitle(jsonObject.get("title").toString());
            clarinLicenseLabel.setExtended(Boolean.parseBoolean(jsonObject.get("is_extended").toString()));
            licenseLabelDictionary.put(clarinLicenseLabel.getLabel(), clarinLicenseLabel);
            clarinLabelsRest.add(clarinLicenseLabelConverter.convert(clarinLicenseLabel, DefaultProjection.DEFAULT));
        }

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/licenses/import/labels")
                        .content(mapper.writeValueAsBytes(clarinLabelsRest))
                        .contentType(contentType))
                .andExpect(status().isOk());

        //extendedMapping
        bufferReader = new BufferedReader(new FileReader("C:/DSpace-Clarin/jm.license_label_extended_mapping.json"));

        List<JsonNode> nodes = new ArrayList<>();
        ObjectNode node;
        while ((line = bufferReader.readLine()) != null) {
            obj = parser.parse(line);
            jsonObject = (JSONObject)obj;
            node = jsonNodeFactory.objectNode();
            node.set("mapping_id", jsonNodeFactory.textNode(jsonObject.get("mapping_id").toString()));
            node.set("license_id", jsonNodeFactory.textNode(jsonObject.get("license_id").toString()));
            node.set("label_id", jsonNodeFactory.textNode(jsonObject.get("label_id").toString()));
            if (extendedMappingDictionary.get(Integer.parseInt(jsonObject.get("license_id").toString())) == null) {
                extendedMappingDictionary.put(Integer.parseInt(jsonObject.get("license_id").toString()), new HashSet<>());
            }
            extendedMappingDictionary.get(Integer.parseInt(jsonObject.get("license_id").toString())).add(Integer.parseInt(jsonObject.get("label_id").toString()));
            nodes.add(node);
        }

        getClient(adminToken).perform(post("/api/licenses/import/extendedMapping")
                        .content(mapper.writeValueAsBytes(nodes))
                        .contentType(contentType))
                .andExpect(status().isOk());


        //licenses
        bufferReader = new BufferedReader(new FileReader("C:/DSpace-Clarin/jm.license_definition.json"));

        List<ClarinLicense> licenses = new ArrayList<>();
        List<ClarinLicenseRest> licensesRest = new ArrayList<>();
        ClarinLicense license;
        while ((line = bufferReader.readLine()) != null) {
            obj = parser.parse(line);
            jsonObject = (JSONObject)obj;
            license = new ClarinLicense();
            license.setId(Integer.parseInt(jsonObject.get("license_id").toString()));
            license.setName(jsonObject.get("name").toString());
            license.setDefinition(jsonObject.get("definition").toString());
            //license.setEpersonID(Integer.parseInt(jsonObject.get("eperson_id").toString()));
            Set<ClarinLicenseLabel> labels = new HashSet<>();
            labels.add(this.clarinLicenseLabelService.find(context, Integer.parseInt(jsonObject.get("label_id").toString())));
            license.setLicenseLabels(labels);
            license.setConfirmation(Integer.parseInt(jsonObject.get("confirmation").toString()));
            license.setRequiredInfo(jsonObject.get("required_info") != null ? jsonObject.get("required_info").toString() : null);
            licenseDictionary.put(license.getName(), license);
            if (extendedMappingDictionary.get(license.getID()) == null) {
                extendedMappingDictionary.put(license.getID(), new HashSet<>());
            }
            extendedMappingDictionary.get(license.getID()).add(license.getLicenseLabels().get(0).getID());
            licensesRest.add(clarinLicenseConverter.convert(license, DefaultProjection.DEFAULT));
        }

        getClient(adminToken).perform(post("/api/licenses/import/licenses")
                        .content(mapper.writeValueAsBytes(licensesRest))
                        .contentType(contentType))
                .andExpect(status().isOk());

        //check
        context.turnOffAuthorisationSystem();
        List<ClarinLicense> clarinLicenses = this.clarinLicenseService.findAll(context);
        Assert.assertEquals(clarinLicenses.size(), licenseDictionary.size());
        //control of the license mapping
        for (ClarinLicense clarinLicense: clarinLicenses) {
            ClarinLicense oldLicense = licenseDictionary.get(clarinLicense.getName());
            Assert.assertNotNull(oldLicense);
            Assert.assertEquals(clarinLicense.getConfirmation(), oldLicense.getConfirmation());
            Assert.assertEquals(clarinLicense.getDefinition(), oldLicense.getDefinition());
            Assert.assertEquals(clarinLicense.getRequiredInfo(), oldLicense.getRequiredInfo());
            Assert.assertEquals(clarinLicense.getLicenseLabels().size(), extendedMappingDictionary.get(oldLicense.getID()).size());
            List<ClarinLicenseLabel> clarinLicenseLabels = clarinLicense.getLicenseLabels();
            for (ClarinLicenseLabel label: clarinLicenseLabels) {
                ClarinLicenseLabel oldLabel = licenseLabelDictionary.get(label.getLabel());
                Assert.assertEquals(label.getLabel(), oldLabel.getLabel());
                Assert.assertEquals(label.getTitle(), oldLabel.getTitle());
                Assert.assertEquals(label.isExtended(), oldLabel.isExtended());
            }
        }

        //control of the license label mapping
        List<ClarinLicenseLabel> clarinLicenseLabels = this.clarinLicenseLabelService.findAll(context);
        Assert.assertEquals(clarinLicenseLabels.size(), licenseLabelDictionary.size());
        for (ClarinLicenseLabel label: clarinLicenseLabels) {
            ClarinLicenseLabel oldLabel = licenseLabelDictionary.get(label.getLabel());
            Assert.assertNotNull(oldLabel);
            Assert.assertEquals(label.getTitle(), oldLabel.getTitle());
            Assert.assertEquals(label.getIcon(), oldLabel.getIcon());
        }
        context.restoreAuthSystemState();
    }
}
