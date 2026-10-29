package com.tanya.workrequests.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProblemTest {

    @Test
    void describesInvalidValueWithTheBadValue() {
        Problem problem = Problem.invalidValue(3, "REQ-7", "priority", "URGENT");

        assertThat(problem.code()).isEqualTo(ProblemCode.INVALID_VALUE);
        assertThat(problem.field()).isEqualTo("priority");
        assertThat(problem.message()).isEqualTo("REQ-7 has invalid priority 'URGENT'");
    }

    @Test
    void describesInvalidValueOfARequestWithoutId() {
        assertThat(Problem.invalidValue(0, null, "requestId", "42").message())
                .isEqualTo("Request has invalid requestId '42'");
    }

    @Test
    void truncatesLongInvalidValues() {
        String huge = "x".repeat(10_000);

        String message = Problem.invalidValue(0, "REQ-1", "priority", huge).message();

        assertThat(message).isEqualTo("REQ-1 has invalid priority '" + "x".repeat(Problem.MAX_VALUE_LENGTH) + "…'");
    }

    @Test
    void setsEscalationFieldsOnlyOnTheEscalatingDuplicate() {
        Problem escalating = Problem.duplicateWithEscalation(7, "REQ-108", "REQ-101", Priority.MEDIUM, Priority.CRITICAL);
        Problem plain = Problem.duplicate(6, "REQ-107", "REQ-101");

        assertThat(escalating.escalatedFrom()).isEqualTo(Priority.MEDIUM);
        assertThat(escalating.escalatedTo()).isEqualTo(Priority.CRITICAL);
        assertThat(plain.escalatedFrom()).isNull();
        assertThat(plain.escalatedTo()).isNull();
        assertThat(Problem.exactRepeat(1, "REQ-1").escalatedTo()).isNull();
    }

    @Test
    void rejectsHalfAnEscalation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Problem(
                0, "REQ-1", ProblemCode.DUPLICATE, null, "REQ-2", Priority.LOW, null, "Duplicate of REQ-2"));
    }
}
