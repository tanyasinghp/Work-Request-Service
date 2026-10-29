package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Staleness checking in the validator: a request older than maxAge is reported as a problem
 * and kept out of the queue, while the no-argument validator keeps its old behaviour.
 */
class StalenessValidationTest {

    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);
    private static final String NINETY_DAYS_OLD = "2026-07-22T08:00:00Z";

    private final WorkRequestValidator validator = new WorkRequestValidator(FIXED_CLOCK, THIRTY_DAYS);

    @Test
    void keepsStaleRequestOutOfTheValidatedQueue() {
        StageResult result = validator.validate(staleAndRecentBatch());

        assertThat(result.requests())
                .extracting(indexed -> indexed.request().requestId())
                .containsExactly("R-2");
    }

    @Test
    void reportsOneStaleProblemForTheStaleRequestAtItsInputIndex() {
        StageResult result = validator.validate(staleAndRecentBatch());

        assertThat(result.problems())
                .extracting(Problem::inputIndex, Problem::requestId, Problem::code, Problem::field)
                .containsExactly(tuple(0, "R-1", ProblemCode.STALE, "requestedAt"));
    }

    @Test
    void acceptsRequestExactlyAtTheMaxAge() {
        StageResult result = validator.validate(boundaryBatch());

        assertThat(result.requests())
                .extracting(indexed -> indexed.request().requestId())
                .contains("R-EXACT");
    }

    @Test
    void flagsOnlyTheRequestOneSecondOlderThanTheMaxAge() {
        StageResult result = validator.validate(boundaryBatch());

        assertThat(result.problems())
                .extracting(Problem::requestId, Problem::code)
                .containsExactly(tuple("R-OLDER", ProblemCode.STALE));
    }

    @Test
    void acceptsRequestDatedInTheFuture() {
        StageResult result = validator.validate(boundaryBatch());

        assertThat(result.requests())
                .extracting(indexed -> indexed.request().requestId())
                .contains("R-FUTURE");
    }

    @Test
    void readsTheClockOncePerValidateCallSoEveryRequestSharesOneNow() {
        Clock clock = mock(Clock.class);
        given(clock.instant()).willReturn(NOW);

        new WorkRequestValidator(clock, THIRTY_DAYS).validate(boundaryBatch());

        verify(clock, times(1)).instant();
    }

    @Test
    void reportsOnlyNullRequestForANullElement() {
        StageResult result = validator.validate(Collections.singletonList(null));

        assertThat(result.problems())
                .extracting(Problem::code)
                .containsExactly(ProblemCode.NULL_REQUEST);
    }

    @Test
    void reportsOnlyTheMissingFieldForAnOldRequestThatIsAlsoIncomplete() {
        StageResult result = validator.validate(List.of(
                TestRequests.request("R-1", null, Priority.MEDIUM, NINETY_DAYS_OLD, "Old and incomplete")));

        assertThat(result.problems())
                .extracting(Problem::code, Problem::field)
                .containsExactly(tuple(ProblemCode.MISSING_FIELD, "equipmentId"));
    }

    @Test
    void reportsOnlyTheInvalidValueForAnOldRequestWithAnUnreadableRequestedAt() {
        StageResult result = validator.validate(
                List.of(TestRequests.request("R-1", "EQ-1", Priority.MEDIUM, NINETY_DAYS_OLD, "Old work")),
                Map.of(0, List.of(new InvalidField("requestedAt", "90-days-ago"))));

        assertThat(result.problems())
                .extracting(Problem::code, Problem::field)
                .containsExactly(tuple(ProblemCode.INVALID_VALUE, "requestedAt"));
    }

    @Test
    void givesEveryStaleProblemANonNullRequestId() {
        StageResult result = validator.validate(mixedRejectionBatch());

        assertThat(result.problems())
                .filteredOn(problem -> problem.code() == ProblemCode.STALE)
                .allSatisfy(problem -> assertThat(problem.requestId()).isNotNull());
    }

    @Test
    void tracesOneSortedNotePerRejectedRequestIncludingStaleOnes() {
        StageResult result = validator.validate(mixedRejectionBatch());

        assertThat(result.trace().notes())
                .containsExactly("R-3 rejected: missing description", "R-7 rejected: stale");
    }

    @Test
    void tracesValidateCountsWithStaleRequestsLeftOutOfTheOutput() {
        StageResult result = validator.validate(mixedRejectionBatch());

        assertThat(result.trace().stage()).isEqualTo("validate");
        assertThat(result.trace().in()).isEqualTo(3);
        assertThat(result.trace().out()).isEqualTo(1);
    }

    @Test
    void noArgumentValidatorAcceptsYearsOldRequests() {
        StageResult result = new WorkRequestValidator().validate(yearsOldBatch());

        assertThat(result.problems()).isEmpty();
        assertThat(result.requests()).hasSize(2);
    }

    @Test
    void processorBuiltFromTheExistingConstructorsAcceptsYearsOldRequests() {
        WorkRequestProcessor processor = new WorkRequestProcessor(new WorkRequestValidator(),
                new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter());

        ProcessingResult result = processor.process(yearsOldBatch());

        assertThat(result.problems()).isEmpty();
        assertThat(result.accepted()).hasSize(2);
    }

    @Test
    void rejectsANullClock() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WorkRequestValidator(null, THIRTY_DAYS))
                .withMessageContaining("clock");
    }

    @Test
    void rejectsANullMaxAge() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WorkRequestValidator(Clock.systemUTC(), null))
                .withMessageContaining("maxAge");
    }

    @Test
    void rejectsAMaxAgeShorterThanOneDay() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new WorkRequestValidator(FIXED_CLOCK, Duration.ofHours(23)));
    }

    private static List<WorkRequest> staleAndRecentBatch() {
        return List.of(
                TestRequests.request("R-1", "EQ-1", Priority.MEDIUM, "2026-09-01T08:00:00Z", "Old work"),
                TestRequests.request("R-2", "EQ-2", Priority.MEDIUM, "2026-10-19T08:00:00Z", "Recent work"));
    }

    /** Exactly at the limit, one second past it, and dated in the future. */
    private static List<WorkRequest> boundaryBatch() {
        return List.of(
                TestRequests.request("R-EXACT", "EQ-1", Priority.MEDIUM, "2026-09-20T08:00:00Z", "Thirty days old"),
                TestRequests.request("R-OLDER", "EQ-2", Priority.MEDIUM, "2026-09-20T07:59:59Z", "One second older"),
                TestRequests.request("R-FUTURE", "EQ-3", Priority.MEDIUM, "2026-11-01T08:00:00Z", "Dated ahead"));
    }

    private static List<WorkRequest> mixedRejectionBatch() {
        return List.of(
                TestRequests.valid("R-1"),
                TestRequests.request("R-3", "EQ-3", Priority.MEDIUM, "2026-10-19T08:00:00Z", " "),
                TestRequests.request("R-7", "EQ-7", Priority.HIGH, NINETY_DAYS_OLD, "Overdue inspection"));
    }

    private static List<WorkRequest> yearsOldBatch() {
        return List.of(
                TestRequests.request("R-1", "EQ-1", Priority.MEDIUM, "2019-01-01T08:00:00Z", "Ancient work"),
                TestRequests.request("R-2", "EQ-2", Priority.HIGH, "2020-06-30T08:00:00Z", "Older work"));
    }
}
