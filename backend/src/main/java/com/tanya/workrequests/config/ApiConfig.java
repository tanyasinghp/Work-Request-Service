package com.tanya.workrequests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanya.workrequests.api.RequestMapper;
import com.tanya.workrequests.api.SampleBatchSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApiConfig {

    @Bean
    RequestMapper requestMapper() {
        return new RequestMapper();
    }

    // Spring Boot's ObjectMapper, so the sample is read exactly as request bodies are.
    @Bean
    SampleBatchSource sampleBatchSource(ObjectMapper objectMapper) {
        return new SampleBatchSource(objectMapper);
    }
}
