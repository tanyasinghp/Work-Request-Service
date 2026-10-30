package com.tanya.workrequests.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The problem reported for a Low-priority request that does not fit in the batch's cap.
 */
class OverLowPriorityCapProblemTest {

    private static final Problem PROBLEM = Problem.overLowPriorityCap(4, "WR-9", 2);

    @Test
    void carriesTheInputIndexOfTheCappedRequest() {
        assertThat(PROBLEM.inputIndex()).isEqualTo(4);
    }

    @Test
    void carriesTheRequestIdOfTheCappedRequest() {
        assertThat(PROBLEM.requestId()).isEqualTo("WR-9");
    }

    @Test
    void usesTheOverLowPriorityCapCode() {
        assertThat(PROBLEM.code()).isEqualTo(ProblemCode.OVER_LOW_PRIORITY_CAP);
    }

    @Test
    void leavesFieldNullBecauseNoSingleFieldIsAtFault() {
        assertThat(PROBLEM.field()).isNull();
    }

    @Test
    void leavesRelatedRequestIdNullBecauseNoOtherRequestIsInvolved() {
        assertThat(PROBLEM.relatedRequestId()).isNull();
    }

    @Test
    void leavesTheEscalationFieldsNull() {
        assertThat(PROBLEM.escalatedFrom()).isNull();
        assertThat(PROBLEM.escalatedTo()).isNull();
    }

    @Test
    void messageNamesTheRequestAndTheCapAndNeverTheInputIndex() {
        assertThat(PROBLEM.message()).isEqualTo("WR-9 exceeds the per-batch Low-priority cap of 2");
    }

    @Test
    void messageReportsTheCapItWasGiven() {
        assertThat(Problem.overLowPriorityCap(0, "WR-1", 0).message())
                .isEqualTo("WR-1 exceeds the per-batch Low-priority cap of 0");
    }

    @Test
    void overLowPriorityCapIsAppendedLastSoExistingCodesKeepTheirOrder() {
        assertThat(ProblemCode.values()).containsExactly(
                ProblemCode.NULL_REQUEST,
                ProblemCode.MISSING_FIELD,
                ProblemCode.INVALID_VALUE,
                ProblemCode.DUPLICATE,
                ProblemCode.CONFLICTING_REQUEST_ID,
                ProblemCode.OVER_LOW_PRIORITY_CAP);
    }
}
