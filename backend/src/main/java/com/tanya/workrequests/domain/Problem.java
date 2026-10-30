package com.tanya.workrequests.domain;

import java.util.Objects;

/**
 * Something wrong with one request in a batch. Problems are returned as data so that one
 * bad request never fails the whole batch.
 *
 * <p>{@code requestId}, {@code field}, {@code relatedRequestId}, {@code escalatedFrom}, and
 * {@code escalatedTo} are null when they don't apply. Messages never mention the input index,
 * so the same problem reads the same no matter where the request sat in the batch.
 *
 * <p>{@code escalatedFrom}/{@code escalatedTo} are set only on the duplicate whose priority
 * raised the kept request, so the UI can show escalations without parsing message text.
 */
public record Problem(int inputIndex,
                      String requestId,
                      ProblemCode code,
                      String field,
                      String relatedRequestId,
                      Priority escalatedFrom,
                      Priority escalatedTo,
                      String message) {

    // Long enough to recognise a bad value, short enough that a huge payload can't bloat the response.
    static final int MAX_VALUE_LENGTH = 40;

    public Problem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if ((escalatedFrom == null) != (escalatedTo == null)) {
            throw new IllegalArgumentException("escalatedFrom and escalatedTo must be set together");
        }
    }

    public static Problem nullRequest(int inputIndex) {
        return new Problem(inputIndex, null, ProblemCode.NULL_REQUEST, null, null, null, null,
                "Request is null");
    }

    public static Problem missingField(int inputIndex, String requestId, String field) {
        return new Problem(inputIndex, requestId, ProblemCode.MISSING_FIELD, field, null, null, null,
                subject(requestId) + " is missing " + field);
    }

    public static Problem invalidValue(int inputIndex, String requestId, String field, String rawValue) {
        return new Problem(inputIndex, requestId, ProblemCode.INVALID_VALUE, field, null, null, null,
                subject(requestId) + " has invalid " + field + " '" + truncate(rawValue) + "'");
    }

    public static Problem duplicate(int inputIndex, String requestId, String keptRequestId) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, null, null,
                "Duplicate of " + keptRequestId);
    }

    /** A byte-for-byte resend of a request that was kept; it points at itself. */
    public static Problem exactRepeat(int inputIndex, String requestId) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, requestId, null, null,
                "Exact repeat of " + requestId);
    }

    public static Problem duplicateWithEscalation(int inputIndex, String requestId, String keptRequestId,
                                                  Priority from, Priority to) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, from, to,
                "Duplicate of " + keptRequestId + "; priority escalated " + from + " to " + to);
    }

    public static Problem conflictingRequestId(int inputIndex, String requestId) {
        return new Problem(inputIndex, requestId, ProblemCode.CONFLICTING_REQUEST_ID, null, null, null, null,
                "Request id " + requestId + " is used by requests with different content");
    }

    /**
     * A Low-priority request that did not fit the per-batch Low cap. Reported rather than
     * dropped silently, so a caller can see exactly which work was deferred.
     */
    public static Problem overLowPriorityCap(int inputIndex, String requestId, int cap) {
        return new Problem(inputIndex, requestId, ProblemCode.OVER_LOW_PRIORITY_CAP, null, null, null, null,
                subject(requestId) + " exceeds the per-batch Low-priority cap of " + cap);
    }

    private static String subject(String requestId) {
        return requestId == null ? "Request" : requestId;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
