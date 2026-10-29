package com.tanya.workrequests.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Binding of app.staleness.* onto the StalenessProperties record. */
class StalenessPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StalenessPropertiesConfig.class);

    @Test
    void defaultsMaxAgeToThirtyDaysWhenNoPropertyIsSet() {
        runner.run(context -> assertThat(context.getBean(StalenessProperties.class).maxAge())
                .isEqualTo(Duration.ofDays(30)));
    }

    @Test
    void bindsMaxAgeFromAppStalenessMaxAge() {
        runner.withPropertyValues("app.staleness.max-age=7d")
                .run(context -> assertThat(context.getBean(StalenessProperties.class).maxAge())
                        .isEqualTo(Duration.ofDays(7)));
    }

    @Test
    void rejectsAZeroMaxAge() {
        runner.withPropertyValues("app.staleness.max-age=0s").run(this::expectNonPositiveRejection);
    }

    @Test
    void rejectsANegativeMaxAge() {
        runner.withPropertyValues("app.staleness.max-age=-1d").run(this::expectNonPositiveRejection);
    }

    private void expectNonPositiveRejection(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.staleness.max-age must be positive");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StalenessProperties.class)
    static class StalenessPropertiesConfig {
    }
}
