package com.tanya.workrequests.api.dto;

import com.tanya.workrequests.domain.StageTrace;

import java.util.List;

public record StageTraceDto(String stage, int in, int out, List<String> notes) {

    public static StageTraceDto from(StageTrace trace) {
        return new StageTraceDto(trace.stage(), trace.in(), trace.out(), trace.notes());
    }
}
