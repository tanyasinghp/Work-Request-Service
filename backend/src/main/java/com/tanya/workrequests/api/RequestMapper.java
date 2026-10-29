package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.WorkRequest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns raw JSON elements into work requests without ever failing. It converts values and
 * notes the ones it couldn't read; deciding what that means is the validator's job.
 *
 * <p>Every element maps to exactly one entry, so input indices never shift: a JSON null or
 * any non-object element becomes a null request. A field that is present but unreadable
 * (wrong JSON type, unknown priority, unparseable date) becomes null and is recorded as an
 * {@link InvalidField}. Absent, JSON-null, and blank values are left for the validator to
 * report as missing. Extra fields are ignored.
 */
public class RequestMapper {

    public ParsedBatch map(ArrayNode elements) {
        List<WorkRequest> requests = new ArrayList<>(elements.size());
        Map<Integer, List<InvalidField>> invalidFieldsByIndex = new HashMap<>();

        for (int index = 0; index < elements.size(); index++) {
            JsonNode element = elements.get(index);
            if (!element.isObject()) {
                requests.add(null);
                continue;
            }
            List<InvalidField> invalidFields = new ArrayList<>();
            requests.add(new WorkRequest(
                    text(element, "requestId", invalidFields),
                    text(element, "equipmentId", invalidFields),
                    priority(element, invalidFields),
                    requestedAt(element, invalidFields),
                    text(element, "description", invalidFields)));
            if (!invalidFields.isEmpty()) {
                invalidFieldsByIndex.put(index, invalidFields);
            }
        }
        return new ParsedBatch(requests, invalidFieldsByIndex);
    }

    // Strings must be JSON strings: 42 or true for an id is reported, never silently coerced.
    private static String text(JsonNode element, String field, List<InvalidField> invalidFields) {
        JsonNode value = element.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        invalidFields.add(new InvalidField(field, value.toString()));
        return null;
    }

    // Case-insensitive so "High" and "high" work; numeric weights are not accepted.
    private static Priority priority(JsonNode element, List<InvalidField> invalidFields) {
        String value = text(element, "priority", invalidFields);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownPriority) {
            invalidFields.add(new InvalidField("priority", value));
            return null;
        }
    }

    // ISO-8601 with a zone or offset; a local time without one is ambiguous across sites.
    private static Instant requestedAt(JsonNode element, List<InvalidField> invalidFields) {
        String value = text(element, "requestedAt", invalidFields);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException unparseable) {
            invalidFields.add(new InvalidField("requestedAt", value));
            return null;
        }
    }
}
