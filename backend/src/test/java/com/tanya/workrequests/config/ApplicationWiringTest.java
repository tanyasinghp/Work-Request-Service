package com.tanya.workrequests.config;

import com.tanya.workrequests.api.RequestMapper;
import com.tanya.workrequests.api.SampleBatchSource;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationWiringTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BatchProperties batchProperties;

    @Test
    void createsOneSharedProcessor() {
        assertThat(context.getBean(WorkRequestProcessor.class)).isSameAs(context.getBean(WorkRequestProcessor.class));
    }

    @Test
    void wiresTheRealStagesSoTheSampleProducesTheKnownQueue() {
        RequestMapper mapper = context.getBean(RequestMapper.class);
        SampleBatchSource sample = context.getBean(SampleBatchSource.class);

        ProcessingResult result = context.getBean(WorkRequestProcessor.class).process(mapper.map(sample.raw()));

        assertThat(result.accepted()).extracting(WorkRequest::requestId)
                .containsExactly("REQ-101", "REQ-102", "REQ-104", "REQ-110", "REQ-111", "REQ-112");
    }

    @Test
    void bindsTheBatchSizeLimitWithItsDefault() {
        assertThat(batchProperties.maxSize()).isEqualTo(5000);
    }
}
