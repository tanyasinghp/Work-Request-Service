package com.tanya.workrequests.config;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** ProcessingConfig must build the sorter bean with BatchProperties.maxLowPriority(). */
class ProcessingConfigLowPriorityCapTest {

    @Test
    void sorterBeanCapsLowRequestsAtTheConfiguredMaxLowPriority() {
        contextWith(new BatchProperties(5000, 2)).run(context -> {
            ProcessingResult result = process(context.getBean(WorkRequestProcessor.class));

            assertThat(result.accepted()).extracting(WorkRequest::requestId)
                    .containsExactly("L-1", "L-2");
        });
    }

    @Test
    void sorterBeanReportsLowRequestsOverTheConfiguredMaxLowPriority() {
        contextWith(new BatchProperties(5000, 2)).run(context -> {
            ProcessingResult result = process(context.getBean(WorkRequestProcessor.class));

            assertThat(result.problems()).extracting(Problem::requestId, Problem::code)
                    .containsExactly(tuple("L-3", ProblemCode.OVER_LOW_PRIORITY_CAP));
        });
    }

    @Test
    void sorterBeanAcceptsEveryLowRequestUnderTheDefaultCap() {
        contextWith(new BatchProperties(5000)).run(context -> {
            ProcessingResult result = process(context.getBean(WorkRequestProcessor.class));

            assertThat(result.accepted()).hasSize(3);
        });
    }

    private static ApplicationContextRunner contextWith(BatchProperties properties) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProcessingConfig.class)
                .withBean(BatchProperties.class, () -> properties);
    }

    private static ProcessingResult process(WorkRequestProcessor processor) {
        return processor.process(List.of(
                low("L-1", "2026-10-20T08:00:00Z"),
                low("L-2", "2026-10-20T08:01:00Z"),
                low("L-3", "2026-10-20T08:02:00Z")));
    }

    private static WorkRequest low(String requestId, String requestedAt) {
        return new WorkRequest(requestId, "EQ-" + requestId, Priority.LOW,
                Instant.parse(requestedAt), "Low work for " + requestId);
    }
}
