package com.tanya.workrequests.domain;

/**
 * Urgency of a work request. Comparisons use {@link #weight()}, never {@code ordinal()},
 * so reordering the constants can't silently change how requests are sorted.
 */
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
