/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.core.Context;
import org.dspace.handle.external.HandleRest;
import org.dspace.handle.service.HandleClarinService;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The public shortener catalogue must not publish its internal submitter contact field.
 */
public class ExternalHandlePrivacyTest {
    private static final String EMAIL = "synthetic-owner@example.invalid";
    private static final String DESTINATION = "https://example.invalid/public-resource";

    @Test
    public void publicMagicCatalogueOmitsSubmitterEmail() throws Exception {
        HandleRest summary = summary();
        Context context = mock(Context.class);
        HandleClarinService service = mock(HandleClarinService.class);
        when(service.findAllExternalHandles(context)).thenReturn(List.of());
        when(service.convertHandleWithMagicToExternalHandle(List.of())).thenReturn(List.of());
        when(service.convertExternalHandleToHandleRest(List.of())).thenReturn(List.of(summary));
        ExternalHandleRestRepository controller = new ExternalHandleRestRepository();
        ReflectionTestUtils.setField(controller, "handleClarinService", service);
        MockMvc client = MockMvcBuilders.standaloneSetup(controller).build();
        try (MockedStatic<ContextUtil> contexts = mockStatic(ContextUtil.class)) {
            contexts.when(() -> ContextUtil.obtainContext(org.mockito.ArgumentMatchers.any())).thenReturn(context);
            client.perform(get("/api/services/handles/magic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value(DESTINATION))
                .andExpect(jsonPath("$[0].reportemail").doesNotExist())
                .andExpect(content().string(not(containsString(EMAIL))));
        }
        assertEquals(EMAIL, summary.getReportemail());
    }

    @Test
    public void summariesDoNotSerializeSubmitterEmail() throws Exception {
        HandleRest summary = summary();
        String output = new ObjectMapper().writeValueAsString(summary);
        assertFalse(output.contains(EMAIL));
        assertFalse(output.contains("reportemail"));
        assertEquals(EMAIL, summary.getReportemail());
    }

    @Test
    public void contactFieldRemainsAvailableForInternalInput() throws Exception {
        HandleRest input = new ObjectMapper().readValue("{\"reportemail\":\"" + EMAIL + "\"}", HandleRest.class);
        assertEquals(EMAIL, input.getReportemail());
    }

    private HandleRest summary() {
        HandleRest rest = new HandleRest();
        rest.setHandle("12345/SYNTHETIC");
        rest.setUrl(DESTINATION);
        rest.setTitle("Synthetic public resource");
        rest.setReportemail(EMAIL);
        return rest;
    }
}
