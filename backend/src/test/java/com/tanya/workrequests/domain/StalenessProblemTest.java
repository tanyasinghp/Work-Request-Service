package com.tanya.workrequests.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the STALE code and the Problem.stale factory. */
class StalenessProblemTest {

    @Test
    void problemCodeContainsStaleAlongsideTheExistingCodes() {
        assertThat(Arrays.stream(ProblemCode.values()).map(Enum::name))
                .containsExactlyInAnyOrder("NULL_REQUEST", "MISSING_FIELD", "INVALID_VALUE",
                        "DUPLICATE", "CONFLICTING_REQUEST_ID", "STALE");
    }

    @Test
    void staleCarriesTheInputIndexItWasGiven() {
        assertThat(Problem.stale(3, "R-7", 30).inputIndex()).isEqualTo(3);
    }

    @Test
    void staleCarriesTheRequestIdItWasGiven() {
        assertThat(Problem.stale(3, "R-7", 30).requestId()).isEqualTo("R-7");
    }

    @Test
    void staleUsesTheStaleProblemCode() {
        assertThat(Problem.stale(3, "R-7", 30).code()).isEqualTo(ProblemCode.STALE);
    }

    @Test
    void staleMessageNamesTheRequestIdAndTheMaxAgeInDays() {
        assertThat(Problem.stale(3, "R-7", 30).message()).isEqualTo("R-7 is older than 30 days");
    }

    @Test
    void staleMessageUsesTheMaxAgeDayCountItWasGiven() {
        assertThat(Problem.stale(0, "R-9", 7).message()).isEqualTo("R-9 is older than 7 days");
    }

    @Test
    void staleLeavesTheFieldUnset() {
        assertThat(Problem.stale(1, "R-2", 30).field()).isNull();
    }

    @Test
    void staleLeavesTheRelatedRequestIdUnset() {
        assertThat(Problem.stale(1, "R-2", 30).relatedRequestId()).isNull();
    }

    @Test
    void staleLeavesBothEscalationFieldsUnset() {
        Problem problem = Problem.stale(1, "R-2", 30);

        assertThat(problem.escalatedFrom()).isNull();
        assertThat(problem.escalatedTo()).isNull();
    }
}
