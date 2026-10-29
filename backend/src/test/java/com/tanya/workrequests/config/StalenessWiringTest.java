package com.tanya.workrequests.config;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.DescriptionNormalizer;
import com.tanya.workrequests.processing.DuplicateResolver;
import com.tanya.workrequests.processing.StageResult;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import com.tanya.workrequests.processing.WorkRequestSorter;
import com.tanya.workrequests.processing.WorkRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deployed service flags staleness: a Clock bean plus the configured maxAge are wired into
 * the validator, while the no-argument constructor stays available for tests.
 */
@SpringBootTest(properties = "app.staleness.max-age=7d")
class StalenessWiringTest {

    /** Far enough in the past that the assertions hold whatever the real date is. */
    private static final WorkRequest LONG_PAST = new WorkRequest("R-1", "EQ-1", Priority.MEDIUM,
            Instant.parse("2001-01-01T00:00:00Z"), "Pump leak");

    @Autowired
    private Clock clock;

    @Autowired
    private WorkRequestValidator validator;

    @Autowired
    private WorkRequestProcessor processor;

    @Test
    void exposesASystemUtcClockBean() {
        assertThat(clock).isEqualTo(Clock.systemUTC());
    }

    @Test
    void buildsTheValidatorBeanWithTheConfiguredMaxAge() {
        StageResult result = validator.validate(List.of(LONG_PAST));

        assertThat(result.problems()).containsExactly(Problem.stale(0, "R-1", 7));
    }

    @Test
    void keepsStaleRequestsOutOfTheQueueOfTheWiredProcessor() {
        assertThat(processor.process(List.of(LONG_PAST)).accepted()).isEmpty();
    }

    @Test
    void stillOffersAStalenessFreeValidatorThroughTheNoArgumentConstructor() {
        StageResult result = new WorkRequestValidator().validate(List.of(LONG_PAST));

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void stillBuildsAWorkingPipelineFromTheExistingConstructors() {
        WorkRequestProcessor directlyBuilt = new WorkRequestProcessor(new WorkRequestValidator(),
                new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter());

        assertThat(directlyBuilt.process(List.of(LONG_PAST)).accepted()).containsExactly(LONG_PAST);
    }
}
