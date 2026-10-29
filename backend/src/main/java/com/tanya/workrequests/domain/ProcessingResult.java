package com.tanya.workrequests.domain;

import java.util.List;

/**
 * Outcome of processing a batch. Lists are copied so callers can't mutate shared state.
 */
public record ProcessingResult(List<WorkRequest> accepted,
                               List<Problem> problems,
                               List<StageTrace> trace) {

    public ProcessingResult {
        accepted = List.copyOf(accepted);
        problems = List.copyOf(problems);
        trace = List.copyOf(trace);
    }
}
