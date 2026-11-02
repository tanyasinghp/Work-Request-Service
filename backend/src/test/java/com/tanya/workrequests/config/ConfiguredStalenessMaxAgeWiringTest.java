package com.tanya.workrequests.config;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The configured threshold, not a hard-coded 30 days, decides what counts as stale. */
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true",
        "app.staleness.max-age=7d"})
class ConfiguredStalenessMaxAgeWiringTest {

    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");

    @Autowired
    private WorkRequestProcessor processor;

    @Test
    void flagsARequestOlderThanTheConfiguredSevenDays() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems())
                .extracting(Problem::requestId, Problem::code)
                .containsExactly(tuple("R-EIGHT-DAYS", ProblemCode.STALE));
    }

    @Test
    void acceptsARequestYoungerThanTheConfiguredSevenDays() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.accepted())
                .extracting(WorkRequest::requestId)
                .containsExactly("R-SIX-DAYS");
    }

    private static List<WorkRequest> batch() {
        return List.of(
                new WorkRequest("R-SIX-DAYS", "EQ-1", Priority.MEDIUM, NOW.minus(Duration.ofDays(6)), "Recent work"),
                new WorkRequest("R-EIGHT-DAYS", "EQ-2", Priority.HIGH, NOW.minus(Duration.ofDays(8)), "Old work"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
