package com.tanya.workrequests.config;

import com.tanya.workrequests.processing.WorkRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** The Clock bean ProcessingConfig exposes so tests can replace "now". */
class ClockBeanTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableStaleness.class, ProcessingConfig.class);

    @Test
    void exposesExactlyOneClockBeanNamedClock() {
        runner.run(context -> assertThat(context.getBeanNamesForType(Clock.class)).containsExactly("clock"));
    }

    @Test
    void clockBeanDefaultsToTheSystemUtcClock() {
        runner.run(context -> assertThat(context.getBean(Clock.class)).isEqualTo(Clock.systemUTC()));
    }

    @Test
    void buildsASingleWorkRequestValidatorBean() {
        runner.run(context -> assertThat(context).hasSingleBean(WorkRequestValidator.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StalenessProperties.class)
    static class EnableStaleness {
    }
}
