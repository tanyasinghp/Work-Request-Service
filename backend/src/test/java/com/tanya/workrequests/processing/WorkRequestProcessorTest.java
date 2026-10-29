package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.tanya.workrequests.domain.Priority.CRITICAL;
import static com.tanya.workrequests.domain.Priority.HIGH;
import static com.tanya.workrequests.domain.Priority.LOW;
import static com.tanya.workrequests.domain.Priority.MEDIUM;
import static com.tanya.workrequests.processing.TestRequests.request;
import static com.tanya.workrequests.processing.TestRequests.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkRequestProcessorTest {

    private static final StageTrace VALIDATE_TRACE = new StageTrace("validate", 3, 2, List.of("v"));
    private static final StageTrace DEDUPLICATE_TRACE = new StageTrace("deduplicate", 2, 1, List.of("d"));
    private static final StageTrace PRIORITIZE_TRACE = new StageTrace("prioritize", 1, 1, List.of("p"));

    @Mock
    private WorkRequestValidator validator;
    @Mock
    private DuplicateResolver duplicateResolver;
    @Mock
    private WorkRequestSorter sorter;

    private final WorkRequest first = valid("REQ-1");
    private final WorkRequest second = valid("REQ-2");
    private final List<WorkRequest> batch = Arrays.asList(first, second, null);

    private final List<IndexedRequest> validated = List.of(new IndexedRequest(0, first), new IndexedRequest(1, second));
    private final List<IndexedRequest> deduplicated = List.of(new IndexedRequest(1, second));
    private final List<IndexedRequest> sorted = List.of(new IndexedRequest(1, second));

    private final Problem nullRequest = Problem.nullRequest(2);
    private final Problem duplicate = Problem.duplicate(0, "REQ-1", "REQ-2");

    private void stubStages() {
        when(validator.validate(batch)).thenReturn(new StageResult(validated, List.of(nullRequest), VALIDATE_TRACE));
        when(duplicateResolver.resolve(validated)).thenReturn(new StageResult(deduplicated, List.of(duplicate), DEDUPLICATE_TRACE));
        when(sorter.sort(deduplicated)).thenReturn(new StageResult(sorted, List.of(), PRIORITIZE_TRACE));
    }

    private ProcessingResult process(List<WorkRequest> input) {
        return new WorkRequestProcessor(validator, duplicateResolver, sorter).process(input);
    }

    @Test
    void callsStagesInOrder() {
        stubStages();

        process(batch);

        InOrder inOrder = inOrder(validator, duplicateResolver, sorter);
        inOrder.verify(validator).validate(batch);
        inOrder.verify(duplicateResolver).resolve(anyList());
        inOrder.verify(sorter).sort(anyList());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void passesEachStageOutputToTheNext() {
        stubStages();

        ProcessingResult result = process(batch);

        verify(duplicateResolver).resolve(validated);
        verify(sorter).sort(deduplicated);
        assertThat(result.accepted()).containsExactly(second);
    }

    @Test
    void mergesProblemsFromAllStages() {
        Problem conflict = Problem.conflictingRequestId(1, "REQ-2");
        when(validator.validate(batch)).thenReturn(new StageResult(validated, List.of(nullRequest), VALIDATE_TRACE));
        // Deduplicate reports a lower index than validate; stage order still wins.
        when(duplicateResolver.resolve(validated)).thenReturn(
                new StageResult(List.of(), List.of(duplicate, conflict), DEDUPLICATE_TRACE));
        when(sorter.sort(List.of())).thenReturn(new StageResult(List.of(), List.of(), PRIORITIZE_TRACE));

        ProcessingResult result = process(batch);

        assertThat(result.problems()).containsExactly(nullRequest, duplicate, conflict);
    }

    @Test
    void collectsOneTracePerStage() {
        stubStages();

        ProcessingResult result = process(batch);

        assertThat(result.trace()).containsExactly(VALIDATE_TRACE, DEDUPLICATE_TRACE, PRIORITIZE_TRACE);
    }

    @Test
    void treatsNullListAsEmpty() {
        when(validator.validate(List.of())).thenReturn(new StageResult(List.of(), List.of(), VALIDATE_TRACE));
        when(duplicateResolver.resolve(List.of())).thenReturn(new StageResult(List.of(), List.of(), DEDUPLICATE_TRACE));
        when(sorter.sort(List.of())).thenReturn(new StageResult(List.of(), List.of(), PRIORITIZE_TRACE));

        ProcessingResult result = process(null);

        verify(validator).validate(List.of());
        assertThat(result.accepted()).isEmpty();
        assertThat(result.problems()).isEmpty();
        assertThat(result.trace()).hasSize(3);
    }

    @Test
    void returnsImmutableResult() {
        stubStages();

        ProcessingResult result = process(batch);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.accepted().add(first));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.problems().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.trace().remove(0));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.trace().get(0).notes().add("x"));
    }

    @Test
    void processesBatchEndToEndWithRealStages() {
        WorkRequestProcessor processor = new WorkRequestProcessor(
                new WorkRequestValidator(), new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter());
        List<WorkRequest> input = Arrays.asList(
                request("REQ-1", "PUMP-7", MEDIUM, "2026-10-20T08:00:00Z", "Pump leaking"),
                null,
                request("REQ-2", "FAN-1", LOW, "2026-10-20T07:00:00Z", "Belt squeal"),
                request("REQ-3", "pump-7", CRITICAL, "2026-10-20T09:00:00Z", " pump leaking "),
                request("REQ-4", "DOOR-5", HIGH, null, "Lock jammed"),
                request("REQ-5", "ROOF-2", HIGH, "2026-10-20T06:00:00Z", "Gutter blocked"),
                request("REQ-5", "ROOF-3", HIGH, "2026-10-20T06:00:00Z", "Gutter blocked"),
                request("REQ-6", "HVAC-2", HIGH, "2026-10-20T10:00:00Z", "Filter clogged"));

        ProcessingResult result = processor.process(input);

        assertThat(result.accepted()).extracting(WorkRequest::requestId, WorkRequest::priority).containsExactly(
                tuple("REQ-1", CRITICAL),
                tuple("REQ-6", HIGH),
                tuple("REQ-2", LOW));
        assertThat(result.problems()).extracting(Problem::inputIndex, Problem::code).containsExactly(
                tuple(1, ProblemCode.NULL_REQUEST),
                tuple(4, ProblemCode.MISSING_FIELD),
                tuple(3, ProblemCode.DUPLICATE),
                tuple(5, ProblemCode.CONFLICTING_REQUEST_ID),
                tuple(6, ProblemCode.CONFLICTING_REQUEST_ID));
        assertThat(result.trace()).extracting(StageTrace::stage, StageTrace::in, StageTrace::out).containsExactly(
                tuple("validate", 8, 6),
                tuple("deduplicate", 6, 3),
                tuple("prioritize", 3, 3));
    }

    @Test
    void passesInvalidFieldsOfAParsedBatchToTheValidator() {
        Map<Integer, List<InvalidField>> invalidFields = Map.of(2, List.of(new InvalidField("priority", "URGENT")));
        ParsedBatch parsed = new ParsedBatch(batch, invalidFields);
        when(validator.validate(parsed.requests(), invalidFields))
                .thenReturn(new StageResult(validated, List.of(nullRequest), VALIDATE_TRACE));
        when(duplicateResolver.resolve(validated)).thenReturn(new StageResult(deduplicated, List.of(duplicate), DEDUPLICATE_TRACE));
        when(sorter.sort(deduplicated)).thenReturn(new StageResult(sorted, List.of(), PRIORITIZE_TRACE));

        ProcessingResult result = new WorkRequestProcessor(validator, duplicateResolver, sorter).process(parsed);

        InOrder inOrder = inOrder(validator, duplicateResolver, sorter);
        inOrder.verify(validator).validate(parsed.requests(), invalidFields);
        inOrder.verify(duplicateResolver).resolve(validated);
        inOrder.verify(sorter).sort(deduplicated);
        assertThat(result.accepted()).containsExactly(second);
        assertThat(result.trace()).containsExactly(VALIDATE_TRACE, DEDUPLICATE_TRACE, PRIORITIZE_TRACE);
    }

    @Test
    void reportsInvalidValuesFromAParsedBatchEndToEnd() {
        WorkRequestProcessor processor = new WorkRequestProcessor(
                new WorkRequestValidator(), new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter());
        // Index 1 looks like a Critical duplicate of REQ-1, but its date is unreadable.
        ParsedBatch parsed = new ParsedBatch(
                Arrays.asList(
                        request("REQ-1", "PUMP-7", MEDIUM, "2026-10-20T08:00:00Z", "Pump leaking"),
                        request("REQ-2", "PUMP-7", CRITICAL, null, "Pump leaking"),
                        null,
                        request("REQ-4", "FAN-1", null, "2026-10-20T09:00:00Z", "Belt squeal")),
                Map.of(1, List.of(new InvalidField("requestedAt", "yesterday")),
                        3, List.of(new InvalidField("priority", "URGENT"))));

        ProcessingResult result = processor.process(parsed);

        assertThat(result.accepted()).extracting(WorkRequest::requestId, WorkRequest::priority)
                .containsExactly(tuple("REQ-1", MEDIUM));
        assertThat(result.problems()).extracting(Problem::inputIndex, Problem::code, Problem::message).containsExactly(
                tuple(1, ProblemCode.INVALID_VALUE, "REQ-2 has invalid requestedAt 'yesterday'"),
                tuple(2, ProblemCode.NULL_REQUEST, "Request is null"),
                tuple(3, ProblemCode.INVALID_VALUE, "REQ-4 has invalid priority 'URGENT'"));
    }
}
