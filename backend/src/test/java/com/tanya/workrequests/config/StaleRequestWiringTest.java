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

/**
 * The wired pipeline reads "now" from the Clock bean and the threshold from app.staleness,
 * so replacing the clock in a test is enough to make a request stale.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class StaleRequestWiringTest {

    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");

    @Autowired
    private WorkRequestProcessor processor;

    @Autowired
    private StalenessProperties stalenessProperties;

    @Test
    void defaultsTheConfiguredMaxAgeToThirtyDays() {
        assertThat(stalenessProperties.maxAge()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void reportsAStaleProblemForARequestOlderThanThirtyDaysBeforeTheClock() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems())
                .extracting(Problem::requestId, Problem::code, Problem::field)
                .contains(tuple("R-OLD", ProblemCode.STALE, "requestedAt"));
    }

    @Test
    void leavesTheStaleRequestOutOfTheQueue() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.accepted())
                .extracting(WorkRequest::requestId)
                .containsExactly("R-NEW");
    }

    private static List<WorkRequest> batch() {
        return List.of(
                new WorkRequest("R-NEW", "EQ-1", Priority.MEDIUM, Instant.parse("2026-10-19T08:00:00Z"), "Recent work"),
                new WorkRequest("R-OLD", "EQ-2", Priority.HIGH, Instant.parse("2026-07-22T08:00:00Z"), "Old work"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
