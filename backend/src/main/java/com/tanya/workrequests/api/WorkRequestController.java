package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tanya.workrequests.api.dto.BatchResponse;
import com.tanya.workrequests.config.BatchProperties;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Work requests")
@RestController
@RequestMapping(path = "/api/work-requests", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkRequestController {

    static final String EXAMPLE_BATCH = """
            [
              {"requestId": "REQ-1", "equipmentId": "PUMP-7", "priority": "MEDIUM",
               "requestedAt": "2026-10-20T08:15:00Z", "description": "Pump leaking"},
              {"requestId": "REQ-2", "equipmentId": " pump-7 ", "priority": "CRITICAL",
               "requestedAt": "2026-10-20T11:05:00Z", "description": "pump  LEAKING"},
              {"requestId": "REQ-3", "equipmentId": "FAN-6", "priority": "URGENT",
               "requestedAt": "2026-10-20T13:00:00Z", "description": "Belt squeal"}
            ]""";

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

    @Operation(summary = "Validate, deduplicate, and prioritize a batch",
            description = "Always 200 for a JSON array within the size limit, however broken its elements: "
                    + "each issue is a problem in the response. 400 for malformed JSON, a non-array body, "
                    + "or more than app.batch.max-size elements.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(type = "array", description = "Work requests; malformed elements are reported, not rejected"),
            examples = @ExampleObject(name = "duplicate with escalation and an unknown priority", value = EXAMPLE_BATCH)))
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

    @Operation(summary = "The 13-request demo batch, raw", description = "Post it back to /batch to see every rule at work.")
    @GetMapping("/sample")
    public JsonNode sample() {
        return sampleBatchSource.raw();
    }
}
