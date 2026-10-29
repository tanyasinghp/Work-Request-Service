# implement-1: prompt

## System

You implement acceptance criteria in a Java 21 / Spring Boot codebase by returning a unified
diff that `git apply` will accept.

Diff rules:
- Use git-style headers: `diff --git a/<path> b/<path>`, `--- a/<path>`, `+++ b/<path>`, paths
  relative to the repository root (they start with `backend/src/`). New files use
  `new file mode 100644` and `--- /dev/null`.
- Every hunk's context and removed lines must match the files you were shown exactly,
  including indentation. Include at least three lines of context.
- Only change files under `backend/src/`. Never touch pom.xml, CI, Docker or wrapper files.
- Never delete, rename, disable or weaken tests. You may extend existing tests when a new
  behaviour legitimately requires it (for example a generator that must cover a new problem code).

Engineering rules: keep existing constructors and public methods working, keep output
independent of input order, keep problems as data, follow the conventions in the notes, and
change no more than the criteria need. Tests for the criteria are written separately; you
won't see them, so implement exactly the identifiers the criteria name.

Return JSON with: `diff` (the unified diff), `summary` (two or three sentences on what you
changed), and `uncertainties` (anything you had to guess).


## User

# Acceptance criteria

```json
{
  "title": "Flag future requestedAt as SUSPICIOUS_TIMESTAMP",
  "criteria": [
    {
      "given": "ProblemCode and Problem",
      "when": "a new problem is needed for future timestamps",
      "then": "ProblemCode has a new constant SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID) and Problem gains a static factory Problem.suspiciousTimestamp(int inputIndex, String requestId, Instant requestedAt) producing code SUSPICIOUS_TIMESTAMP, field \"requestedAt\", null relatedRequestId/escalatedFrom/escalatedTo, and message \"<requestId> has requestedAt <instant> in the future\" using Instant.toString(), or \"Request has requestedAt <instant> in the future\" when requestId is null"
    },
    {
      "given": "a WorkRequestValidator built with Clock.fixed(Instant.parse(\"2026-10-20T12:00:00Z\"), ZoneOffset.UTC)",
      "when": "validate is called on one otherwise-valid request with requestedAt 2026-10-21T08:30:00Z",
      "then": "exactly one problem is returned, with code SUSPICIOUS_TIMESTAMP, field \"requestedAt\", inputIndex 0 and the request's requestId, and the request is absent from StageResult.requests()"
    },
    {
      "given": "the same clock fixed at 2026-10-20T12:00:00Z",
      "when": "requests with requestedAt 2026-10-20T12:00:00Z (exactly equal) or 2026-10-20T11:59:59.999Z are validated",
      "then": "no SUSPICIOUS_TIMESTAMP problem is reported and both are accepted; only an instant strictly after the clock instant is future, with no tolerance window and no configuration property"
    },
    {
      "given": "a request whose requestedAt is null, or whose requestedAt is listed in invalidFieldsByIndex as an unreadable raw value",
      "when": "it is validated with a fixed clock",
      "then": "only MISSING_FIELD or INVALID_VALUE for field \"requestedAt\" is reported and no SUSPICIOUS_TIMESTAMP problem, since the check needs a parsed instant"
    },
    {
      "given": "a request WR-1 missing equipmentId and also dated in the future",
      "when": "it is validated",
      "then": "both problems are reported for that input index, the existing field-order problems first and SUSPICIOUS_TIMESTAMP last, and the validate trace note is the index-free \"WR-1 rejected: missing equipmentId, future requestedAt\" (label \"Request without id\" when the id is blank), with notes still sorted and out counting only accepted requests"
    },
    {
      "given": "a batch with identical content in a past-dated and a future-dated request, processed by WorkRequestProcessor with a fixed clock",
      "when": "process(batch) runs and the batch is shuffled repeatedly",
      "then": "the future-dated request is rejected in validate, never reaches deduplicate (no DUPLICATE problem or escalation for it), the trace still has exactly three entries validate/deduplicate/prioritize, and queue, problems and notes are identical for every ordering with SUSPICIOUS_TIMESTAMP problems ordered by inputIndex inside the validate block"
    },
    {
      "given": "existing callers of new WorkRequestValidator()",
      "when": "the clock is introduced",
      "then": "the no-arg constructor still compiles and behaves as if given Clock.systemUTC(), a new WorkRequestValidator(Clock clock) overload exists that throws NullPointerException with message \"clock\" on null, and clock.instant() is read once per validate call so all requests in a batch share one instant"
    },
    {
      "given": "the Spring context",
      "when": "it starts with no overriding bean",
      "then": "ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC() and builds the workRequestValidator bean with it"
    },
    {
      "given": "the pinned 13-request sample batch (all requestedAt on 2026-10-20) and the generated order-independence batch",
      "when": "they are processed after 2026-10-20 with the system clock",
      "then": "the sample batch produces no SUSPICIOUS_TIMESTAMP and keeps its pinned queue, problems and counts 13 \u2192 10 \u2192 6, while the generated batch is extended to include a future-dated request so every ProblemCode value, including SUSPICIOUS_TIMESTAMP, is still covered and BatchObserver reports one counter per code"
    }
  ],
  "affectedClasses": [
    "ProblemCode",
    "Problem",
    "WorkRequestValidator",
    "ProcessingConfig",
    "OrderIndependenceTest",
    "BatchObserver"
  ],
  "outOfScope": [
    "Any configurable clock-skew tolerance or app.* property for the future check",
    "Flagging implausibly old requestedAt values",
    "Making DuplicateResolver, WorkRequestSorter or WorkRequestProcessor clock-aware",
    "Changing the pinned sample-batch.json data or its expected output",
    "Changing the API DTO shape or adding a new HTTP endpoint/response field",
    "Changing existing ProblemCode constant order or existing problem messages"
  ]
}
```

# Codebase notes

# Work Request Service: what you need to know

Java 21, Spring Boot 3.5, Maven. Source in `backend/src/main/java/com/tanya/workrequests/`,
tests in `backend/src/test/java/com/tanya/workrequests/`. Packages: `domain`, `processing`,
`api` (+ `api.dto`), `config`.

## Domain (records and enums, immutable)

- `WorkRequest(requestId, equipmentId, Priority priority, Instant requestedAt, String description)`,
  with `withPriority(Priority)`.
- `Priority`: LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4); compare with `weight()`, never `ordinal()`.
- `ProblemCode`: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID.
- `Problem(inputIndex, requestId, code, field, relatedRequestId, escalatedFrom, escalatedTo, message)`,
  built through static factories (`Problem.missingField(...)`, `Problem.duplicate(...)`, ...).
  `escalatedFrom`/`escalatedTo` are set together or not at all. Messages name request ids but
  never the input index.
- `StageTrace(stage, in, out, notes)`; `ProcessingResult(accepted, problems, trace)`.

## Processing pipeline

`WorkRequestProcessor(validator, duplicateResolver, sorter)` runs three stages in order and
returns a `ProcessingResult` whose trace always has exactly three entries: `validate`,
`deduplicate`, `prioritize`.

- `WorkRequestValidator.validate(List<WorkRequest>)` and `validate(List, Map<Integer, List<InvalidField>>)`
  return a `StageResult(List<IndexedRequest> requests, List<Problem> problems, StageTrace trace)`.
- `DuplicateResolver(DescriptionNormalizer).resolve(List<IndexedRequest>)`: request-id conflicts,
  then duplicates (earliest kept, priority escalated to the group's highest).
- `WorkRequestSorter.sort(List<IndexedRequest>)`: priority weight desc, requestedAt asc, requestId asc.
- `IndexedRequest(inputIndex, request)` carries the original position through the stages.

Rules the tests pin down:

- Problems are data, never exceptions; one bad request never fails a batch.
- Output must be identical regardless of input order (tests shuffle batches 100 times). Trace
  notes are sorted text and contain no input indices. Problems are listed in stage order, then
  by input index within a stage.
- Validation runs before deduplication.

## Wiring and configuration

- `config/ProcessingConfig` (`@Configuration(proxyBeanMethods = false)`) creates every stage and
  the processor as `@Bean`s. Main code has no `@Component` annotations.
- Settings are `@ConfigurationProperties` records with `@DefaultValue`, e.g.
  `BatchProperties(@DefaultValue("5000") int maxSize)` bound from `app.batch.*` in
  `src/main/resources/application.yml`; the application class has `@ConfigurationPropertiesScan`.
- There is no `java.time.Clock` bean yet. Code that needs "now" should take a `Clock` rather than
  calling `Instant.now()` directly, so tests can use `Clock.fixed(...)`.

## Compatibility hazards

- Many existing tests call `new WorkRequestValidator()`, `new DuplicateResolver(new DescriptionNormalizer())`,
  `new WorkRequestSorter()` and `new WorkRequestProcessor(validator, resolver, sorter)` directly.
  Keep those constructors working (add overloads rather than changing signatures).
- `SampleBatchTest` pins the 13-request sample batch (`src/main/resources/sample-batch.json`,
  all requestedAt on 2026-10-20) to an exact queue, exact problems, and trace counts 13 → 10 → 6.
- `OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem` asserts that a generated batch
  produces every `ProblemCode` value, and `BatchObserverTest` expects one metrics counter per code.
- `EdgeCaseTest`, `WorkRequestControllerTest` and `ApiErrorHandlingTest` pin API and edge-case behaviour.

## Test conventions

JUnit 5, AssertJ, Mockito. Test names describe behaviour (`keepsEarliestDuplicateAndEscalatesPriority`).
In package `com.tanya.workrequests.processing` there is a package-private helper `TestRequests`:
`request(id, equipmentId, priority, "2026-10-20T08:00:00Z", description)`, `valid(id)`, and
`indexed(WorkRequest...)`. Test classes must end in `Test` to run.

## Code conventions

Records for immutable data, no Lombok, constructor injection, small single-purpose classes,
meaningful names, comments that explain why rather than what.


# All source files (paths only)

backend/src/main/java/com/tanya/workrequests/WorkRequestServiceApplication.java
backend/src/main/java/com/tanya/workrequests/api/ApiExceptionHandler.java
backend/src/main/java/com/tanya/workrequests/api/BatchObserver.java
backend/src/main/java/com/tanya/workrequests/api/InvalidBatchException.java
backend/src/main/java/com/tanya/workrequests/api/RequestMapper.java
backend/src/main/java/com/tanya/workrequests/api/SampleBatchSource.java
backend/src/main/java/com/tanya/workrequests/api/WorkRequestController.java
backend/src/main/java/com/tanya/workrequests/api/dto/ApiError.java
backend/src/main/java/com/tanya/workrequests/api/dto/BatchResponse.java
backend/src/main/java/com/tanya/workrequests/api/dto/ProblemDto.java
backend/src/main/java/com/tanya/workrequests/api/dto/StageTraceDto.java
backend/src/main/java/com/tanya/workrequests/api/dto/WorkRequestDto.java
backend/src/main/java/com/tanya/workrequests/config/ApiConfig.java
backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java
backend/src/main/java/com/tanya/workrequests/config/CorsConfig.java
backend/src/main/java/com/tanya/workrequests/config/CorsProperties.java
backend/src/main/java/com/tanya/workrequests/config/MetricsConfig.java
backend/src/main/java/com/tanya/workrequests/config/OpenApiConfig.java
backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
backend/src/main/java/com/tanya/workrequests/domain/InvalidField.java
backend/src/main/java/com/tanya/workrequests/domain/ParsedBatch.java
backend/src/main/java/com/tanya/workrequests/domain/Priority.java
backend/src/main/java/com/tanya/workrequests/domain/Problem.java
backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
backend/src/main/java/com/tanya/workrequests/domain/ProcessingResult.java
backend/src/main/java/com/tanya/workrequests/domain/StageTrace.java
backend/src/main/java/com/tanya/workrequests/domain/WorkRequest.java
backend/src/main/java/com/tanya/workrequests/processing/DescriptionNormalizer.java
backend/src/main/java/com/tanya/workrequests/processing/DuplicateResolver.java
backend/src/main/java/com/tanya/workrequests/processing/IndexedRequest.java
backend/src/main/java/com/tanya/workrequests/processing/StageResult.java
backend/src/main/java/com/tanya/workrequests/processing/WorkRequestProcessor.java
backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java
backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java
backend/src/test/java/com/tanya/workrequests/SmokeTest.java
backend/src/test/java/com/tanya/workrequests/api/ApiErrorHandlingTest.java
backend/src/test/java/com/tanya/workrequests/api/BatchObserverTest.java
backend/src/test/java/com/tanya/workrequests/api/CorsAndOpenApiTest.java
backend/src/test/java/com/tanya/workrequests/api/RequestMapperTest.java
backend/src/test/java/com/tanya/workrequests/api/SampleBatchSourceTest.java
backend/src/test/java/com/tanya/workrequests/api/UnexpectedErrorTest.java
backend/src/test/java/com/tanya/workrequests/api/WorkRequestControllerTest.java
backend/src/test/java/com/tanya/workrequests/config/ApplicationWiringTest.java
backend/src/test/java/com/tanya/workrequests/config/BatchPropertiesTest.java
backend/src/test/java/com/tanya/workrequests/config/CorsPropertiesTest.java
backend/src/test/java/com/tanya/workrequests/domain/PriorityTest.java
backend/src/test/java/com/tanya/workrequests/domain/ProblemTest.java
backend/src/test/java/com/tanya/workrequests/processing/DescriptionNormalizerTest.java
backend/src/test/java/com/tanya/workrequests/processing/DuplicateResolverTest.java
backend/src/test/java/com/tanya/workrequests/processing/EdgeCaseTest.java
backend/src/test/java/com/tanya/workrequests/processing/OrderIndependenceTest.java
backend/src/test/java/com/tanya/workrequests/processing/SampleBatch.java
backend/src/test/java/com/tanya/workrequests/processing/SampleBatchTest.java
backend/src/test/java/com/tanya/workrequests/processing/TestRequests.java
backend/src/test/java/com/tanya/workrequests/processing/WorkRequestProcessorTest.java
backend/src/test/java/com/tanya/workrequests/processing/WorkRequestSorterTest.java
backend/src/test/java/com/tanya/workrequests/processing/WorkRequestValidatorTest.java

# Files you may need to change (current contents)

=== backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java ===
```java
package com.tanya.workrequests.domain;

public enum ProblemCode {
    NULL_REQUEST,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE,
    CONFLICTING_REQUEST_ID
}
```

=== backend/src/main/java/com/tanya/workrequests/domain/Problem.java ===
```java
package com.tanya.workrequests.domain;

import java.util.Objects;

/**
 * Something wrong with one request in a batch. Problems are returned as data so that one
 * bad request never fails the whole batch.
 *
 * <p>{@code requestId}, {@code field}, {@code relatedRequestId}, {@code escalatedFrom}, and
 * {@code escalatedTo} are null when they don't apply. Messages never mention the input index,
 * so the same problem reads the same no matter where the request sat in the batch.
 *
 * <p>{@code escalatedFrom}/{@code escalatedTo} are set only on the duplicate whose priority
 * raised the kept request, so the UI can show escalations without parsing message text.
 */
public record Problem(int inputIndex,
                      String requestId,
                      ProblemCode code,
                      String field,
                      String relatedRequestId,
                      Priority escalatedFrom,
                      Priority escalatedTo,
                      String message) {

    // Long enough to recognise a bad value, short enough that a huge payload can't bloat the response.
    static final int MAX_VALUE_LENGTH = 40;

    public Problem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if ((escalatedFrom == null) != (escalatedTo == null)) {
            throw new IllegalArgumentException("escalatedFrom and escalatedTo must be set together");
        }
    }

    public static Problem nullRequest(int inputIndex) {
        return new Problem(inputIndex, null, ProblemCode.NULL_REQUEST, null, null, null, null,
                "Request is null");
    }

    public static Problem missingField(int inputIndex, String requestId, String field) {
        return new Problem(inputIndex, requestId, ProblemCode.MISSING_FIELD, field, null, null, null,
                subject(requestId) + " is missing " + field);
    }

    public static Problem invalidValue(int inputIndex, String requestId, String field, String rawValue) {
        return new Problem(inputIndex, requestId, ProblemCode.INVALID_VALUE, field, null, null, null,
                subject(requestId) + " has invalid " + field + " '" + truncate(rawValue) + "'");
    }

    public static Problem duplicate(int inputIndex, String requestId, String keptRequestId) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, null, null,
                "Duplicate of " + keptRequestId);
    }

    /** A byte-for-byte resend of a request that was kept; it points at itself. */
    public static Problem exactRepeat(int inputIndex, String requestId) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, requestId, null, null,
                "Exact repeat of " + requestId);
    }

    public static Problem duplicateWithEscalation(int inputIndex, String requestId, String keptRequestId,
                                                  Priority from, Priority to) {
        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, from, to,
                "Duplicate of " + keptRequestId + "; priority escalated " + from + " to " + to);
    }

    public static Problem conflictingRequestId(int inputIndex, String requestId) {
        return new Problem(inputIndex, requestId, ProblemCode.CONFLICTING_REQUEST_ID, null, null, null, null,
                "Request id " + requestId + " is used by requests with different content");
    }

    private static String subject(String requestId) {
        return requestId == null ? "Request" : requestId;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
```

=== backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java ===
```java
package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rejects null, incomplete, and unreadable requests. Every missing or invalid field is its
 * own problem, so a request with two bad fields reports both. Blank strings count as missing.
 */
public class WorkRequestValidator {

    static final String STAGE = "validate";

    public StageResult validate(List<WorkRequest> batch) {
        return validate(batch, Map.of());
    }

    /**
     * @param invalidFieldsByIndex fields that were present in the raw input but unreadable;
     *        they are null in {@code batch} and are reported as INVALID_VALUE, not MISSING_FIELD
     */
    public StageResult validate(List<WorkRequest> batch, Map<Integer, List<InvalidField>> invalidFieldsByIndex) {
        List<IndexedRequest> valid = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (int index = 0; index < batch.size(); index++) {
            WorkRequest request = batch.get(index);
            List<InvalidField> invalidFields = invalidFieldsByIndex.getOrDefault(index, List.of());
            List<Problem> requestProblems = problemsFor(index, request, invalidFields);
            if (requestProblems.isEmpty()) {
                valid.add(new IndexedRequest(index, request));
            } else {
                problems.addAll(requestProblems);
                notes.add(rejectionNote(request, requestProblems));
            }
        }
        // Sorted, and free of input indices, so the trace is identical whatever the input order.
        notes.sort(Comparator.naturalOrder());

        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
        return new StageResult(valid, problems, trace);
    }

    private static List<Problem> problemsFor(int index, WorkRequest request, List<InvalidField> invalidFields) {
        if (request == null) {
            return List.of(Problem.nullRequest(index));
        }
        Map<String, String> invalid = invalidFields.stream()
                .collect(Collectors.toMap(InvalidField::field, InvalidField::rawValue, (first, second) -> first));
        // A blank id is reported as no id at all, matching "blank counts as missing".
        String requestId = isBlank(request.requestId()) ? null : request.requestId();

        List<Problem> problems = new ArrayList<>();
        check(problems, index, requestId, "requestId", requestId == null, invalid);
        check(problems, index, requestId, "equipmentId", isBlank(request.equipmentId()), invalid);
        check(problems, index, requestId, "priority", request.priority() == null, invalid);
        check(problems, index, requestId, "requestedAt", request.requestedAt() == null, invalid);
        check(problems, index, requestId, "description", isBlank(request.description()), invalid);
        return problems;
    }

    private static void check(List<Problem> problems, int index, String requestId, String field,
                              boolean missing, Map<String, String> invalid) {
        if (invalid.containsKey(field)) {
            problems.add(Problem.invalidValue(index, requestId, field, invalid.get(field)));
        } else if (missing) {
            problems.add(Problem.missingField(index, requestId, field));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rejectionNote(WorkRequest request, List<Problem> problems) {
        if (request == null) {
            return "Null request rejected";
        }
        Problem first = problems.get(0);
        String label = first.requestId() != null ? first.requestId() : "Request without id";
        String reasons = problems.stream()
                .map(problem -> (problem.code() == ProblemCode.INVALID_VALUE ? "invalid " : "missing ") + problem.field())
                .collect(Collectors.joining(", "));
        return label + " rejected: " + reasons;
    }
}
```

=== backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java ===
```java
package com.tanya.workrequests.config;

import com.tanya.workrequests.processing.DescriptionNormalizer;
import com.tanya.workrequests.processing.DuplicateResolver;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import com.tanya.workrequests.processing.WorkRequestSorter;
import com.tanya.workrequests.processing.WorkRequestValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free processing classes. Every stage is stateless, so one shared
 * instance of each (Spring's default singleton scope) is safe across concurrent requests.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessingConfig {

    @Bean
    DescriptionNormalizer descriptionNormalizer() {
        return new DescriptionNormalizer();
    }

    @Bean
    WorkRequestValidator workRequestValidator() {
        return new WorkRequestValidator();
    }

    @Bean
    DuplicateResolver duplicateResolver(DescriptionNormalizer descriptionNormalizer) {
        return new DuplicateResolver(descriptionNormalizer);
    }

    @Bean
    WorkRequestSorter workRequestSorter() {
        return new WorkRequestSorter();
    }

    @Bean
    WorkRequestProcessor workRequestProcessor(WorkRequestValidator validator,
                                              DuplicateResolver duplicateResolver,
                                              WorkRequestSorter sorter) {
        return new WorkRequestProcessor(validator, duplicateResolver, sorter);
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/processing/OrderIndependenceTest.java ===
```java
package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same batch in any order must give the same queue, problems, and trace. Problems are
 * compared without inputIndex, because the index is where the request sat in this ordering.
 */
class OrderIndependenceTest {

    private static final long SHUFFLE_SEED = 42;
    private static final long GENERATOR_SEED = 2026;
    private static final Instant START = Instant.parse("2026-10-20T00:00:00Z");
    private static final Priority[] PRIORITIES = Priority.values();

    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    @Test
    void producesIdenticalOutputAcross100Shuffles() {
        assertSameOutputAcrossShuffles(SampleBatch.load(), 100, batch -> processor.process(batch));
    }

    @Test
    void producesIdenticalOutputAcross50ShufflesOfALargeGeneratedBatch() {
        assertSameOutputAcrossShuffles(generatedBatch(), 50, this::processElements);
    }

    @Test
    void generatedBatchCoversEveryKindOfProblem() {
        List<Element> batch = generatedBatch();

        ProcessingResult result = processElements(batch);

        assertThat(batch).hasSize(200);
        assertThat(result.problems()).extracting(Problem::code).contains(ProblemCode.values());
        assertThat(result.problems()).extracting(Problem::message)
                .anyMatch(message -> message.contains("priority escalated"))
                .anyMatch(message -> message.startsWith("Exact repeat of"));
        assertThat(result.trace().get(1).notes())
                .anyMatch(note -> note.contains("merged duplicates") && note.contains(", "));
    }

    private <T> void assertSameOutputAcrossShuffles(List<T> batch, int shuffles,
                                                    Function<List<T>, ProcessingResult> process) {
        ProcessingResult expected = process.apply(batch);
        Random random = new Random(SHUFFLE_SEED);

        for (int run = 1; run <= shuffles; run++) {
            List<T> shuffled = new ArrayList<>(batch);
            Collections.shuffle(shuffled, random);

            ProcessingResult actual = process.apply(shuffled);

            assertThat(actual.accepted()).as("accepted, shuffle %d", run).isEqualTo(expected.accepted());
            assertThat(actual.trace()).as("trace, shuffle %d", run).isEqualTo(expected.trace());
            assertThat(withoutIndex(actual.problems())).as("problems, shuffle %d", run)
                    .isEqualTo(withoutIndex(expected.problems()));
        }
    }

    private static List<Problem> withoutIndex(List<Problem> problems) {
        return problems.stream()
                .map(problem -> new Problem(0, problem.requestId(), problem.code(), problem.field(),
                        problem.relatedRequestId(), problem.escalatedFrom(), problem.escalatedTo(),
                        problem.message()))
                .sorted(Comparator.comparing(Problem::toString))
                .toList();
    }

    /**
     * 200 requests built from a fixed seed: 100 distinct, 40 duplicates (some three-way, some
     * tied on time), 15 exact repeats, 23 in conflict groups, 17 invalid (including unreadable
     * priority and date values), 5 null. Times sit on a 15-minute grid so priority and time
     * ties are common.
     */
    private static List<Element> generatedBatch() {
        Random random = new Random(GENERATOR_SEED);
        List<WorkRequest> batch = new ArrayList<>();

        List<WorkRequest> base = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            base.add(new WorkRequest(String.format("REQ-%03d", i), "EQ-" + i, randomPriority(random),
                    slot(random.nextInt(48)), "Task " + i));
        }
        batch.addAll(base);

        // Duplicates with case/space variants; a zero offset ties on time with the original.
        for (int i = 0; i < 40; i++) {
            WorkRequest original = base.get(i % 30);
            batch.add(new WorkRequest(String.format("DUP-%03d", i), "  eq-" + (i % 30) + " ",
                    randomPriority(random),
                    original.requestedAt().plus(Duration.ofMinutes(15L * random.nextInt(4))),
                    "  TASK   " + (i % 30) + " "));
        }

        // Exact repeats: 0-4 sit in duplicate groups (some escalated), 30-39 are standalone.
        for (int i = 0; i < 5; i++) {
            batch.add(base.get(i));
        }
        for (int i = 30; i < 40; i++) {
            batch.add(base.get(i));
        }

        // Conflict groups; CON-000/001 would also be earlier, Critical duplicates of 50/51.
        for (int i = 0; i < 10; i++) {
            String id = String.format("CON-%03d", i);
            WorkRequest first = i < 2
                    ? new WorkRequest(id, "EQ-" + (50 + i), Priority.CRITICAL,
                            base.get(50 + i).requestedAt().minus(Duration.ofHours(1)), "Task " + (50 + i))
                    : new WorkRequest(id, "CEQ-" + i, randomPriority(random), slot(random.nextInt(48)), "Conflict A " + i);
            batch.add(first);
            batch.add(new WorkRequest(id, "CEQ-" + i, randomPriority(random), slot(random.nextInt(48)), "Conflict B " + i));
            if (i >= 7) {
                batch.add(first);
            }
        }

        // Invalid requests; 60-64 are earlier, Critical look-alikes of base requests.
        for (int i = 0; i < 5; i++) {
            WorkRequest target = base.get(60 + i);
            batch.add(new WorkRequest(null, target.equipmentId(), Priority.CRITICAL,
                    target.requestedAt().minus(Duration.ofHours(1)), target.description()));
        }
        // Unreadable values (as the API mapper reports them) sit at the same index as their request.
        Map<Integer, List<InvalidField>> invalidFields = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            String id = String.format("BAD-%03d", i);
            switch (i % 6) {
                case 4 -> invalidFields.put(batch.size(), List.of(new InvalidField("priority", "URGENT")));
                case 5 -> invalidFields.put(batch.size(), List.of(new InvalidField("requestedAt", "yesterday")));
                default -> { }
            }
            batch.add(switch (i % 6) {
                case 0 -> new WorkRequest(id, "  ", randomPriority(random), slot(i), "Bad " + i);
                case 1 -> new WorkRequest(id, "BEQ-" + i, null, slot(i), "Bad " + i);
                case 2 -> new WorkRequest(id, "BEQ-" + i, randomPriority(random), null, "Bad " + i);
                case 3 -> new WorkRequest(id, null, randomPriority(random), slot(i), null);
                case 4 -> new WorkRequest(id, "BEQ-" + i, null, slot(i), "Bad " + i);
                default -> new WorkRequest(id, "BEQ-" + i, randomPriority(random), null, "Bad " + i);
            });
        }

        for (int i = 0; i < 5; i++) {
            batch.add(null);
        }
        return IntStream.range(0, batch.size())
                .mapToObj(index -> new Element(batch.get(index), invalidFields.getOrDefault(index, List.of())))
                .toList();
    }

    /** One input element and the fields the mapper found unreadable, so a shuffle moves both together. */
    private record Element(WorkRequest request, List<InvalidField> invalidFields) {
    }

    private ProcessingResult processElements(List<Element> elements) {
        Map<Integer, List<InvalidField>> invalidFields = new HashMap<>();
        for (int index = 0; index < elements.size(); index++) {
            if (!elements.get(index).invalidFields().isEmpty()) {
                invalidFields.put(index, elements.get(index).invalidFields());
            }
        }
        List<WorkRequest> requests = elements.stream().map(Element::request).toList();
        return processor.process(new ParsedBatch(requests, invalidFields));
    }

    private static Priority randomPriority(Random random) {
        return PRIORITIES[random.nextInt(PRIORITIES.length)];
    }

    private static Instant slot(int index) {
        return START.plus(Duration.ofMinutes(15L * index));
    }
}
```

=== backend/src/main/java/com/tanya/workrequests/api/BatchObserver.java ===
```java
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
```

# Classes named in the criteria that don't exist yet (create them)

(none)



## Output schema

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": [
    "diff",
    "summary",
    "uncertainties"
  ],
  "properties": {
    "diff": {
      "type": "string",
      "description": "Unified diff, git style, paths under backend/src/."
    },
    "summary": {
      "type": "string"
    },
    "uncertainties": {
      "type": "array",
      "items": {
        "type": "string"
      }
    }
  }
}
```
