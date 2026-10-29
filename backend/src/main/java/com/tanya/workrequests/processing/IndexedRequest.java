package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.WorkRequest;

/**
 * A request paired with its position in the original batch, so later stages can still
 * report {@code inputIndex} after earlier stages have dropped requests.
 */
public record IndexedRequest(int inputIndex, WorkRequest request) {
}
