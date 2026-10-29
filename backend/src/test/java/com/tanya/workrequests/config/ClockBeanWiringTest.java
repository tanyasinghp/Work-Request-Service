package com.tanya.workrequests.config;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.WorkRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Clock bean and the validator that must be built with it. */
class ClockBeanWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProcessingConfig.class);

    @Test
    void exposesAClockBeanNamedClock() {
        runner.run(context -> assertThat(context).hasBean("clock"));
    }

    @Test
    void theClockBeanIsTheSystemUtcClock() {
        runner.run(context ->
                assertThat(context.getBean("clock", Clock.class)).isEqualTo(Clock.systemUTC()));
    }

    @Test
    void buildsTheValidatorWithTheContextClockSoFutureRequestsAreFlagged() {
        runner.run(context -> {
            Clock clock = context.getBean(Clock.class);
            WorkRequest future = new WorkRequest("WR-1", "EQ-1", Priority.HIGH,
                    clock.instant().plus(Duration.ofHours(1)), "Pump leak");

            assertThat(context.getBean(WorkRequestValidator.class).validate(List.of(future)).problems())
                    .extracting(Problem::code)
                    .containsExactly(ProblemCode.SUSPICIOUS_TIMESTAMP);
        });
    }

    @Test
    void buildsTheValidatorWithTheContextClockSoPastRequestsAreAccepted() {
        runner.run(context -> {
            Clock clock = context.getBean(Clock.class);
            WorkRequest past = new WorkRequest("WR-1", "EQ-1", Priority.HIGH,
                    clock.instant().minus(Duration.ofHours(1)), "Pump leak");

            assertThat(context.getBean(WorkRequestValidator.class).validate(List.of(past)).problems())
                    .isEmpty();
        });
    }
}
