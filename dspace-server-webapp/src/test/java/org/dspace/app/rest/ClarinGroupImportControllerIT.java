package org.dspace.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.matcher.GroupMatcher;
import org.dspace.app.rest.model.GroupRest;
import org.dspace.app.rest.model.MetadataRest;
import org.dspace.app.rest.model.MetadataValueRest;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.GroupBuilder;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.jayway.jsonpath.JsonPath.read;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ClarinGroupImportControllerIT  extends AbstractControllerIntegrationTest {
    @Test
    public void importGroupTest() throws Exception {
        // hold the id of the created workflow item
        AtomicReference<UUID> idRef = new AtomicReference<>();
        AtomicReference<UUID> idRefNoEmbeds = new AtomicReference<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            GroupRest groupRest = new GroupRest();
            String groupDescription = "test description";

            MetadataRest metadata = new MetadataRest();
            metadata.put("dc.description", new MetadataValueRest(groupDescription));
            groupRest.setMetadata(metadata);

            String authToken = getAuthToken(admin.getEmail(), password);
            getClient(authToken).perform(post("/api/groups/import")
                            .content(mapper.writeValueAsBytes(groupRest))
                            .contentType(contentType))
                    .andExpect(status().isOk())
                    .andDo(result -> idRef
                            .set(UUID.fromString(read(result.getResponse().getContentAsString(), "$.id")))
                    );

            GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
            Group group = groupService.find(context, idRef.get());

            assertEquals(
                    groupService.getMetadata(group, "dc.description"),
                    groupDescription
            );

        } finally {
            // remove the created group if any
            GroupBuilder.deleteGroup(idRef.get());
            GroupBuilder.deleteGroup(idRefNoEmbeds.get());
        }
    }
}
