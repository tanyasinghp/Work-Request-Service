package com.tanya.workrequests.config;

import com.tanya.workrequests.processing.DescriptionNormalizer;
import com.tanya.workrequests.processing.DuplicateResolver;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import com.tanya.workrequests.processing.WorkRequestSorter;
import com.tanya.workrequests.processing.WorkRequestValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free processing classes. Every stage is stateless, so one shared
 * instance of each (Spring's default singleton scope) is safe across concurrent requests.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessingConfig {

    @Bean
    DescriptionNormalizer descriptionNormalizer() {
        return new DescriptionNormalizer();
    }

    @Bean
    WorkRequestValidator workRequestValidator() {
        return new WorkRequestValidator();
    }

    @Bean
    DuplicateResolver duplicateResolver(DescriptionNormalizer descriptionNormalizer) {
        return new DuplicateResolver(descriptionNormalizer);
    }

    @Bean
    WorkRequestSorter workRequestSorter(BatchProperties batchProperties) {
        return new WorkRequestSorter(batchProperties.maxLowPriority());
    }

    @Bean
    WorkRequestProcessor workRequestProcessor(WorkRequestValidator validator,
                                              DuplicateResolver duplicateResolver,
                                              WorkRequestSorter sorter) {
        return new WorkRequestProcessor(validator, duplicateResolver, sorter);
    }
}
