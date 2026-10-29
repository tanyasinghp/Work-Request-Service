package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BatchObserverTest {

    private static final String SECRET_DESCRIPTION = "Pump leaking near the server room";

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final BatchObserver observer = new BatchObserver(registry);

    private final WorkRequest accepted = new WorkRequest("REQ-1", "PUMP-7", Priority.HIGH,
            Instant.parse("2026-10-20T08:00:00Z"), SECRET_DESCRIPTION);
    private final ProcessingResult result = new ProcessingResult(
            List.of(accepted),
            List.of(Problem.missingField(1, "REQ-2", "equipmentId"),
                    Problem.missingField(1, "REQ-2", "description"),
                    Problem.duplicate(2, "REQ-3", "REQ-1")),
            List.of(new StageTrace("validate", 3, 2, List.of())));

    @Test
    void returnsTheResultUnchanged() {
        assertThat(observer.observe(3, () -> result)).isSameAs(result);
    }

    @Test
    void countsEveryRequestReceived() {
        observer.observe(3, () -> result);
        observer.observe(5, () -> result);

        assertThat(registry.get("workrequests.processed").counter().count()).isEqualTo(8);
    }

    @Test
    void countsProblemsByCode() {
        observer.observe(3, () -> result);

        assertThat(rejected(ProblemCode.MISSING_FIELD)).isEqualTo(2);
        assertThat(rejected(ProblemCode.DUPLICATE)).isEqualTo(1);
        assertThat(rejected(ProblemCode.CONFLICTING_REQUEST_ID)).isZero();
    }

    @Test
    void registersARejectedCounterForEveryCodeUpFront() {
        assertThat(registry.get("workrequests.rejected").counters()).hasSize(ProblemCode.values().length);
    }

    @Test
    void timesEachBatch() {
        observer.observe(3, () -> {
            clock.add(Duration.ofMillis(42));
            return result;
        });

        assertThat(registry.get("workrequests.batch.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("workrequests.batch.duration").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(42);
    }

    @Test
    void logsOneSummaryLineWithCountsAndNoRequestContent(CapturedOutput output) {
        observer.observe(3, () -> {
            clock.add(Duration.ofMillis(7));
            return result;
        });

        assertThat(output.getOut().lines().filter(line -> line.contains("Processed batch")))
                .singleElement()
                .asString()
                .contains("INFO")
                .contains("size=3 accepted=1 problems={MISSING_FIELD=2, DUPLICATE=1} durationMs=7");
        assertThat(output.getOut()).doesNotContain(SECRET_DESCRIPTION).doesNotContain("PUMP-7");
    }

    private double rejected(ProblemCode code) {
        return registry.get("workrequests.rejected").tag("code", code.name()).counter().count();
    }
}
