package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Removes request-id conflicts, then merges duplicates. Runs after validation, so every
 * request here is complete and an invalid request can never knock out a valid one.
 */
public class DuplicateResolver {

    static final String STAGE = "deduplicate";

    private static final Comparator<IndexedRequest> SURVIVOR_ORDER = Comparator
            .comparing((IndexedRequest indexed) -> indexed.request().requestedAt())
            .thenComparing(indexed -> indexed.request().requestId());

    private static final Comparator<IndexedRequest> BY_REQUEST_ID =
            Comparator.comparing(indexed -> indexed.request().requestId());

    private final DescriptionNormalizer normalizer;

    public DuplicateResolver(DescriptionNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    public StageResult resolve(List<IndexedRequest> requests) {
        List<Problem> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        List<IndexedRequest> withoutConflicts = removeConflicts(requests, problems, notes);
        List<IndexedRequest> kept = mergeDuplicates(withoutConflicts, problems, notes);

        kept.sort(Comparator.comparingInt(IndexedRequest::inputIndex));
        problems.sort(Comparator.comparingInt(Problem::inputIndex));
        StageTrace trace = new StageTrace(STAGE, requests.size(), kept.size(), notes);
        return new StageResult(kept, problems, trace);
    }

    private static List<IndexedRequest> removeConflicts(List<IndexedRequest> requests,
                                                        List<Problem> problems, List<String> notes) {
        // TreeMap so conflict notes come out in requestId order whatever the input order.
        Map<String, List<IndexedRequest>> byRequestId = requests.stream()
                .collect(Collectors.groupingBy(indexed -> indexed.request().requestId(),
                        TreeMap::new, Collectors.toList()));

        List<IndexedRequest> remaining = new ArrayList<>();
        byRequestId.forEach((requestId, group) -> {
            if (hasConflict(group)) {
                group.forEach(indexed -> problems.add(Problem.conflictingRequestId(indexed.inputIndex(), requestId)));
                notes.add(requestId + " rejected: " + group.size()
                        + " requests share this id with different content");
            } else {
                remaining.addAll(group);
            }
        });
        return remaining;
    }

    // Any difference in any field makes the whole group a conflict, even if some members
    // are exact repeats of each other: we can't tell which version is the real one.
    private static boolean hasConflict(List<IndexedRequest> group) {
        WorkRequest first = group.get(0).request();
        return group.stream().anyMatch(indexed -> !indexed.request().equals(first));
    }

    private List<IndexedRequest> mergeDuplicates(List<IndexedRequest> requests,
                                                 List<Problem> problems, List<String> notes) {
        Map<DuplicateKey, List<IndexedRequest>> groups = new LinkedHashMap<>();
        for (IndexedRequest indexed : requests) {
            groups.computeIfAbsent(keyOf(indexed.request()), key -> new ArrayList<>()).add(indexed);
        }

        // Survivor ids are unique once conflicts are gone, so this gives a stable note order.
        List<List<IndexedRequest>> groupsBySurvivorId = groups.values().stream()
                .sorted(Comparator.comparing(group -> Collections.min(group, SURVIVOR_ORDER),
                        BY_REQUEST_ID))
                .toList();

        List<IndexedRequest> kept = new ArrayList<>();
        for (List<IndexedRequest> group : groupsBySurvivorId) {
            kept.add(resolveGroup(group, problems, notes));
        }
        return kept;
    }

    private static IndexedRequest resolveGroup(List<IndexedRequest> group,
                                               List<Problem> problems, List<String> notes) {
        IndexedRequest survivor = Collections.min(group, SURVIVOR_ORDER);
        if (group.size() == 1) {
            return survivor;
        }
        List<IndexedRequest> dropped = group.stream()
                .filter(indexed -> indexed.inputIndex() != survivor.inputIndex())
                .sorted(BY_REQUEST_ID)
                .toList();

        String keptId = survivor.request().requestId();
        Priority original = survivor.request().priority();
        Priority highest = group.stream()
                .map(indexed -> indexed.request().priority())
                .max(Comparator.comparingInt(Priority::weight))
                .orElseThrow();
        // Sorted by id, so the first dropped request at the highest priority is the lowest id.
        IndexedRequest escalatedBy = highest.weight() > original.weight()
                ? dropped.stream().filter(indexed -> indexed.request().priority() == highest).findFirst().orElseThrow()
                : null;

        for (IndexedRequest indexed : dropped) {
            String droppedId = indexed.request().requestId();
            problems.add(indexed == escalatedBy
                    ? Problem.duplicateWithEscalation(indexed.inputIndex(), droppedId, keptId, original, highest)
                    : Problem.duplicate(indexed.inputIndex(), droppedId, keptId));
        }
        notes.add(keptId + " kept; merged duplicates " + dropped.stream()
                .map(indexed -> indexed.request().requestId())
                .collect(Collectors.joining(", ")));

        if (escalatedBy == null) {
            return survivor;
        }
        notes.add(keptId + " escalated " + original + " to " + highest
                + " (from " + escalatedBy.request().requestId() + ")");
        return new IndexedRequest(survivor.inputIndex(), survivor.request().withPriority(highest));
    }

    private DuplicateKey keyOf(WorkRequest request) {
        return new DuplicateKey(request.equipmentId().strip().toLowerCase(Locale.ROOT),
                normalizer.normalize(request.description()));
    }

    // A record rather than a concatenated string, so ("a|b", "c") and ("a", "b|c") can't collide.
    private record DuplicateKey(String equipmentId, String description) {
    }
}
