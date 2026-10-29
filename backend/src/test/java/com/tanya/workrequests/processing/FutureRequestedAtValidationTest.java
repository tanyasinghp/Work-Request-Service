package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Validation of requestedAt against the validator's clock. Everything uses a fixed clock except
 * the few tests that must pin the no-arg constructor to the system clock.
 */
class FutureRequestedAtValidationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private final WorkRequestValidator validator = new WorkRequestValidator(FIXED);

    @Test
    void reportsOneSuspiciousTimestampProblemForAFutureDatedRequest() {
        WorkRequest future = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(future));

        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.code()).isEqualTo(ProblemCode.SUSPICIOUS_TIMESTAMP);
            assertThat(problem.field()).isEqualTo("requestedAt");
            assertThat(problem.inputIndex()).isZero();
            assertThat(problem.requestId()).isEqualTo("WR-1");
        });
    }

    @Test
    void dropsTheFutureDatedRequestFromTheStageResultRequests() {
        WorkRequest future = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(future));

        assertThat(result.requests()).isEmpty();
    }

    @Test
    void countsOnlyAcceptedRequestsInTheValidateTrace() {
        List<WorkRequest> batch = List.of(
                TestRequests.request("WR-1", "EQ-1", Priority.HIGH, "2026-10-21T08:30:00Z", "Pump leak"),
                TestRequests.valid("WR-2"));

        StageResult result = validator.validate(batch);

        assertThat(result.trace().in()).isEqualTo(2);
        assertThat(result.trace().out()).isEqualTo(1);
    }

    @Test
    void acceptsARequestDatedExactlyAtTheClockInstant() {
        WorkRequest onTheDot = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-20T12:00:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(onTheDot));

        assertThat(result.problems()).isEmpty();
        assertThat(result.requests()).extracting(IndexedRequest::request).containsExactly(onTheDot);
    }

    @Test
    void acceptsARequestDatedOneMillisecondBeforeTheClockInstant() {
        WorkRequest justBefore = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-20T11:59:59.999Z", "Pump leak");

        StageResult result = validator.validate(List.of(justBefore));

        assertThat(result.problems()).isEmpty();
        assertThat(result.requests()).extracting(IndexedRequest::request).containsExactly(justBefore);
    }

    @Test
    void flagsARequestDatedOneMillisecondAfterTheClockInstantSoThereIsNoToleranceWindow() {
        WorkRequest justAfter = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-20T12:00:00.001Z", "Pump leak");

        StageResult result = validator.validate(List.of(justAfter));

        assertThat(result.problems()).extracting(Problem::code)
                .containsExactly(ProblemCode.SUSPICIOUS_TIMESTAMP);
    }

    @Test
    void reportsOnlyMissingFieldWhenRequestedAtIsNull() {
        WorkRequest noTimestamp = TestRequests.request("WR-1", "EQ-1", Priority.HIGH, null, "Pump leak");

        StageResult result = validator.validate(List.of(noTimestamp));

        assertThat(result.problems()).extracting(Problem::code, Problem::field)
                .containsExactly(tuple(ProblemCode.MISSING_FIELD, "requestedAt"));
    }

    @Test
    void reportsOnlyInvalidValueWhenRequestedAtCouldNotBeParsed() {
        WorkRequest unreadable = TestRequests.request("WR-1", "EQ-1", Priority.HIGH, null, "Pump leak");
        Map<Integer, List<InvalidField>> invalid =
                Map.of(0, List.of(new InvalidField("requestedAt", "yesterday")));

        StageResult result = validator.validate(List.of(unreadable), invalid);

        assertThat(result.problems()).extracting(Problem::code, Problem::field)
                .containsExactly(tuple(ProblemCode.INVALID_VALUE, "requestedAt"));
    }

    @Test
    void reportsExistingFieldProblemsBeforeTheSuspiciousTimestampProblem() {
        WorkRequest incompleteAndFuture = TestRequests.request("WR-1", null, Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(incompleteAndFuture));

        assertThat(result.problems()).extracting(Problem::code, Problem::field).containsExactly(
                tuple(ProblemCode.MISSING_FIELD, "equipmentId"),
                tuple(ProblemCode.SUSPICIOUS_TIMESTAMP, "requestedAt"));
    }

    @Test
    void reportsBothProblemsForTheSameInputIndex() {
        List<WorkRequest> batch = List.of(
                TestRequests.valid("WR-0"),
                TestRequests.request("WR-1", null, Priority.HIGH, "2026-10-21T08:30:00Z", "Pump leak"));

        StageResult result = validator.validate(batch);

        assertThat(result.problems()).extracting(Problem::inputIndex).containsExactly(1, 1);
    }

    @Test
    void writesAnIndexFreeNoteListingMissingAndFutureReasons() {
        WorkRequest incompleteAndFuture = TestRequests.request("WR-1", null, Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(incompleteAndFuture));

        assertThat(result.trace().notes())
                .containsExactly("WR-1 rejected: missing equipmentId, future requestedAt");
    }

    @Test
    void writesAFutureRequestedAtNoteForAnOtherwiseValidRequest() {
        WorkRequest future = TestRequests.request("WR-1", "EQ-1", Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(future));

        assertThat(result.trace().notes()).containsExactly("WR-1 rejected: future requestedAt");
    }

    @Test
    void labelsABlankIdRequestAsRequestWithoutIdInTheNote() {
        WorkRequest blankId = TestRequests.request("   ", "EQ-1", Priority.HIGH,
                "2026-10-21T08:30:00Z", "Pump leak");

        StageResult result = validator.validate(List.of(blankId));

        assertThat(result.trace().notes())
                .containsExactly("Request without id rejected: missing requestId, future requestedAt");
    }

    @Test
    void keepsNotesSortedWhenAFutureRequestIsRejected() {
        List<WorkRequest> batch = List.of(
                TestRequests.request("WR-9", "EQ-9", Priority.HIGH, "2026-10-21T08:30:00Z", "Pump leak"),
                TestRequests.request("WR-1", null, Priority.HIGH, TestRequests.DEFAULT_TIME, "Seal swap"));

        StageResult result = validator.validate(batch);

        assertThat(result.trace().notes()).containsExactly(
                "WR-1 rejected: missing equipmentId",
                "WR-9 rejected: future requestedAt");
    }

    @Test
    void noArgConstructorFlagsARequestDatedAfterTheRealNow() {
        WorkRequest future = new WorkRequest("WR-1", "EQ-1", Priority.HIGH,
                Instant.now().plus(Duration.ofHours(1)), "Pump leak");

        StageResult result = new WorkRequestValidator().validate(List.of(future));

        assertThat(result.problems()).extracting(Problem::code)
                .containsExactly(ProblemCode.SUSPICIOUS_TIMESTAMP);
    }

    @Test
    void noArgConstructorAcceptsARequestDatedBeforeTheRealNow() {
        WorkRequest past = new WorkRequest("WR-1", "EQ-1", Priority.HIGH,
                Instant.now().minus(Duration.ofHours(1)), "Pump leak");

        StageResult result = new WorkRequestValidator().validate(List.of(past));

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void acceptsSampleBatchDatesUnderTheSystemClockBecauseTheyAreInThePast() {
        WorkRequest sampleDated = TestRequests.valid("WR-1");

        StageResult result = new WorkRequestValidator().validate(List.of(sampleDated));

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void rejectsANullClock() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WorkRequestValidator((Clock) null))
                .withMessage("clock");
    }

    @Test
    void readsTheClockOncePerValidateCall() {
        CountingClock clock = new CountingClock(NOW);

        new WorkRequestValidator(clock).validate(List.of(
                TestRequests.valid("WR-1"), TestRequests.valid("WR-2"), TestRequests.valid("WR-3")));

        assertThat(clock.reads()).isEqualTo(1);
    }

    @Test
    void comparesEveryRequestInABatchAgainstTheSameInstant() {
        // The clock jumps an hour per read, so a second read would make the second request look past.
        CountingClock clock = new CountingClock(NOW, Duration.ofHours(1));
        List<WorkRequest> batch = List.of(
                TestRequests.request("WR-1", "EQ-1", Priority.HIGH, "2026-10-20T12:30:00Z", "Pump leak"),
                TestRequests.request("WR-2", "EQ-2", Priority.HIGH, "2026-10-20T12:30:00Z", "Valve check"));

        StageResult result = new WorkRequestValidator(clock).validate(batch);

        assertThat(result.problems()).extracting(Problem::requestId).containsExactly("WR-1", "WR-2");
    }

    /** Counts reads so a test can pin "one instant per validate call". */
    private static final class CountingClock extends Clock {

        private final Instant start;
        private final Duration step;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingClock(Instant start) {
            this(start, Duration.ZERO);
        }

        private CountingClock(Instant start, Duration step) {
            this.start = start;
            this.step = step;
        }

        int reads() {
            return reads.get();
        }

        @Override
        public Instant instant() {
            return start.plus(step.multipliedBy(reads.getAndIncrement()));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
