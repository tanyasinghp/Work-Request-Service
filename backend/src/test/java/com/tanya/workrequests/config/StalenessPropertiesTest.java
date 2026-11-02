package com.tanya.workrequests.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding of app.staleness.max-age, including its default and its one-day floor. */
class StalenessPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableStaleness.class);

    @Test
    void maxAgeDefaultsToThirtyDaysWhenThePropertyIsAbsent() {
        runner.run(context -> assertThat(context.getBean(StalenessProperties.class).maxAge())
                .isEqualTo(Duration.ofDays(30)));
    }

    @Test
    void bindsMaxAgeFromTheProperty() {
        runner.withPropertyValues("app.staleness.max-age=7d")
                .run(context -> assertThat(context.getBean(StalenessProperties.class).maxAge())
                        .isEqualTo(Duration.ofDays(7)));
    }

    @Test
    void failsStartupWhenMaxAgeIsZero() {
        runner.withPropertyValues("app.staleness.max-age=0d").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("app.staleness.max-age must be at least 1 day")
                    .rootCause().isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void failsStartupWhenMaxAgeIsUnderOneDay() {
        runner.withPropertyValues("app.staleness.max-age=23h").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("app.staleness.max-age must be at least 1 day");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StalenessProperties.class)
    static class EnableStaleness {
    }
}
