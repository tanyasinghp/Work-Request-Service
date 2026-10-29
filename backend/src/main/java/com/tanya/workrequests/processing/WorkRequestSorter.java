package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.Comparator;
import java.util.List;

/**
 * Orders the queue: highest priority first, then oldest request, then requestId. The final
 * requestId tiebreaker makes the order total, so output never depends on input order.
 */
public class WorkRequestSorter {

    static final String STAGE = "prioritize";

    static final Comparator<WorkRequest> ORDER = Comparator
            .comparingInt((WorkRequest request) -> request.priority().weight()).reversed()
            .thenComparing(WorkRequest::requestedAt)
            .thenComparing(WorkRequest::requestId);

    public StageResult sort(List<IndexedRequest> requests) {
        List<IndexedRequest> sorted = requests.stream()
                .sorted(Comparator.comparing(IndexedRequest::request, ORDER))
                .toList();
        List<String> notes = List.of("Sorted by priority (highest first), then requestedAt (earliest first), then requestId");
        StageTrace trace = new StageTrace(STAGE, requests.size(), sorted.size(), notes);
        return new StageResult(sorted, List.of(), trace);
    }
}
