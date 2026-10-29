package com.tanya.workrequests.api.dto;

import com.tanya.workrequests.domain.WorkRequest;

import java.time.Instant;

/** An accepted request as returned by the API. Accepted requests are always complete. */
public record WorkRequestDto(String requestId,
                             String equipmentId,
                             String priority,
                             Instant requestedAt,
                             String description) {

    public static WorkRequestDto from(WorkRequest request) {
        return new WorkRequestDto(request.requestId(), request.equipmentId(), request.priority().name(),
                request.requestedAt(), request.description());
    }
}
