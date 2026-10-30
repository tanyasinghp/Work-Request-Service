package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.DescriptionNormalizer;
import com.tanya.workrequests.processing.DuplicateResolver;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import com.tanya.workrequests.processing.WorkRequestSorter;
import com.tanya.workrequests.processing.WorkRequestValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The new ProblemCode must still get exactly one counter, like every other code. */
class BatchObserverOverLowPriorityCapTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BatchObserver observer = new BatchObserver(registry);

    @Test
    void registersOneRejectedCounterPerProblemCode() {
        assertThat(registry.find("workrequests.rejected").counters())
                .extracting(counter -> counter.getId().getTag("code"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(ProblemCode.values()).map(Enum::name).toList());
    }

    @Test
    void reportsZeroCapProblemsBeforeAnyBatchIsObserved() {
        assertThat(countFor(ProblemCode.OVER_LOW_PRIORITY_CAP)).isZero();
    }

    @Test
    void countsAnOverLowPriorityCapProblem() {
        ProcessingResult result = cappedBatchResult();

        observer.observe(1, () -> result);

        assertThat(countFor(ProblemCode.OVER_LOW_PRIORITY_CAP)).isEqualTo(1.0);
    }

    private double countFor(ProblemCode code) {
        return registry.get("workrequests.rejected").tag("code", code.name()).counter().count();
    }

    private static ProcessingResult cappedBatchResult() {
        WorkRequestProcessor processor = new WorkRequestProcessor(new WorkRequestValidator(),
                new DuplicateResolver(new DescriptionNormalizer()),
                new WorkRequestSorter(0));
        return processor.process(List.of(new WorkRequest("L-1", "EQ-1", Priority.LOW,
                Instant.parse("2026-10-20T08:00:00Z"), "Low work")));
    }
}
