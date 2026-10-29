package com.tanya.workrequests.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityTest {

    @ParameterizedTest(name = "{0} has weight {1}")
    @CsvSource({
            "LOW, 1",
            "MEDIUM, 2",
            "HIGH, 3",
            "CRITICAL, 4"
    })
    void weightsMatchSpecification(Priority priority, int expectedWeight) {
        assertThat(priority.weight()).isEqualTo(expectedWeight);
    }
}
