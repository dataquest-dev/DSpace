package org.dspace.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataValue;
import org.junit.Assert;

import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProvenanceMetadataCheck {
    private JsonNode suite;
    private ObjectMapper objectMapper = new ObjectMapper();

    public void ProvenanceMetadataCheck() throws Exception {
        suite = objectMapper.readTree(getClass().getResourceAsStream("provenance-patch-suite.json"));
    }

    private String provenanceMetadataModified(String metadata) {
        // Regex to match the date pattern
        String datePattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";
        Pattern pattern = Pattern.compile(datePattern);
        Matcher matcher = pattern.matcher(metadata);
        String rspModifiedProvenance = metadata;
        while (matcher.find()) {
            String dateString = matcher.group(0);
            rspModifiedProvenance = rspModifiedProvenance.replaceAll(dateString, "");
        }
        return rspModifiedProvenance;
    }

    public void objectCheck(DSpaceObject obj, String respKey) {
        String expectedSubStr = suite.get(respKey).asText();
        List<MetadataValue> metadata = obj.getMetadata();
        boolean contain = false;
        for (MetadataValue value : metadata) {
            if (!Objects.equals(value.getMetadataField().toString(), "dc_description_provenance")) {
                continue;
            }
            if (provenanceMetadataModified(value.getValue()).contains(expectedSubStr)) {
                contain = true;
                break;
            }
        }
        if (!contain) {
            Assert.fail("Metadata provenance do not contain expected data: " + expectedSubStr);
        }
    }
}
