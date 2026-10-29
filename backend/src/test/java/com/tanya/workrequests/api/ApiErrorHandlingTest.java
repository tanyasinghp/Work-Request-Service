package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A small limit keeps the oversized-batch cases cheap; the default 5000 is checked elsewhere. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.batch.max-size=3")
class ApiErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"[{\"requestId\": \"REQ-1\",", "not json", "[1, 2,]", "{\"a\" 1}"})
    void rejectsMalformedJson(String body) throws Exception {
        postBatch(body)
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body is missing or is not valid JSON"));
    }

    @Test
    void rejectsAMissingBody() throws Exception {
        postBatch("")
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body is missing or is not valid JSON"));
    }

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"{\"requestId\": \"REQ-1\"}", "\"REQ-1\"", "42", "true", "null"})
    void rejectsABodyThatIsNotAnArray(String body) throws Exception {
        postBatch(body)
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body must be a JSON array of work requests"));
    }

    @Test
    void rejectsABatchOverTheLimit() throws Exception {
        postBatch("[{}, {}, {}, {}]")
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Batch has 4 requests; the maximum is 3"));
    }

    @Test
    void acceptsABatchExactlyAtTheLimit() throws Exception {
        postBatch("[{}, {}, {}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace[0].in").value(3));
    }

    @Test
    void keepsTheStatusOfFrameworkErrors() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.TEXT_PLAIN).content("[]"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
        mockMvc.perform(get("/api/work-requests/batch"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(get("/api/work-requests/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$", aMapWithSize(3)));
    }

    private ResultActions postBatch(String body) throws Exception {
        return mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Exactly {status, error, message}, with nothing that looks like a stack trace or parser detail. */
    private static ResultMatcher errorBody(int status, String error, String message) {
        return result -> {
            jsonPath("$", aMapWithSize(3)).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.error").value(error).match(result);
            jsonPath("$.message").value(message).match(result);
            content().string(not(containsString("Exception"))).match(result);
            content().string(not(containsString("com.fasterxml"))).match(result);
        };
    }
}
