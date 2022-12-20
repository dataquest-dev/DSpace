/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.minidev.json.JSONObject;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.content.clarin.ClarinLicenseLabel;
import org.json.JSONArray;
import org.junit.Test;
import org.json.simple.parser.*;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinLicenseImportControllerIT extends AbstractControllerIntegrationTest {

    @Test
    public void importLicenseLabel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = new String(Files.readAllBytes(Paths.get("C:/DSpace-Clarin/jm.license_label.json")));
        String[] labels = json.split("\n");

        List<ClarinLicenseLabel> clarinLabels = new ArrayList<>();
        ClarinLicenseLabel clarinLicenseLabel = null;
        for (int i = 1; i < labels.length - 1; i++) {
            String[] label = labels[i].split(",");
            clarinLicenseLabel = new ClarinLicenseLabel();
            clarinLicenseLabel.setId(Integer.parseInt(label[0].split(":")[1]));
            clarinLicenseLabel.setLabel(label[1].split(":")[1]);
            clarinLicenseLabel.setTitle(label[2].split(":")[1]);
            clarinLicenseLabel.setExtended(Boolean.parseBoolean(label[3].split(":")[1]));
            clarinLabels.add(clarinLicenseLabel);
        }

        String adminToken = getAuthToken(admin.getEmail(), password);
        getClient(adminToken).perform(post("/api/licenses/import/labels")
                        .content(mapper.writeValueAsBytes(clarinLabels))
                        .contentType(contentType))
                .andExpect(status().isOk());
    }

    @Test
    public void importLicenseLabelMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = new String(Files.readAllBytes(Paths.get("C:/DSpace-Clarin/jm.license_label_extended_mapping.json")));
        String[] labels = json.split("\n");

//        List<> clarinLabels = new ArrayList<>();
//        ClarinLicenseLabel clarinLicenseLabel = null;
//        for (int i = 1; i < labels.length - 1; i++) {
//            String[] label = labels[i].split(",");
//            clarinLicenseLabel = new ClarinLicenseLabel();
//            clarinLicenseLabel.setId(Integer.parseInt(label[0].split(":")[1]));
//            clarinLicenseLabel.setLabel(label[1].split(":")[1]);
//            clarinLicenseLabel.setTitle(label[2].split(":")[1]);
//            clarinLicenseLabel.setExtended(Boolean.parseBoolean(label[3].split(":")[1]));
//            clarinLabels.add(clarinLicenseLabel);
//        }

//        String adminToken = getAuthToken(admin.getEmail(), password);
//        getClient(adminToken).perform(post("/api/licenses/import/extendedMapping")
//                        .content(mapper.writeValueAsBytes(clarinLabels))
//                        .contentType(contentType))
//                .andExpect(status().isOk());
    }

}
