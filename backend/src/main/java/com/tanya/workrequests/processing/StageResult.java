package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;

import java.util.List;

/**
 * What every stage returns: the requests that pass to the next stage, the problems this
 * stage found, and its trace. One shape for all stages keeps the processor a simple pipeline.
 */
public record StageResult(List<IndexedRequest> requests, List<Problem> problems, StageTrace trace) {

    public StageResult {
        requests = List.copyOf(requests);
        problems = List.copyOf(problems);
    }
}
