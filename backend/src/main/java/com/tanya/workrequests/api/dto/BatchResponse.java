package com.tanya.workrequests.api.dto;

import com.tanya.workrequests.domain.ProcessingResult;

import java.util.List;

/** Response of {@code POST /api/work-requests/batch}: the queue, the problems, and what each stage did. */
public record BatchResponse(List<WorkRequestDto> accepted, List<ProblemDto> problems, List<StageTraceDto> trace) {

    public static BatchResponse from(ProcessingResult result) {
        return new BatchResponse(
                result.accepted().stream().map(WorkRequestDto::from).toList(),
                result.problems().stream().map(ProblemDto::from).toList(),
                result.trace().stream().map(StageTraceDto::from).toList());
    }
}
