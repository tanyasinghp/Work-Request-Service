package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A 1 KB limit keeps these cheap; the 2 MB default is checked in WorkRequestControllerTest. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.request.max-body-size=1KB")
class RequestBodyLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsABodyOverTheLimitWithTheUsualErrorBody() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(jsonArrayOf(1025)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message").value("Request body is larger than the 1 KB limit"));
    }

    @Test
    void acceptsABodyExactlyAtTheLimit() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(jsonArrayOf(1024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace[0].in").value(0));
    }

    @Test
    void rejectsBeforeParsingSoEvenMalformedOversizedBodiesGet413() throws Exception {
        // Not JSON at all: a 413 rather than a 400 proves the size check runs before Jackson.
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content("x".repeat(2000)))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void leavesRequestsWithoutABodyAlone() throws Exception {
        mockMvc.perform(get("/api/work-requests/sample")).andExpect(status().isOk());
    }

    /** "[", padding, "]": valid JSON (an empty array) of exactly {@code bytes} bytes. */
    static String jsonArrayOf(int bytes) {
        return "[" + " ".repeat(bytes - 2) + "]";
    }
}
