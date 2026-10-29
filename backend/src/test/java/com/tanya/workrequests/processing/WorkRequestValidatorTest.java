package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.tanya.workrequests.processing.TestRequests.DEFAULT_TIME;
import static com.tanya.workrequests.processing.TestRequests.request;
import static com.tanya.workrequests.processing.TestRequests.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WorkRequestValidatorTest {

    private final WorkRequestValidator validator = new WorkRequestValidator();

    @Test
    void acceptsCompleteRequest() {
        WorkRequest request = valid("REQ-1");

        StageResult result = validator.validate(List.of(request));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, request));
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void reportsNullElementWithItsIndex() {
        StageResult result = validator.validate(Arrays.asList(valid("REQ-1"), null, valid("REQ-3")));

        assertThat(result.requests()).extracting(IndexedRequest::inputIndex).containsExactly(0, 2);
        assertThat(result.problems()).containsExactly(Problem.nullRequest(1));
    }

    static Stream<Arguments> missingStringFields() {
        return Stream.of("requestId", "equipmentId", "description")
                .flatMap(field -> Stream.of(null, "", "   ", "\t\n")
                        .map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest(name = "{0} = [{1}]")
    @MethodSource("missingStringFields")
    void reportsMissingStringField(String field, String value) {
        WorkRequest request = withStringField(field, value);

        StageResult result = validator.validate(List.of(request));

        assertThat(result.requests()).isEmpty();
        String expectedRequestId = field.equals("requestId") ? null : "REQ-1";
        assertThat(result.problems()).containsExactly(Problem.missingField(0, expectedRequestId, field));
    }

    @Test
    void reportsMissingRequestedAt() {
        WorkRequest request = request("REQ-1", "PUMP-7", Priority.HIGH, null, "Pump leaking");

        StageResult result = validator.validate(List.of(request));

        assertThat(result.problems())
                .extracting(Problem::code, Problem::field, Problem::message)
                .containsExactly(tuple(ProblemCode.MISSING_FIELD, "requestedAt", "REQ-1 is missing requestedAt"));
    }

    @Test
    void reportsMissingPriority() {
        WorkRequest request = request("REQ-1", "PUMP-7", null, DEFAULT_TIME, "Pump leaking");

        StageResult result = validator.validate(List.of(request));

        assertThat(result.requests()).isEmpty();
        assertThat(result.problems()).containsExactly(Problem.missingField(0, "REQ-1", "priority"));
    }

    @Test
    void reportsEachMissingFieldSeparately() {
        WorkRequest request = request("REQ-1", " ", Priority.LOW, null, "Pump leaking");

        StageResult result = validator.validate(List.of(request));

        assertThat(result.problems()).containsExactly(
                Problem.missingField(0, "REQ-1", "equipmentId"),
                Problem.missingField(0, "REQ-1", "requestedAt"));
    }

    @Test
    void reportsEveryFieldOfAnEmptyRequest() {
        StageResult result = validator.validate(List.of(new WorkRequest(null, null, null, null, null)));

        assertThat(result.problems()).extracting(Problem::field)
                .containsExactly("requestId", "equipmentId", "priority", "requestedAt", "description");
        assertThat(result.problems()).extracting(Problem::message)
                .first().isEqualTo("Request is missing requestId");
    }

    @Test
    void preservesInputOrderOfValidRequests() {
        WorkRequest third = valid("REQ-3");
        WorkRequest first = valid("REQ-1");
        WorkRequest second = valid("REQ-2");
        WorkRequest invalid = request("REQ-4", null, Priority.LOW, DEFAULT_TIME, "Light out");

        StageResult result = validator.validate(List.of(third, invalid, first, second));

        assertThat(result.requests()).containsExactly(
                new IndexedRequest(0, third),
                new IndexedRequest(2, first),
                new IndexedRequest(3, second));
    }

    @Test
    void recordsInAndOutCountsInTrace() {
        List<WorkRequest> batch = Arrays.asList(
                valid("REQ-1"),
                null,
                request(" ", null, Priority.LOW, DEFAULT_TIME, "Light out"),
                valid("REQ-4"));

        StageResult result = validator.validate(batch);

        assertThat(result.trace().stage()).isEqualTo("validate");
        assertThat(result.trace().in()).isEqualTo(4);
        assertThat(result.trace().out()).isEqualTo(2);
        assertThat(result.trace().notes()).containsExactly(
                "Null request rejected",
                "Request without id rejected: missing requestId, missing equipmentId");
    }

    @Test
    void sortsTraceNotesByTextSoInputOrderDoesNotMatter() {
        WorkRequest missingEquipment = request("REQ-9", null, Priority.LOW, DEFAULT_TIME, "Light out");
        WorkRequest missingPriority = request("REQ-2", "FAN-1", null, DEFAULT_TIME, "Belt squeal");
        List<WorkRequest> batch = Arrays.asList(missingEquipment, valid("REQ-1"), null, missingPriority);
        List<WorkRequest> reversed = Arrays.asList(missingPriority, null, valid("REQ-1"), missingEquipment);

        List<String> notes = validator.validate(batch).trace().notes();

        assertThat(notes).containsExactly(
                "Null request rejected",
                "REQ-2 rejected: missing priority",
                "REQ-9 rejected: missing equipmentId");
        assertThat(validator.validate(reversed).trace().notes()).isEqualTo(notes);
    }

    @Test
    void handlesEmptyBatch() {
        StageResult result = validator.validate(List.of());

        assertThat(result.requests()).isEmpty();
        assertThat(result.problems()).isEmpty();
        assertThat(result.trace().in()).isZero();
        assertThat(result.trace().out()).isZero();
    }

    private static WorkRequest withStringField(String field, String value) {
        return switch (field) {
            case "requestId" -> request(value, "PUMP-7", Priority.HIGH, DEFAULT_TIME, "Pump leaking");
            case "equipmentId" -> request("REQ-1", value, Priority.HIGH, DEFAULT_TIME, "Pump leaking");
            case "description" -> request("REQ-1", "PUMP-7", Priority.HIGH, DEFAULT_TIME, value);
            default -> throw new IllegalArgumentException("Unknown field " + field);
        };
    }
}
