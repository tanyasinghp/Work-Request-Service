package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tanya.workrequests.api.dto.BatchResponse;
import com.tanya.workrequests.config.BatchProperties;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point. The body is taken as raw JSON, never typed DTOs, so a bad enum or date in
 * one request becomes a problem in the response instead of failing the whole batch.
 */
@RestController
@RequestMapping(path = "/api/work-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkRequestController {

    private final RequestMapper requestMapper;
    private final WorkRequestProcessor processor;
    private final SampleBatchSource sampleBatchSource;
    private final BatchProperties batchProperties;
    private final BatchObserver batchObserver;

    public WorkRequestController(RequestMapper requestMapper,
                                 WorkRequestProcessor processor,
                                 SampleBatchSource sampleBatchSource,
                                 BatchProperties batchProperties,
                                 BatchObserver batchObserver) {
        this.requestMapper = requestMapper;
        this.processor = processor;
        this.sampleBatchSource = sampleBatchSource;
        this.batchProperties = batchProperties;
        this.batchObserver = batchObserver;
    }

    @PostMapping(path = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BatchResponse processBatch(@RequestBody JsonNode body) {
        if (body == null || !body.isArray()) {
            throw new InvalidBatchException("Request body must be a JSON array of work requests");
        }
        // Checked before mapping or processing, so an oversized batch costs almost nothing.
        if (body.size() > batchProperties.maxSize()) {
            throw new InvalidBatchException("Batch has " + body.size() + " requests; the maximum is "
                    + batchProperties.maxSize());
        }
        ProcessingResult result = batchObserver.observe(body.size(),
                () -> processor.process(requestMapper.map((ArrayNode) body)));
        return BatchResponse.from(result);
    }

    @GetMapping("/sample")
    public JsonNode sample() {
        return sampleBatchSource.raw();
    }
}
