package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.time.Clock;
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
 * <p>Also rejects requests dated in the future. The clock is read once per batch so every
 * request in one call is judged against the same instant.
 */
public class WorkRequestValidator {

    static final String STAGE = "validate";

    private final Clock clock;

    public WorkRequestValidator() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock source of "now" for the future-timestamp check; an instant equal to now is
     *        accepted, only a strictly later one is future
     */
    public WorkRequestValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
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
        // One instant for the whole batch: two identical requests can't disagree about "now".
        Instant now = clock.instant();

        for (int index = 0; index < batch.size(); index++) {
            WorkRequest request = batch.get(index);
            List<InvalidField> invalidFields = invalidFieldsByIndex.getOrDefault(index, List.of());
            List<Problem> requestProblems = problemsFor(index, request, invalidFields, now);
            if (requestProblems.isEmpty()) {
                valid.add(new IndexedRequest(index, request));
            } else {
                problems.addAll(requestProblems);
                notes.add(rejectionNote(request, requestProblems));
            }
        }
        // Sorted, and free of input indices, so the trace is identical whatever the input order.
        notes.sort(Comparator.naturalOrder());

        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
        return new StageResult(valid, problems, trace);
    }

    private static List<Problem> problemsFor(int index, WorkRequest request, List<InvalidField> invalidFields,
                                             Instant now) {
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
        // Last, after the field-order problems; needs a parsed instant, so unreadable dates are skipped.
        if (request.requestedAt() != null && !invalid.containsKey("requestedAt")
                && request.requestedAt().isAfter(now)) {
            problems.add(Problem.suspiciousTimestamp(index, requestId, request.requestedAt()));
        }
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
                .map(problem -> reasonPrefix(problem.code()) + problem.field())
                .collect(Collectors.joining(", "));
        return label + " rejected: " + reasons;
    }

    private static String reasonPrefix(ProblemCode code) {
        return switch (code) {
            case INVALID_VALUE -> "invalid ";
            case SUSPICIOUS_TIMESTAMP -> "future ";
            default -> "missing ";
        };
    }
}
