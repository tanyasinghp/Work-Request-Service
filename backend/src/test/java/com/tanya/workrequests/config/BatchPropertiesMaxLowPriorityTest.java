package com.tanya.workrequests.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** app.batch.max-low-priority: default, bounds, and the old single-argument constructor. */
class BatchPropertiesMaxLowPriorityTest {

    @Test
    void defaultsMaxLowPriorityToOneThousandWhenNotConfigured() {
        assertThat(bind(Map.of()).maxLowPriority()).isEqualTo(1000);
    }

    @Test
    void keepsTheMaxSizeDefaultOfFiveThousandWhenNotConfigured() {
        assertThat(bind(Map.of()).maxSize()).isEqualTo(5000);
    }

    @Test
    void bindsAConfiguredMaxLowPriority() {
        assertThat(bind(Map.of("app.batch.max-low-priority", "25")).maxLowPriority()).isEqualTo(25);
    }

    @Test
    void rejectsANegativeMaxLowPriorityWhenConstructed() {
        assertThatThrownBy(() -> new BatchProperties(5000, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-low-priority must be at least 0, was -1");
    }

    @Test
    void rejectsANegativeMaxLowPriorityWhenBound() {
        Throwable thrown = catchThrowable(() -> bind(Map.of("app.batch.max-low-priority", "-1")));

        assertThat(rootCauseOf(thrown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-low-priority must be at least 0, was -1");
    }

    @Test
    void acceptsAMaxLowPriorityOfZero() {
        assertThat(new BatchProperties(5000, 0).maxLowPriority()).isZero();
    }

    @Test
    void acceptsAPositiveMaxLowPriority() {
        assertThat(new BatchProperties(5000, 7).maxLowPriority()).isEqualTo(7);
    }

    @Test
    void singleArgumentConstructorStillDefaultsMaxLowPriorityToOneThousand() {
        assertThat(new BatchProperties(5000).maxLowPriority()).isEqualTo(1000);
    }

    @Test
    void singleArgumentConstructorKeepsTheMaxSizeItIsGiven() {
        assertThat(new BatchProperties(5000).maxSize()).isEqualTo(5000);
    }

    @Test
    void stillRejectsAMaxSizeBelowOne() {
        assertThatThrownBy(() -> new BatchProperties(0, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-size must be at least 1, was 0");
    }

    private static BatchProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("app.batch", BatchProperties.class);
    }

    /** The exception itself when it has no cause, so either way of failing is accepted. */
    private static Throwable rootCauseOf(Throwable thrown) {
        Throwable cause = thrown;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
