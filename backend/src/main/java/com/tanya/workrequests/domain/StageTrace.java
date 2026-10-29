package com.tanya.workrequests.domain;

import java.util.List;

/**
 * What one processing stage did: how many requests went in and came out, plus
 * human-readable notes. The UI renders this instead of re-implementing the logic.
 */
public record StageTrace(String stage, int in, int out, List<String> notes) {

    public StageTrace {
        notes = List.copyOf(notes);
    }
}
