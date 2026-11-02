package com.tanya.workrequests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanya.workrequests.api.RequestBodyLimitFilter;
import com.tanya.workrequests.api.RequestMapper;
import com.tanya.workrequests.api.SampleBatchSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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

    // Runs before Spring MVC (and so before Jackson) on every API request.
    @Bean
    FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(RequestBodyProperties properties,
                                                                          ObjectMapper objectMapper) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(properties.maxBodySize(), objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
