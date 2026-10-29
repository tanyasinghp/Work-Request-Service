package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.batch.*}: guards that protect the service from oversized input. */
@ConfigurationProperties("app.batch")
public record BatchProperties(@DefaultValue("5000") int maxSize) {

    public BatchProperties {
        if (maxSize < 1) {
            throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
        }
    }
}
