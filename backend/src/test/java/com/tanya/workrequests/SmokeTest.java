package com.tanya.workrequests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the real server on a random port and talks to it over HTTP, like a client would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmokeTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsAndReportsHealthUp() {
        ResponseEntity<JsonNode> health = http.getForEntity("/actuator/health", JsonNode.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void processesTheSampleAndCountsItsProblemsInMetrics() {
        double duplicatesBefore = rejected("DUPLICATE");
        JsonNode sample = http.getForObject("/api/work-requests/sample", JsonNode.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.postForEntity("/api/work-requests/batch",
                new HttpEntity<>(sample.toString(), headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accepted")).hasSize(6);
        assertThat(rejected("DUPLICATE")).isEqualTo(duplicatesBefore + 2);
        assertThat(http.getForObject("/actuator/metrics/workrequests.batch.duration", JsonNode.class)
                .at("/measurements/0/value").asDouble()).isPositive();
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() {
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/actuator/heapdump", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private double rejected(String code) {
        return http.getForObject("/actuator/metrics/workrequests.rejected?tag=code:" + code, JsonNode.class)
                .at("/measurements/0/value").asDouble();
    }
}
