package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.WorkRequest;

import java.time.Instant;

/** Builders that keep test inputs short and readable. */
final class TestRequests {

    static final String DEFAULT_TIME = "2026-10-20T08:00:00Z";

    private TestRequests() {
    }

    static WorkRequest request(String requestId, String equipmentId, Priority priority,
                               String requestedAt, String description) {
        Instant time = requestedAt == null ? null : Instant.parse(requestedAt);
        return new WorkRequest(requestId, equipmentId, priority, time, description);
    }

    /** A complete, valid request whose content is unique to its id. */
    static WorkRequest valid(String requestId) {
        return request(requestId, "EQ-" + requestId, Priority.MEDIUM, DEFAULT_TIME, "Work for " + requestId);
    }
}
