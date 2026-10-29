# implement-2: prompt

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
  "title": "Flag requests older than 30 days as stale",
  "criteria": [
    {
      "given": "the ProblemCode enum",
      "when": "its values are listed",
      "then": "a sixth constant STALE exists, appended last so existing constants keep their order: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID, STALE"
    },
    {
      "given": "the new static factory Problem.stale(int inputIndex, String requestId, Instant requestedAt, Duration maxAge)",
      "when": "called with 3, \"R-7\", 2026-09-01T08:00:00Z and Duration.ofDays(30)",
      "then": "it returns a Problem with inputIndex 3, requestId \"R-7\", code STALE, field \"requestedAt\", null relatedRequestId/escalatedFrom/escalatedTo and message exactly \"R-7 is stale: requestedAt 2026-09-01T08:00:00Z is more than 30 days old\" (day count from maxAge.toDays(); no input index in the message)"
    },
    {
      "given": "a validator built with the new constructor WorkRequestValidator(Clock clock, Duration maxAge) using Clock.fixed(2026-10-20T08:00:00Z, UTC) and Duration.ofDays(30)",
      "when": "validate is called with otherwise valid R-1 requestedAt 2026-09-01T08:00:00Z and R-2 requestedAt 2026-10-19T08:00:00Z",
      "then": "StageResult.requests holds only R-2 and problems hold exactly one STALE problem for R-1 at its input index: a stale request is both reported as a problem and kept out of the queue"
    },
    {
      "given": "the same validator (fixed clock, 30 days), with clock.instant() read once per validate call so all requests share one \"now\"",
      "when": "validate is called with requests requestedAt 2026-09-20T08:00:00Z (exactly 30 days), 2026-09-20T07:59:59Z (one second older) and 2026-11-01T08:00:00Z (future)",
      "then": "only the one-second-older request is STALE; staleness is strictly requestedAt.isBefore(now.minus(maxAge)), and future dates are accepted with no problem"
    },
    {
      "given": "a validator with a fixed clock and 30 day max age",
      "when": "validate is called with (a) a null element, (b) a 90-day-old request also missing equipmentId, (c) a 90-day-old request whose requestedAt is reported unreadable via invalidFieldsByIndex",
      "then": "(a) yields only NULL_REQUEST, (b) only the MISSING_FIELD problem for equipmentId, (c) only the INVALID_VALUE problem for requestedAt \u2014 field checks run first and a request with any field problem is never checked for staleness, so a STALE problem always has a non-null requestId"
    },
    {
      "given": "a validator with Clock.fixed(2026-10-20T08:00:00Z, UTC) and 30 days",
      "when": "validate is called with 3 requests of which R-7 is 90 days old and R-3 is missing description",
      "then": "the StageTrace is stage \"validate\", in 3, out 1, notes exactly [\"R-3 rejected: missing description\", \"R-7 rejected: stale\"]: one naturally sorted note per rejected request, stale notes read \"<requestId> rejected: stale\" and contain no input index"
    },
    {
      "given": "the preserved no-argument constructor new WorkRequestValidator() and the other existing constructors of DuplicateResolver, WorkRequestSorter and WorkRequestProcessor",
      "when": "a batch of years-old but otherwise valid requests is validated through new WorkRequestValidator()",
      "then": "no STALE problem is produced and every request is accepted \u2014 the no-arg constructor performs no staleness check, so existing tests including SampleBatchTest's 13 \u2192 10 \u2192 6 counts are unaffected; new WorkRequestValidator(null, Duration.ofDays(30)) and new WorkRequestValidator(Clock.systemUTC(), null) throw NullPointerException (\"clock\", \"maxAge\") and a maxAge under one day throws IllegalArgumentException"
    },
    {
      "given": "a new @ConfigurationProperties(\"app.staleness\") record StalenessProperties(@DefaultValue(\"30d\") Duration maxAge) with the default also written in application.yml",
      "when": "the context starts with no app.staleness property, then with app.staleness.max-age=7d, then with app.staleness.max-age=0d",
      "then": "maxAge binds to Duration.ofDays(30), then Duration.ofDays(7); 0d or any value under one day fails startup with IllegalArgumentException containing \"app.staleness.max-age must be at least 1 day\""
    },
    {
      "given": "ProcessingConfig",
      "when": "the Spring context starts",
      "then": "it exposes exactly one java.time.Clock bean named \"clock\" defaulting to Clock.systemUTC() (replaceable by Clock.fixed(...) in tests) and builds the WorkRequestValidator bean from that Clock and StalenessProperties.maxAge, so a batch posted to the API containing a request requestedAt more than 30 days before the clock returns a ProblemDto with code \"STALE\" and field \"requestedAt\" and omits that request from the queue"
    },
    {
      "given": "a processor built from a staleness-aware validator (fixed clock, 30 days), the real DuplicateResolver and WorkRequestSorter, and a batch where recent MEDIUM R-1 and 90-day-old CRITICAL R-2 share equipmentId and normalized description, alongside invalid requests and duplicates",
      "when": "the batch is processed 100 times in shuffled order",
      "then": "every run gives the identical queue, problems and trace: R-2 produces only a STALE problem with no DUPLICATE problem, R-1 stays in the queue at MEDIUM with no escalation, the trace still has exactly three entries validate/deduplicate/prioritize with validate out == deduplicate in, validate-stage problems (stale and field problems interleaved by input index) precede deduplicate-stage problems, and BatchObserver has a counter for STALE following the existing per-ProblemCode naming scheme that increments once per stale problem"
    }
  ],
  "affectedClasses": [
    "ProblemCode",
    "Problem",
    "WorkRequestValidator",
    "StalenessProperties",
    "ProcessingConfig",
    "BatchObserver"
  ],
  "outOfScope": [
    "Changing sample-batch.json or the pinned SampleBatchTest expectations",
    "Flagging requests whose requestedAt is in the future, or any clock-skew tolerance",
    "Marking stale requests as a non-blocking warning that still enters the queue",
    "A per-priority or per-equipment staleness threshold",
    "Re-checking staleness in the deduplicate or prioritize stages, or a new API field/endpoint for stale counts",
    "Replacing Instant.now() usages elsewhere in the codebase with the new Clock bean"
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

- StalenessProperties

# Previous attempt failed

Attempt 1 did not get through. Your earlier diff is already committed on this branch
unless the failure says it was rejected or did not apply; the files above show the current
state. Return a new diff against the files exactly as shown above.

## What failed

```
Rejected before the gate:
no file changes found in the diff (missing 'diff --git' or '---/+++' headers)
```

## Your previous diff

```diff
placeholder
```

## Other files the failure points at (current contents)

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
