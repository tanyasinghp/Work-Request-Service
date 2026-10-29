package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.List;
import java.util.Objects;

/**
 * Runs the stages in order: validate, deduplicate, prioritize. Each stage only sees what
 * the previous stage passed on; the processor just wires them and merges their output.
 */
public class WorkRequestProcessor {

    private final WorkRequestValidator validator;
    private final DuplicateResolver duplicateResolver;
    private final WorkRequestSorter sorter;

    public WorkRequestProcessor(WorkRequestValidator validator,
                                DuplicateResolver duplicateResolver,
                                WorkRequestSorter sorter) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.duplicateResolver = Objects.requireNonNull(duplicateResolver, "duplicateResolver");
        this.sorter = Objects.requireNonNull(sorter, "sorter");
    }

    public ProcessingResult process(List<WorkRequest> batch) {
        // Null is treated as empty and still runs every stage, so the trace always has three entries.
        List<WorkRequest> requests = batch == null ? List.of() : batch;
        return runFrom(validator.validate(requests));
    }

    /** Processes a batch read from raw input, reporting unreadable fields as INVALID_VALUE. */
    public ProcessingResult process(ParsedBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return runFrom(validator.validate(batch.requests(), batch.invalidFieldsByIndex()));
    }

    private ProcessingResult runFrom(StageResult validated) {
        StageResult deduplicated = duplicateResolver.resolve(validated.requests());
        StageResult prioritized = sorter.sort(deduplicated.requests());
        List<StageResult> stages = List.of(validated, deduplicated, prioritized);

        // Each stage already orders its own problems by input index; concatenating keeps stage order.
        return new ProcessingResult(
                prioritized.requests().stream().map(IndexedRequest::request).toList(),
                stages.stream().flatMap(stage -> stage.problems().stream()).toList(),
                stages.stream().map(StageResult::trace).toList());
    }
}
