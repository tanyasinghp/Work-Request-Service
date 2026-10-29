package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class RequestMapperTest {

    private static final String COMPLETE = """
            {"requestId": "REQ-1", "equipmentId": "PUMP-7", "priority": "HIGH",
             "requestedAt": "2026-10-20T08:15:00Z", "description": "Pump leaking"}""";

    private final RequestMapper mapper = new RequestMapper();

    @Test
    void mapsCompleteRequest() {
        ParsedBatch batch = map("[" + COMPLETE + "]");

        assertThat(batch.requests()).containsExactly(new WorkRequest(
                "REQ-1", "PUMP-7", Priority.HIGH, Instant.parse("2026-10-20T08:15:00Z"), "Pump leaking"));
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"high, HIGH", "' Critical ', CRITICAL", "low, LOW", "MEDIUM, MEDIUM"})
    void acceptsPriorityInAnyCase(String raw, Priority expected) {
        ParsedBatch batch = map(withField("priority", "\"" + raw + "\""));

        assertThat(batch.requests().get(0).priority()).isEqualTo(expected);
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @Test
    void reportsUnknownPriorityAsInvalid() {
        ParsedBatch batch = map(withField("priority", "\"URGENT\""));

        assertThat(batch.requests().get(0).priority()).isNull();
        assertThat(batch.invalidFieldsAt(0)).containsExactly(new InvalidField("priority", "URGENT"));
    }

    @Test
    void acceptsTimestampWithOffsetAndConvertsToUtc() {
        ParsedBatch batch = map(withField("requestedAt", "\"2026-10-20T13:45:00+05:30\""));

        assertThat(batch.requests().get(0).requestedAt()).isEqualTo(Instant.parse("2026-10-20T08:15:00Z"));
    }

    @ParameterizedTest(name = "requestedAt \"{0}\"")
    @ValueSource(strings = {"yesterday", "2026-10-20T08:15:00", "2026-13-01T00:00:00Z", "20/10/2026 08:15"})
    void reportsUnparseableDateAsInvalid(String raw) {
        ParsedBatch batch = map(withField("requestedAt", "\"" + raw + "\""));

        assertThat(batch.requests().get(0).requestedAt()).isNull();
        assertThat(batch.invalidFieldsAt(0)).containsExactly(new InvalidField("requestedAt", raw));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', value = {
            "requestId   | 42",
            "equipmentId | true",
            "priority    | 3",
            "requestedAt | 1729412100",
            "description | {\"text\":\"Pump leaking\"}",
            "priority    | [\"HIGH\"]"})
    void reportsWrongJsonTypesAsInvalid(String field, String json) {
        ParsedBatch batch = map(withField(field, json));

        assertThat(batch.invalidFieldsAt(0)).containsExactly(new InvalidField(field, json));
    }

    @Test
    void treatsNullAndAbsentFieldsAsMissingNotInvalid() {
        ParsedBatch batch = map("""
                [{"requestId": null, "priority": null, "requestedAt": null}]""");

        assertThat(batch.requests()).containsExactly(new WorkRequest(null, null, null, null, null));
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @Test
    void treatsBlankPriorityAndDateAsMissingNotInvalid() {
        ParsedBatch batch = map("""
                [{"requestId": "REQ-1", "priority": "  ", "requestedAt": ""}]""");

        assertThat(batch.requests().get(0).priority()).isNull();
        assertThat(batch.requests().get(0).requestedAt()).isNull();
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @Test
    void keepsBlankStringsForTheValidatorToReport() {
        ParsedBatch batch = map(withField("description", "\"   \""));

        assertThat(batch.requests().get(0).description()).isEqualTo("   ");
    }

    @Test
    void ignoresExtraFields() {
        ParsedBatch batch = map("[" + COMPLETE.replace("}", ", \"site\": \"North\", \"tags\": [1, 2]}") + "]");

        assertThat(batch.requests()).containsExactlyElementsOf(map("[" + COMPLETE + "]").requests());
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @Test
    void mapsNullAndNonObjectElementsToNullRequests() {
        ParsedBatch batch = map("[null, 42, \"REQ-1\", [], true]");

        assertThat(batch.requests()).hasSize(5).containsOnlyNulls();
        assertThat(batch.invalidFieldsByIndex()).isEmpty();
    }

    @Test
    void keepsEveryElementAtItsOriginalIndex() {
        ParsedBatch batch = map("[" + COMPLETE + ", 7, "
                + withFieldObject("priority", "\"URGENT\"") + ", null, "
                + COMPLETE.replace("REQ-1", "REQ-5") + "]");

        assertThat(batch.requests()).hasSize(5);
        assertThat(batch.requests().get(0).requestId()).isEqualTo("REQ-1");
        assertThat(batch.requests().get(1)).isNull();
        assertThat(batch.requests().get(2).requestId()).isEqualTo("REQ-1");
        assertThat(batch.requests().get(3)).isNull();
        assertThat(batch.requests().get(4).requestId()).isEqualTo("REQ-5");
        assertThat(batch.invalidFieldsByIndex()).containsOnlyKeys(2);
    }

    @Test
    void reportsEveryInvalidFieldOfOneRequest() {
        ParsedBatch batch = map("""
                [{"requestId": 7, "equipmentId": "PUMP-7", "priority": "URGENT",
                  "requestedAt": "soon", "description": "Pump leaking"}]""");

        assertThat(batch.invalidFieldsAt(0)).containsExactly(
                new InvalidField("requestId", "7"),
                new InvalidField("priority", "URGENT"),
                new InvalidField("requestedAt", "soon"));
    }

    private ParsedBatch map(String json) {
        try {
            return mapper.map((ArrayNode) JsonMapper.builder().build().readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad test JSON: " + json, e);
        }
    }

    /** A one-element batch: the complete request with one field replaced by raw JSON. */
    private static String withField(String field, String json) {
        return "[" + withFieldObject(field, json) + "]";
    }

    private static String withFieldObject(String field, String json) {
        return COMPLETE.replaceFirst("\"" + field + "\": (\"[^\"]*\")", "\"" + field + "\": " + Matcher.quoteReplacement(json));
    }
}
