package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.batch.*}: guards that protect the service from oversized input. */
@ConfigurationProperties("app.batch")
public record BatchProperties(@DefaultValue("5000") int maxSize,
                              @DefaultValue("1000") int maxLowPriority) {

    static final int DEFAULT_MAX_LOW_PRIORITY = 1000;

    public BatchProperties {
        if (maxSize < 1) {
            throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
        }
        // 0 is legal and means "accept no Low work at all"; only negatives are nonsense.
        if (maxLowPriority < 0) {
            throw new IllegalArgumentException(
                    "app.batch.max-low-priority must be at least 0, was " + maxLowPriority);
        }
    }

    /** Kept for callers that only set the size guard; they get the default Low cap. */
    public BatchProperties(int maxSize) {
        this(maxSize, DEFAULT_MAX_LOW_PRIORITY);
    }
}
