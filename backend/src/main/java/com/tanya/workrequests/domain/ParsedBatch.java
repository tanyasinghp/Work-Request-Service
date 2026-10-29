package com.tanya.workrequests.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A batch as read from raw input: one entry per input element (null where the element wasn't
 * a request), plus the fields that were present but unreadable, keyed by input index.
 * Unreadable fields are null in {@code requests}; {@code invalidFieldsByIndex} lets the
 * validator report them as invalid rather than missing.
 */
public record ParsedBatch(List<WorkRequest> requests, Map<Integer, List<InvalidField>> invalidFieldsByIndex) {

    public ParsedBatch {
        // Not List.copyOf: it rejects nulls, and null entries keep every later index in place.
        requests = Collections.unmodifiableList(new ArrayList<>(requests));
        invalidFieldsByIndex = invalidFieldsByIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public List<InvalidField> invalidFieldsAt(int index) {
        return invalidFieldsByIndex.getOrDefault(index, List.of());
    }
}
