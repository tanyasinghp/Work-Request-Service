package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * How old a request may be before it is flagged as stale and kept out of the queue.
 *
 * <p>Bound from {@code app.staleness.*}. A non-positive maximum age would reject every
 * request, so it is refused at startup rather than silently emptying the queue.
 */
@ConfigurationProperties("app.staleness")
public record StalenessProperties(@DefaultValue("30d") Duration maxAge) {

    public StalenessProperties {
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("app.staleness.max-age must be positive");
        }
    }
}
