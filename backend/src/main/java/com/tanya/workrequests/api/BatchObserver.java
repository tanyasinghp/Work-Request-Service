package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Records one batch: a timer, counters, and exactly one INFO summary line. The line carries
 * counts only, never request content, so descriptions can't leak into logs.
 */
public class BatchObserver {

    private static final Logger log = LoggerFactory.getLogger(BatchObserver.class);

    private final MeterRegistry registry;
    private final Counter processed;
    private final Map<ProblemCode, Counter> rejectedByCode;
    private final Timer duration;

    public BatchObserver(MeterRegistry registry) {
        this.registry = registry;
        this.processed = Counter.builder("workrequests.processed")
                .description("Work requests received in accepted batches")
                .register(registry);
        // Registered up front so every code reports 0 before its first problem.
        this.rejectedByCode = Arrays.stream(ProblemCode.values())
                .collect(Collectors.toMap(Function.identity(), code -> Counter.builder("workrequests.rejected")
                                .description("Problems reported, by code (a request with two bad fields counts twice)")
                                .tag("code", code.name())
                                .register(registry),
                        (first, second) -> first, () -> new EnumMap<>(ProblemCode.class)));
        this.duration = Timer.builder("workrequests.batch.duration")
                .description("Time to map and process one batch")
                .register(registry);
    }

    public ProcessingResult observe(int batchSize, Supplier<ProcessingResult> processing) {
        Timer.Sample sample = Timer.start(registry);
        ProcessingResult result = processing.get();
        long nanos = sample.stop(duration);

        processed.increment(batchSize);
        Map<ProblemCode, Long> problemCounts = result.problems().stream()
                .collect(Collectors.groupingBy(Problem::code, () -> new EnumMap<>(ProblemCode.class), Collectors.counting()));
        problemCounts.forEach((code, count) -> rejectedByCode.get(code).increment(count));

        log.info("Processed batch size={} accepted={} problems={} durationMs={}",
                batchSize, result.accepted().size(), problemCounts, TimeUnit.NANOSECONDS.toMillis(nanos));
        return result;
    }
}
