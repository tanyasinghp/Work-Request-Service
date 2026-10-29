package com.tanya.workrequests.api.dto;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;

/** One problem as returned by the API; fields that don't apply are null. */
public record ProblemDto(int inputIndex,
                         String requestId,
                         String code,
                         String field,
                         String relatedRequestId,
                         String escalatedFrom,
                         String escalatedTo,
                         String message) {

    public static ProblemDto from(Problem problem) {
        return new ProblemDto(problem.inputIndex(), problem.requestId(), problem.code().name(), problem.field(),
                problem.relatedRequestId(), nameOf(problem.escalatedFrom()), nameOf(problem.escalatedTo()),
                problem.message());
    }

    private static String nameOf(Priority priority) {
        return priority == null ? null : priority.name();
    }
}
