package com.tanya.workrequests.domain;

import java.time.Instant;

/**
 * A maintenance work request as received. Fields may be null or blank here on purpose:
 * the validator reports those as problems instead of the constructor throwing.
 */
public record WorkRequest(String requestId,
                          String equipmentId,
                          Priority priority,
                          Instant requestedAt,
                          String description) {

    public WorkRequest withPriority(Priority newPriority) {
        return new WorkRequest(requestId, equipmentId, newPriority, requestedAt, description);
    }
}
