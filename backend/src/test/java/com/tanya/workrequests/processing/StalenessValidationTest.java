package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Staleness is reported inside the validate stage, and only when a Clock is supplied.
 * "Now" is always the fixed clock, never the real date.
 */
class StalenessValidationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

    private final WorkRequestValidator validator =
            new WorkRequestValidator(FIXED_CLOCK, Duration.ofDays(30));

    @Test
    void validatorBuiltWithoutAClockNeverReportsStaleProblems() {
        StageResult result = new WorkRequestValidator()
                .validate(List.of(dated("R-1", "2001-01-01T00:00:00Z")));

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void validatorBuiltWithoutAClockKeepsAncientRequests() {
        WorkRequest ancient = dated("R-1", "2001-01-01T00:00:00Z");

        StageResult result = new WorkRequestValidator().validate(List.of(ancient));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, ancient));
    }

    @Test
    void validatorBuiltWithoutAClockTracesAncientRequestsAsAccepted() {
        StageResult result = new WorkRequestValidator()
                .validate(List.of(dated("R-1", "2001-01-01T00:00:00Z")));

        assertThat(result.trace()).isEqualTo(new StageTrace("validate", 1, 1, List.of()));
    }

    @Test
    void reportsExactlyOneStaleProblemForARequestOlderThanMaxAge() {
        StageResult result = validator.validate(List.of(dated("R-1", "2026-09-01T00:00:00Z")));

        assertThat(result.problems()).containsExactly(Problem.stale(0, "R-1", 30));
    }

    @Test
    void keepsAStaleRequestOutOfTheRequestsThatReachTheNextStage() {
        StageResult result = validator.validate(List.of(dated("R-1", "2026-09-01T00:00:00Z")));

        assertThat(result.requests()).isEmpty();
    }

    @Test
    void acceptsARequestWhoseAgeIsExactlyMaxAge() {
        WorkRequest exactlyThirtyDaysOld = dated("R-1", "2026-09-29T00:00:00Z");

        StageResult result = validator.validate(List.of(exactlyThirtyDaysOld));

        assertThat(result.problems()).isEmpty();
        assertThat(result.requests()).containsExactly(new IndexedRequest(0, exactlyThirtyDaysOld));
    }

    @Test
    void reportsStaleForARequestOneSecondOlderThanMaxAge() {
        WorkRequest justOverThirtyDaysOld = dated("R-1", "2026-09-28T23:59:59Z");

        StageResult result = validator.validate(List.of(justOverThirtyDaysOld));

        assertThat(result.problems()).containsExactly(Problem.stale(0, "R-1", 30));
        assertThat(result.requests()).isEmpty();
    }

    @Test
    void acceptsAFutureDatedRequest() {
        WorkRequest futureDated = dated("R-1", "2026-11-05T00:00:00Z");

        StageResult result = validator.validate(List.of(futureDated));

        assertThat(result.problems()).isEmpty();
        assertThat(result.requests()).containsExactly(new IndexedRequest(0, futureDated));
    }

    @Test
    void doesNotReportStaleForARequestWithoutARequestedAt() {
        WorkRequest withoutTimestamp =
                TestRequests.request("R-1", "EQ-1", Priority.MEDIUM, null, "Work for R-1");

        StageResult result = validator.validate(List.of(withoutTimestamp));

        assertThat(result.problems()).containsExactly(Problem.missingField(0, "R-1", "requestedAt"));
    }

    @Test
    void doesNotAddStaleToARequestThatAlreadyFailsAFieldCheck() {
        WorkRequest staleAndIncomplete = TestRequests.request("R-2", null, Priority.MEDIUM,
                "2026-07-21T00:00:00Z", "Work for R-2");

        StageResult result = validator.validate(List.of(staleAndIncomplete));

        assertThat(result.problems()).containsExactly(Problem.missingField(0, "R-2", "equipmentId"));
    }

    @Test
    void doesNotAddStaleToANullRequest() {
        List<WorkRequest> batchWithNull = new ArrayList<>();
        batchWithNull.add(null);

        StageResult result = validator.validate(batchWithNull);

        assertThat(result.problems()).containsExactly(Problem.nullRequest(0));
    }

    @Test
    void tracesStaleRequestsAsRejectedWithinTheValidateStage() {
        StageResult result = validator.validate(batchWithTwoStaleAndTwoFreshRequests());

        assertThat(result.trace()).isEqualTo(new StageTrace("validate", 4, 2,
                List.of("R-1 rejected: stale", "R-2 rejected: stale")));
    }

    @Test
    void keepsOnlyTheFreshRequestsOfAPartlyStaleBatch() {
        StageResult result = validator.validate(batchWithTwoStaleAndTwoFreshRequests());

        assertThat(result.requests().stream().map(IndexedRequest::request).map(WorkRequest::requestId))
                .containsExactly("R-3", "R-4");
    }

    @Test
    void reportsStaleProblemsInAscendingInputIndexOrder() {
        StageResult result = validator.validate(batchWithTwoStaleAndTwoFreshRequests());

        assertThat(result.problems())
                .containsExactly(Problem.stale(0, "R-1", 30), Problem.stale(1, "R-2", 30));
    }

    @Test
    void takesTheDayCountInTheMessageFromTheConfiguredMaxAge() {
        WorkRequestValidator sevenDayValidator =
                new WorkRequestValidator(FIXED_CLOCK, Duration.ofDays(7));

        List<Problem> problems = sevenDayValidator
                .validate(List.of(dated("R-1", "2026-10-19T00:00:00Z"))).problems();

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).message()).isEqualTo("R-1 is older than 7 days");
    }

    private static List<WorkRequest> batchWithTwoStaleAndTwoFreshRequests() {
        return List.of(dated("R-1", "2026-09-01T00:00:00Z"),
                dated("R-2", "2026-09-02T00:00:00Z"),
                dated("R-3", "2026-10-20T08:00:00Z"),
                dated("R-4", "2026-10-21T08:00:00Z"));
    }

    /** A complete, valid request whose only variable is when it was raised. */
    private static WorkRequest dated(String requestId, String requestedAt) {
        return TestRequests.request(requestId, "EQ-" + requestId, Priority.MEDIUM, requestedAt,
                "Work for " + requestId);
    }
}
