package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnexpectedErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkRequestProcessor processor;

    @Test
    void returnsAGeneric500WithoutLeakingTheCause() throws Exception {
        when(processor.process(any(ParsedBatch.class)))
                .thenThrow(new IllegalStateException("secret internal detail at com.tanya.Something"));

        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
