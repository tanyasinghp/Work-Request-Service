package com.tanya.workrequests.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The STALE problem code and the Problem.stale factory. */
class StaleProblemTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-01T08:00:00Z");

    @Test
    void listsStaleAsSixthAndLastProblemCode() {
        assertThat(ProblemCode.values()).containsExactly(
                ProblemCode.NULL_REQUEST,
                ProblemCode.MISSING_FIELD,
                ProblemCode.INVALID_VALUE,
                ProblemCode.DUPLICATE,
                ProblemCode.CONFLICTING_REQUEST_ID,
                ProblemCode.STALE);
    }

    @Test
    void staleFactoryKeepsInputIndexAndRequestId() {
        Problem problem = Problem.stale(3, "R-7", REQUESTED_AT, Duration.ofDays(30));

        assertThat(problem.inputIndex()).isEqualTo(3);
        assertThat(problem.requestId()).isEqualTo("R-7");
    }

    @Test
    void staleFactoryUsesStaleCodeAndRequestedAtField() {
        Problem problem = Problem.stale(3, "R-7", REQUESTED_AT, Duration.ofDays(30));

        assertThat(problem.code()).isEqualTo(ProblemCode.STALE);
        assertThat(problem.field()).isEqualTo("requestedAt");
    }

    @Test
    void staleFactoryLeavesRelationAndEscalationFieldsUnset() {
        Problem problem = Problem.stale(3, "R-7", REQUESTED_AT, Duration.ofDays(30));

        assertThat(problem.relatedRequestId()).isNull();
        assertThat(problem.escalatedFrom()).isNull();
        assertThat(problem.escalatedTo()).isNull();
    }

    @Test
    void staleMessageNamesRequestIdTimestampAndMaxAgeInDays() {
        Problem problem = Problem.stale(3, "R-7", REQUESTED_AT, Duration.ofDays(30));

        assertThat(problem.message())
                .isEqualTo("R-7 is stale: requestedAt 2026-09-01T08:00:00Z is more than 30 days old");
    }

    @Test
    void staleMessageTakesItsDayCountFromTheGivenMaxAge() {
        Problem problem = Problem.stale(0, "R-9", REQUESTED_AT, Duration.ofDays(7));

        assertThat(problem.message())
                .isEqualTo("R-9 is stale: requestedAt 2026-09-01T08:00:00Z is more than 7 days old");
    }

    @Test
    void staleMessageOmitsTheInputIndex() {
        Problem problem = Problem.stale(42, "R-7", REQUESTED_AT, Duration.ofDays(30));

        assertThat(problem.message()).doesNotContain("42");
    }
}
