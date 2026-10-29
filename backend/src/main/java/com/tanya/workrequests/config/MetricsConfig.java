package com.tanya.workrequests.config;

import com.tanya.workrequests.api.BatchObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MetricsConfig {

    @Bean
    BatchObserver batchObserver(MeterRegistry meterRegistry) {
        return new BatchObserver(meterRegistry);
    }
}
