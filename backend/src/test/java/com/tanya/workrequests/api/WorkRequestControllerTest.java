package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void processesTheSampleBatchIntoTheKnownQueue() throws Exception {
        postBatch(sampleJson())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accepted[*].requestId")
                        .value(contains("REQ-101", "REQ-102", "REQ-104", "REQ-110", "REQ-111", "REQ-112")))
                .andExpect(jsonPath("$.accepted[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.problems", hasSize(7)))
                .andExpect(jsonPath("$.problems[?(@.code == 'MISSING_FIELD')]", hasSize(3)))
                .andExpect(jsonPath("$.problems[?(@.code == 'DUPLICATE')]", hasSize(2)))
                .andExpect(jsonPath("$.problems[?(@.code == 'CONFLICTING_REQUEST_ID')]", hasSize(2)))
                .andExpect(jsonPath("$.trace[*].stage").value(contains("validate", "deduplicate", "prioritize")))
                .andExpect(jsonPath("$.trace[*].in").value(contains(13, 10, 6)))
                .andExpect(jsonPath("$.trace[*].out").value(contains(10, 6, 6)));
    }

    @Test
    void writesTimesAsIsoInstantsAndPrioritiesAsNames() throws Exception {
        postBatch(sampleJson())
                .andExpect(jsonPath("$.accepted[0].requestedAt").value("2026-10-20T08:15:00Z"))
                .andExpect(jsonPath("$.accepted[5].priority").value("LOW"));
    }

    @Test
    void includesFieldsThatDoNotApplyAsNulls() throws Exception {
        // A stable shape lets the UI read every field without existence checks.
        postBatch(sampleJson())
                .andExpect(jsonPath("$.problems[0].relatedRequestId").value(nullValue()))
                .andExpect(jsonPath("$.problems[0].escalatedFrom").value(nullValue()));
    }

    @Test
    void sampleEndpointReturnsTheThirteenRawRequests() throws Exception {
        mockMvc.perform(get("/api/work-requests/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[0].requestId").value("REQ-101"))
                .andExpect(jsonPath("$[2].equipmentId").value(nullValue()))
                .andExpect(jsonPath("$[5].description").value("   "));
    }

    @Test
    void processesAnEmptyArray() throws Exception {
        postBatch("[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted", hasSize(0)))
                .andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.trace", hasSize(3)));
    }

    @Test
    void reportsUnknownPriorityAsInvalidValue() throws Exception {
        postBatch("""
                [{"requestId": "REQ-7", "equipmentId": "PUMP-7", "priority": "URGENT",
                  "requestedAt": "2026-10-20T08:15:00Z", "description": "Pump leaking"},
                 {"requestId": "REQ-8", "equipmentId": "FAN-1", "priority": "high",
                  "requestedAt": "2026-10-20T09:00:00Z", "description": "Belt squeal"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-8")))
                .andExpect(jsonPath("$.accepted[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.problems", hasSize(1)))
                .andExpect(jsonPath("$.problems[0].inputIndex").value(0))
                .andExpect(jsonPath("$.problems[0].requestId").value("REQ-7"))
                .andExpect(jsonPath("$.problems[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.problems[0].field").value("priority"))
                .andExpect(jsonPath("$.problems[0].message").value("REQ-7 has invalid priority 'URGENT'"));
    }

    @Test
    void reportsUnreadableValuesWithoutShiftingIndices() throws Exception {
        postBatch("""
                [42,
                 {"requestId": "REQ-2", "equipmentId": "PUMP-7", "priority": 3,
                  "requestedAt": "yesterday", "description": "Pump leaking", "site": "North"},
                 null,
                 {"requestId": "REQ-4", "equipmentId": "FAN-1", "priority": "LOW",
                  "requestedAt": "2026-10-20T09:00:00+05:30", "description": "Belt squeal"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-4")))
                .andExpect(jsonPath("$.accepted[0].requestedAt").value("2026-10-20T03:30:00Z"))
                .andExpect(jsonPath("$.problems[*].inputIndex").value(contains(0, 1, 1, 2)))
                .andExpect(jsonPath("$.problems[*].code")
                        .value(contains("NULL_REQUEST", "INVALID_VALUE", "INVALID_VALUE", "NULL_REQUEST")))
                .andExpect(jsonPath("$.problems[1].message").value("REQ-2 has invalid priority '3'"))
                .andExpect(jsonPath("$.problems[2].message").value("REQ-2 has invalid requestedAt 'yesterday'"));
    }

    @Test
    void exposesEscalationFieldsOnTheEscalatingDuplicateOnly() throws Exception {
        postBatch(sampleJson())
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].escalatedFrom").value(contains("MEDIUM")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].escalatedTo").value(contains("CRITICAL")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].relatedRequestId").value(contains("REQ-101")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-107')].escalatedTo").value(contains(nullValue())))
                .andExpect(jsonPath("$.problems[?(@.escalatedTo != null)]", hasSize(1)));
    }

    @Test
    void rejectsABatchOverTheDefaultLimitOf5000() throws Exception {
        String oversized = "[" + String.join(",", Collections.nCopies(5001, "{}")) + "]";

        postBatch(oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Batch has 5001 requests; the maximum is 5000"));
    }

    private ResultActions postBatch(String json) throws Exception {
        return mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String sampleJson() throws IOException {
        try (InputStream json = WorkRequestControllerTest.class.getResourceAsStream("/sample-batch.json")) {
            return new String(json.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
