package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.tanya.workrequests.domain.Priority.CRITICAL;
import static com.tanya.workrequests.domain.Priority.HIGH;
import static com.tanya.workrequests.domain.Priority.LOW;
import static com.tanya.workrequests.domain.Priority.MEDIUM;
import static com.tanya.workrequests.domain.ProblemCode.CONFLICTING_REQUEST_ID;
import static com.tanya.workrequests.domain.ProblemCode.DUPLICATE;
import static com.tanya.workrequests.domain.ProblemCode.MISSING_FIELD;
import static com.tanya.workrequests.domain.ProblemCode.NULL_REQUEST;
import static com.tanya.workrequests.processing.TestRequests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * One row per line of the edge-case table in docs/SPEC.md, run through the real processor.
 * Display names mirror the table so the test report reads as documentation.
 */
class EdgeCaseTest {

    private static final String T0800 = "2026-10-20T08:00:00Z";
    private static final String T0900 = "2026-10-20T09:00:00Z";
    private static final String T1000 = "2026-10-20T10:00:00Z";

    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    static Stream<Arguments> edgeCases() {
        return Stream.of(
                row("Null list → empty accepted, no problems",
                        null,
                        accepted(),
                        problems()),
                row("Empty list → empty accepted, no problems",
                        List.of(),
                        accepted(),
                        problems()),
                row("Null element in list → NULL_REQUEST with its index",
                        Arrays.asList(request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking"), null),
                        accepted(tuple("REQ-1", MEDIUM)),
                        problems(problem(1, NULL_REQUEST, null, null))),
                row("Missing or blank requestId → MISSING_FIELD, field = requestId",
                        List.of(
                                request(null, "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                                request("   ", "FAN-1", MEDIUM, T0800, "Belt squeal"),
                                request("REQ-3", "DOOR-5", MEDIUM, T0800, "Lock jammed")),
                        accepted(tuple("REQ-3", MEDIUM)),
                        problems(
                                problem(0, MISSING_FIELD, "requestId", null),
                                problem(1, MISSING_FIELD, "requestId", null))),
                row("Request missing two fields → two MISSING_FIELD problems",
                        List.of(request("REQ-1", null, HIGH, T0800, "   ")),
                        accepted(),
                        problems(
                                problem(0, MISSING_FIELD, "equipmentId", null),
                                problem(0, MISSING_FIELD, "description", null))),
                row("Missing requestedAt → MISSING_FIELD, field = requestedAt",
                        List.of(request("REQ-1", "PUMP-7", HIGH, null, "Pump leaking")),
                        accepted(),
                        problems(problem(0, MISSING_FIELD, "requestedAt", null))),
                row("Same equipment + description, different case or spacing → one kept, one DUPLICATE",
                        List.of(
                                request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                                request("REQ-2", " pump-7 ", MEDIUM, T0900, "  PUMP   leaking ")),
                        accepted(tuple("REQ-1", MEDIUM)),
                        problems(problem(1, DUPLICATE, null, "REQ-1"))),
                row("Same description, different equipment → both kept",
                        List.of(
                                request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                                request("REQ-2", "PUMP-8", MEDIUM, T0900, "Pump leaking")),
                        accepted(tuple("REQ-1", MEDIUM), tuple("REQ-2", MEDIUM)),
                        problems()),
                row("Duplicate with higher priority than the original → original kept, escalated, duplicate reported",
                        List.of(
                                request("REQ-1", "PUMP-7", LOW, T0800, "Pump leaking"),
                                request("REQ-2", "PUMP-7", CRITICAL, T0900, "Pump leaking")),
                        accepted(tuple("REQ-1", CRITICAL)),
                        problems(problem(1, DUPLICATE, null, "REQ-1"))),
                row("Three-way duplicate → earliest kept, two DUPLICATE problems",
                        List.of(
                                request("REQ-1", "PUMP-7", MEDIUM, T1000, "Pump leaking"),
                                request("REQ-2", "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                                request("REQ-3", "PUMP-7", MEDIUM, T0900, "Pump leaking")),
                        accepted(tuple("REQ-2", MEDIUM)),
                        problems(
                                problem(0, DUPLICATE, null, "REQ-2"),
                                problem(2, DUPLICATE, null, "REQ-2"))),
                row("Same requestId, different content → CONFLICTING_REQUEST_ID",
                        List.of(
                                request("REQ-1", "CHILLER-1", MEDIUM, T0800, "Compressor noise"),
                                request("REQ-1", "ROOF-2", LOW, T0900, "Gutter blocked"),
                                request("REQ-2", "PUMP-7", MEDIUM, T0800, "Pump leaking")),
                        accepted(tuple("REQ-2", MEDIUM)),
                        problems(
                                problem(0, CONFLICTING_REQUEST_ID, null, null),
                                problem(1, CONFLICTING_REQUEST_ID, null, null))),
                row("Same priority, different times → earlier first",
                        List.of(
                                request("REQ-1", "PUMP-7", HIGH, T0900, "Pump leaking"),
                                request("REQ-2", "FAN-1", HIGH, T0800, "Belt squeal")),
                        accepted(tuple("REQ-2", HIGH), tuple("REQ-1", HIGH)),
                        problems()),
                row("Same priority and time → sorted by requestId",
                        List.of(
                                request("REQ-3", "DOOR-5", HIGH, T0800, "Lock jammed"),
                                request("REQ-1", "PUMP-7", HIGH, T0800, "Pump leaking"),
                                request("REQ-2", "FAN-1", HIGH, T0800, "Belt squeal")),
                        accepted(tuple("REQ-1", HIGH), tuple("REQ-2", HIGH), tuple("REQ-3", HIGH)),
                        problems()),
                row("All requests invalid → empty accepted, all problems listed",
                        Arrays.asList(
                                null,
                                request(null, "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                                request("REQ-3", "FAN-1", null, T0800, "Belt squeal")),
                        accepted(),
                        problems(
                                problem(0, NULL_REQUEST, null, null),
                                problem(1, MISSING_FIELD, "requestId", null),
                                problem(2, MISSING_FIELD, "priority", null))),
                row("Invalid request that looks like a duplicate → reported as invalid only, never used for dedup",
                        // Earlier and Critical: if it took part in dedup it would win and escalate REQ-2.
                        List.of(
                                request(null, "PUMP-7", CRITICAL, T0800, "Pump leaking"),
                                request("REQ-2", "PUMP-7", MEDIUM, T0900, "Pump leaking")),
                        accepted(tuple("REQ-2", MEDIUM)),
                        problems(problem(0, MISSING_FIELD, "requestId", null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("edgeCases")
    void handlesEdgeCase(String description, List<WorkRequest> input,
                         List<Tuple> expectedAccepted, List<Tuple> expectedProblems) {
        ProcessingResult result = processor.process(input);

        assertThat(result.accepted())
                .extracting(WorkRequest::requestId, WorkRequest::priority)
                .containsExactlyElementsOf(expectedAccepted);
        assertThat(result.problems())
                .extracting(Problem::inputIndex, Problem::code, Problem::field, Problem::relatedRequestId)
                .containsExactlyElementsOf(expectedProblems);
    }

    private static Arguments row(String description, List<WorkRequest> input,
                                 List<Tuple> expectedAccepted, List<Tuple> expectedProblems) {
        return Arguments.of(description, input, expectedAccepted, expectedProblems);
    }

    /** Expected accepted requests as (requestId, priority), in queue order. */
    private static List<Tuple> accepted(Tuple... requests) {
        return List.of(requests);
    }

    private static List<Tuple> problems(Tuple... problems) {
        return List.of(problems);
    }

    private static Tuple problem(int inputIndex, ProblemCode code, String field, String relatedRequestId) {
        return tuple(inputIndex, code, field, relatedRequestId);
    }
}
