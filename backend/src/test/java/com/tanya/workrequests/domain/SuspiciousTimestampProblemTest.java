package com.tanya.workrequests.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The new problem code and the factory that builds its problem. */
class SuspiciousTimestampProblemTest {

    private static final Instant FUTURE = Instant.parse("2026-10-21T08:30:00Z");

    @Test
    void declaresSuspiciousTimestampLastAfterConflictingRequestId() {
        assertThat(ProblemCode.values()).containsExactly(
                ProblemCode.NULL_REQUEST,
                ProblemCode.MISSING_FIELD,
                ProblemCode.INVALID_VALUE,
                ProblemCode.DUPLICATE,
                ProblemCode.CONFLICTING_REQUEST_ID,
                ProblemCode.SUSPICIOUS_TIMESTAMP);
    }

    @Test
    void buildsASuspiciousTimestampProblemForTheRequestedAtField() {
        Problem problem = Problem.suspiciousTimestamp(3, "WR-1", FUTURE);

        assertThat(problem.code()).isEqualTo(ProblemCode.SUSPICIOUS_TIMESTAMP);
        assertThat(problem.field()).isEqualTo("requestedAt");
    }

    @Test
    void keepsTheInputIndexAndRequestIdItWasGiven() {
        Problem problem = Problem.suspiciousTimestamp(3, "WR-1", FUTURE);

        assertThat(problem.inputIndex()).isEqualTo(3);
        assertThat(problem.requestId()).isEqualTo("WR-1");
    }

    @Test
    void leavesRelatedRequestIdAndEscalationFieldsNull() {
        Problem problem = Problem.suspiciousTimestamp(0, "WR-1", FUTURE);

        assertThat(problem.relatedRequestId()).isNull();
        assertThat(problem.escalatedFrom()).isNull();
        assertThat(problem.escalatedTo()).isNull();
    }

    @Test
    void namesTheRequestAndTheInstantInTheMessage() {
        Problem problem = Problem.suspiciousTimestamp(0, "WR-1", FUTURE);

        assertThat(problem.message()).isEqualTo("WR-1 has requestedAt 2026-10-21T08:30:00Z in the future");
    }

    @Test
    void fallsBackToAGenericSubjectWhenTheRequestIdIsNull() {
        Problem problem = Problem.suspiciousTimestamp(0, null, FUTURE);

        assertThat(problem.message()).isEqualTo("Request has requestedAt 2026-10-21T08:30:00Z in the future");
    }

    @Test
    void rendersTheInstantWithInstantToStringSoSubSecondPrecisionIsKept() {
        Problem problem = Problem.suspiciousTimestamp(0, "WR-2", Instant.parse("2026-10-21T08:30:00.500Z"));

        assertThat(problem.message()).isEqualTo("WR-2 has requestedAt 2026-10-21T08:30:00.500Z in the future");
    }
}
