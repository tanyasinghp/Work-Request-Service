package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orders the queue: highest priority first, then oldest request, then requestId. The final
 * requestId tiebreaker makes the order total, so output never depends on input order.
 *
 * <p>Optionally caps how many {@link Priority#LOW} requests one batch may accept. The cap is
 * applied after sorting, so the Low requests that survive are the earliest ones (ties broken by
 * requestId, exactly the order this stage already uses) and the rest become problems instead of
 * disappearing. Priorities are read as they stand after deduplication, so an escalated duplicate
 * is no longer Low and never counts against the cap.
 */
public class WorkRequestSorter {

    static final String STAGE = "prioritize";

    /** Sentinel for "no cap configured": no realistic batch can hold this many Low requests. */
    static final int NO_CAP = Integer.MAX_VALUE;

    static final String SORT_NOTE =
            "Sorted by priority (highest first), then requestedAt (earliest first), then requestId";

    static final Comparator<WorkRequest> ORDER = Comparator
            .comparingInt((WorkRequest request) -> request.priority().weight()).reversed()
            .thenComparing(WorkRequest::requestedAt)
            .thenComparing(WorkRequest::requestId);

    private final int maxLowPriority;

    /** No Low-priority cap; every request that reaches this stage is accepted. */
    public WorkRequestSorter() {
        this(NO_CAP);
    }

    public WorkRequestSorter(int maxLowPriority) {
        if (maxLowPriority < 0) {
            throw new IllegalArgumentException("maxLowPriority must be at least 0, was " + maxLowPriority);
        }
        this.maxLowPriority = maxLowPriority;
    }

    public StageResult sort(List<IndexedRequest> requests) {
        List<IndexedRequest> sorted = requests.stream()
                .sorted(Comparator.comparing(IndexedRequest::request, ORDER))
                .toList();

        List<IndexedRequest> accepted = new ArrayList<>(sorted.size());
        List<IndexedRequest> overCap = new ArrayList<>();
        int lowAccepted = 0;
        for (IndexedRequest indexed : sorted) {
            if (indexed.request().priority() == Priority.LOW) {
                if (lowAccepted >= maxLowPriority) {
                    overCap.add(indexed);
                    continue;
                }
                lowAccepted++;
            }
            accepted.add(indexed);
        }

        StageTrace trace = new StageTrace(STAGE, requests.size(), accepted.size(), notes(overCap));
        return new StageResult(List.copyOf(accepted), problems(overCap), trace);
    }

    /** Problems are listed by input index so the stage's report reads in batch order. */
    private List<Problem> problems(List<IndexedRequest> overCap) {
        return overCap.stream()
                .sorted(Comparator.comparingInt(IndexedRequest::inputIndex))
                .map(indexed -> Problem.overLowPriorityCap(indexed.inputIndex(),
                        indexed.request().requestId(), maxLowPriority))
                .toList();
    }

    private List<String> notes(List<IndexedRequest> overCap) {
        if (overCap.isEmpty()) {
            return List.of(SORT_NOTE);
        }
        // Sorted text, no input indices, so the notes don't depend on input order.
        return overCap.stream()
                .map(indexed -> indexed.request().requestId() + " dropped: over Low-priority cap of " + maxLowPriority)
                .sorted()
                .toList();
    }
}
