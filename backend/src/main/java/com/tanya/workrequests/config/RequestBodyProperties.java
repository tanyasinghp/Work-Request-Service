package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * {@code app.request.*}: limits on the raw request, enforced before the body is parsed. The
 * element-count limit ({@code app.batch.max-size}) only applies once the JSON has been read,
 * so a huge body would otherwise cost its full size in memory first.
 */
@ConfigurationProperties("app.request")
public record RequestBodyProperties(@DefaultValue("2MB") DataSize maxBodySize) {

    // Bodies under the limit are buffered in memory when their length isn't declared.
    static final DataSize CEILING = DataSize.ofMegabytes(100);

    public RequestBodyProperties {
        if (maxBodySize.toBytes() < 1 || maxBodySize.compareTo(CEILING) > 0) {
            throw new IllegalArgumentException("app.request.max-body-size must be between 1B and 100MB, was " + maxBodySize);
        }
    }
}
