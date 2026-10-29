package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsAndOpenApiTest {

    private static final String FRONT_END = "http://localhost:5173";
    private static final String OTHER_SITE = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsPreflightFromTheFrontEnd() throws Exception {
        mockMvc.perform(preflight(FRONT_END))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END));
    }

    @Test
    void refusesPreflightFromAnyOtherOrigin() throws Exception {
        mockMvc.perform(preflight(OTHER_SITE))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void refusesACrossOriginPostFromAnyOtherOrigin() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").header(HttpHeaders.ORIGIN, OTHER_SITE)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/work-requests/batch").header(HttpHeaders.ORIGIN, FRONT_END)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END));
    }

    @Test
    void publishesTheApiDescription() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Work Request Service"))
                .andExpect(jsonPath("$.paths['/api/work-requests/batch'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/work-requests/batch'].post.requestBody.content['application/json'].examples")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/work-requests/sample'].get").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void documentedExampleBehavesAsItsNameSays() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(WorkRequestController.EXAMPLE_BATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-1")))
                .andExpect(jsonPath("$.accepted[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.problems[*].code").value(contains("INVALID_VALUE", "DUPLICATE")))
                .andExpect(jsonPath("$.problems[0].message").value(containsString("invalid priority 'URGENT'")))
                .andExpect(jsonPath("$.problems[1].escalatedTo").value("CRITICAL"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder preflight(String origin) {
        return options("/api/work-requests/batch")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");
    }
}
