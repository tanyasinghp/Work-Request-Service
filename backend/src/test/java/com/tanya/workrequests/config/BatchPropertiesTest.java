package com.tanya.workrequests.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BatchPropertiesTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsALimitThatWouldBlockEveryBatch(int maxSize) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BatchProperties(maxSize))
                .withMessageContaining("app.batch.max-size");
    }
}
