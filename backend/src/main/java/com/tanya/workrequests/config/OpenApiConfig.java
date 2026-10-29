package com.tanya.workrequests.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI workRequestOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Work Request Service")
                .version("0.1.0")
                .description("""
                        Processes a batch of maintenance work requests in three stages: validate, \
                        deduplicate, prioritize. Bad requests never fail the batch; each issue is \
                        returned as a problem with its input index. The response also carries a \
                        per-stage trace. Try `GET /api/work-requests/sample` for a messy demo batch \
                        you can post straight back."""));
    }
}
