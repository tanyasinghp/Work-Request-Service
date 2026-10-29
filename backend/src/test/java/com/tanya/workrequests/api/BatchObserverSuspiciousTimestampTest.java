package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.DescriptionNormalizer;
import com.tanya.workrequests.processing.DuplicateResolver;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import com.tanya.workrequests.processing.WorkRequestSorter;
import com.tanya.workrequests.processing.WorkRequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Metrics keep one rejection counter per problem code, including the new one. */
class BatchObserverSuspiciousTimestampTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BatchObserver observer = new BatchObserver(registry);
    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(FIXED),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    @Test
    void registersOneRejectionCounterPerProblemCode() {
        List<String> codes = registry.find("workrequests.rejected").counters().stream()
                .map(counter -> counter.getId().getTag("code"))
                .toList();

        assertThat(codes).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(ProblemCode.values()).map(ProblemCode::name).toList());
    }

    @Test
    void countsAFutureDatedRequestUnderTheSuspiciousTimestampCode() {
        List<WorkRequest> batch = List.of(new WorkRequest("WR-1", "EQ-1", Priority.HIGH,
                Instant.parse("2026-10-21T08:30:00Z"), "Pump leak"));

        observer.observe(batch.size(), () -> processor.process(batch));

        Counter counter = registry.get("workrequests.rejected").tag("code", "SUSPICIOUS_TIMESTAMP").counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
