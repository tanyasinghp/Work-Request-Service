package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Rejects null, incomplete, and unreadable requests. Every missing or invalid field is its
 * own problem, so a request with two bad fields reports both. Blank strings count as missing.
 *
 * <p>When built with a {@link Clock} and a maximum age, a request whose {@code requestedAt} is
 * older than that age is also rejected as STALE. Staleness is only checked for requests whose
 * fields are all present and readable, so a STALE problem always carries a request id.
 */
public class WorkRequestValidator {

    static final String STAGE = "validate";

    // A threshold below one day would make "n days old" messages meaningless.
    private static final Duration MIN_MAX_AGE = Duration.ofDays(1);

    private final Clock clock;
    private final Duration maxAge;

    /** Validates fields only; no staleness check. Kept for callers that have no clock. */
    public WorkRequestValidator() {
        this.clock = null;
        this.maxAge = null;
    }

    public WorkRequestValidator(Clock clock, Duration maxAge) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.compareTo(MIN_MAX_AGE) < 0) {
            throw new IllegalArgumentException("maxAge must be at least 1 day");
        }
    }

    public StageResult validate(List<WorkRequest> batch) {
        return validate(batch, Map.of());
    }

    /**
     * @param invalidFieldsByIndex fields that were present in the raw input but unreadable;
     *        they are null in {@code batch} and are reported as INVALID_VALUE, not MISSING_FIELD
     */
    public StageResult validate(List<WorkRequest> batch, Map<Integer, List<InvalidField>> invalidFieldsByIndex) {
        List<IndexedRequest> valid = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        // Read once per call so every request in the batch is judged against the same "now".
        Instant staleCutoff = staleCutoff();

        for (int index = 0; index < batch.size(); index++) {
            WorkRequest request = batch.get(index);
            List<InvalidField> invalidFields = invalidFieldsByIndex.getOrDefault(index, List.of());
            List<Problem> requestProblems = problemsFor(index, request, invalidFields);
            if (!requestProblems.isEmpty()) {
                problems.addAll(requestProblems);
                notes.add(rejectionNote(request, requestProblems));
                continue;
            }
            if (isStale(request, staleCutoff)) {
                problems.add(Problem.stale(index, request.requestId(), request.requestedAt(), maxAge));
                notes.add(request.requestId() + " rejected: stale");
                continue;
            }
            valid.add(new IndexedRequest(index, request));
        }
        // Sorted, and free of input indices, so the trace is identical whatever the input order.
        notes.sort(Comparator.naturalOrder());

        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
        return new StageResult(valid, problems, trace);
    }

    private Instant staleCutoff() {
        return clock == null ? null : clock.instant().minus(maxAge);
    }

    /** Strictly older than the cutoff; a request exactly at the limit, or in the future, is fine. */
    private static boolean isStale(WorkRequest request, Instant staleCutoff) {
        return staleCutoff != null && request.requestedAt().isBefore(staleCutoff);
    }

    private static List<Problem> problemsFor(int index, WorkRequest request, List<InvalidField> invalidFields) {
        if (request == null) {
            return List.of(Problem.nullRequest(index));
        }
        Map<String, String> invalid = invalidFields.stream()
                .collect(Collectors.toMap(InvalidField::field, InvalidField::rawValue, (first, second) -> first));
        // A blank id is reported as no id at all, matching "blank counts as missing".
        String requestId = isBlank(request.requestId()) ? null : request.requestId();

        List<Problem> problems = new ArrayList<>();
        check(problems, index, requestId, "requestId", requestId == null, invalid);
        check(problems, index, requestId, "equipmentId", isBlank(request.equipmentId()), invalid);
        check(problems, index, requestId, "priority", request.priority() == null, invalid);
        check(problems, index, requestId, "requestedAt", request.requestedAt() == null, invalid);
        check(problems, index, requestId, "description", isBlank(request.description()), invalid);
        return problems;
    }

    private static void check(List<Problem> problems, int index, String requestId, String field,
                              boolean missing, Map<String, String> invalid) {
        if (invalid.containsKey(field)) {
            problems.add(Problem.invalidValue(index, requestId, field, invalid.get(field)));
        } else if (missing) {
            problems.add(Problem.missingField(index, requestId, field));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rejectionNote(WorkRequest request, List<Problem> problems) {
        if (request == null) {
            return "Null request rejected";
        }
        Problem first = problems.get(0);
        String label = first.requestId() != null ? first.requestId() : "Request without id";
        String reasons = problems.stream()
                .map(problem -> (problem.code() == ProblemCode.INVALID_VALUE ? "invalid " : "missing ") + problem.field())
                .collect(Collectors.joining(", "));
        return label + " rejected: " + reasons;
    }
}
