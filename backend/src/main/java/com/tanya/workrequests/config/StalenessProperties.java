package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * How old a request may be before it is rejected as stale. Validated here so a misconfigured
 * threshold fails startup instead of silently rejecting every request.
 */
@ConfigurationProperties("app.staleness")
public record StalenessProperties(@DefaultValue("30d") Duration maxAge) {

    // Below one day the "n days old" problem message would always read "0 days".
    private static final Duration MIN_MAX_AGE = Duration.ofDays(1);

    public StalenessProperties {
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.compareTo(MIN_MAX_AGE) < 0) {
            throw new IllegalArgumentException("app.staleness.max-age must be at least 1 day");
        }
    }
}
