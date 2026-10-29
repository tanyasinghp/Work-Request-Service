# enrich-retry: prompt

## System

You turn a plain-English user story for a Java service into acceptance criteria that two
people could implement and test independently and still agree.

Return only JSON matching the given schema. Each criterion is a concrete, testable
given/when/then. Name the exact identifiers the change introduces (enum constants, method or
factory names, configuration property keys and their defaults, problem messages where they
matter), because the implementation and the tests are written separately from these criteria
alone. State every behavioural decision the story leaves open. List in affectedClasses the
simple names of the classes that must change or be created, including wiring/config classes.
Keep the scope to what the story asks; put tempting extras in outOfScope.


## User

# Story

# Flag stale requests

As a maintenance supervisor, I want requests older than 30 days to be flagged as stale in
the problems list, so that old reports that may no longer be relevant get reviewed instead
of silently joining the queue.

"Older than 30 days" is measured against the time the batch is processed. A stale request
is reported as a problem; whether it is also kept out of the queue is a decision the
acceptance criteria must state explicitly.

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


# Source files in the backend

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

# Key existing files

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

=== backend/src/main/java/com/tanya/workrequests/domain/WorkRequest.java ===
```java
package com.tanya.workrequests.domain;

import java.time.Instant;

/**
 * A maintenance work request as received. Fields may be null or blank here on purpose:
 * the validator reports those as problems instead of the constructor throwing.
 */
public record WorkRequest(String requestId,
                          String equipmentId,
                          Priority priority,
                          Instant requestedAt,
                          String description) {

    public WorkRequest withPriority(Priority newPriority) {
        return new WorkRequest(requestId, equipmentId, newPriority, requestedAt, description);
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

=== backend/src/main/java/com/tanya/workrequests/processing/WorkRequestProcessor.java ===
```java
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

=== backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java ===
```java
package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.batch.*}: guards that protect the service from oversized input. */
@ConfigurationProperties("app.batch")
public record BatchProperties(@DefaultValue("5000") int maxSize) {

    public BatchProperties {
        if (maxSize < 1) {
            throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
        }
    }
}
```


# Your previous response was rejected

- criteria: [{'given': 'the ProblemCode enum', 'when': 'its values are listed', 'then': 'a new constant STALE exists, appended last so the existing constants keep their order and ordinals: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID, STALE (values().length == 6)'}, {'given': 'the static factory Problem.stale(int inputIndex, String requestId, Instant requestedAt, Duration maxAge)', 'when': 'called with inputIndex 3, requestId "R-7", requestedAt 2026-09-01T08:00:00Z and Duration.ofDays(30)', 'then': 'it returns a Problem with inputIndex 3, requestId "R-7", code STALE, field "requestedAt", relatedRequestId null, escalatedFrom null, escalatedTo null and message exactly "R-7 is stale: requestedAt 2026-09-01T08:00:00Z is more than 30 days old" (the day count is maxAge.toDays(); the message names the request id and never the input index)'}, {'given': 'a WorkRequestValidator built with the new constructor WorkRequestValidator(Clock clock, Duration maxAge), using Clock.fixed(2026-10-20T08:00:00Z, UTC) and Duration.ofDays(30)', 'when': 'validate is called with one otherwise valid request R-1 requestedAt 2026-09-01T08:00:00Z and one valid request R-2 requestedAt 2026-10-19T08:00:00Z', 'then': 'the StageResult requests contain only IndexedRequest for R-2, and problems contain exactly one Problem with code STALE for R-1 at its input index; i.e. a stale request is reported as a problem AND kept out of the queue'}, {'given': 'a validator with Clock.fixed(2026-10-20T08:00:00Z, UTC) and Duration.ofDays(30)', 'when': 'validate is called with a request requestedAt exactly 2026-09-20T08:00:00Z (exactly 30 days old) and another requestedAt 2026-09-20T07:59:59Z (one second over)', 'then': 'the exactly-30-days request is accepted with no problem and the one-second-older request is STALE: staleness is strictly older than maxAge, i.e. requestedAt.isBefore(now.minus(maxAge))'}, {'given': 'a validator with a fixed clock and 30 day max age', 'when': 'validate is called with an otherwise valid request whose requestedAt is in the future relative to the clock', 'then': 'the request is accepted into the StageResult requests with no problem of any code (future dates are not a staleness concern)'}, {'given': 'a validator with a fixed clock and 30 day max age', 'when': 'validate is called with (a) a null element, (b) a request 90 days old that is also missing equipmentId, and (c) a request 90 days old whose requestedAt was unreadable in the raw input and is therefore reported through invalidFieldsByIndex', 'then': '(a) yields only NULL_REQUEST, (b) yields only the MISSING_FIELD problem for equipmentId and no STALE problem, (c) yields only the INVALID_VALUE problem for requestedAt and no STALE problem: field checks run first and a request that already has field problems is never additionally checked for staleness, so a STALE problem always carries a non-null requestId'}, {'given': 'a validator with Clock.fixed(2026-10-20T08:00:00Z, UTC) and Duration.ofDays(30)', 'when': 'validate is called with 3 requests of which R-7 is 90 days old and R-3 is missing description', 'then': 'the returned StageTrace has stage "validate", in 3, out 1, and notes exactly ["R-3 rejected: missing description", "R-7 rejected: stale"] — one note per rejected request, stale notes read "<requestId> rejected: stale", notes stay sorted naturally and contain no input index'}, {'given': 'a validator built with the preserved no-argument constructor new WorkRequestValidator()', 'when': 'validate is called with requests dated years in the past', 'then': "no STALE problem is produced and every otherwise valid request is accepted: the no-arg constructor performs no staleness check at all, so existing behaviour (including SampleBatchTest's 13 → 10 → 6 counts for the unchanged sample-batch.json) is untouched"}, {'given': 'the new validator constructor', 'when': 'called as new WorkRequestValidator(null, Duration.ofDays(30)), new WorkRequestValidator(Clock.systemUTC(), null) or new WorkRequestValidator(Clock.systemUTC(), Duration.ofHours(12))', 'then': 'the first two throw NullPointerException (messages "clock" and "maxAge") and the third throws IllegalArgumentException because maxAge must be at least one day'}, {'given': 'a validator with a fixed clock and 30 day max age', 'when': 'validate is called with two requests whose requestedAt are both exactly 30 days and 1 second before that clock instant', 'then': 'both are reported STALE; the clock is read once per validate call (a single clock.instant()) so every request in a batch is compared against the same now'}, {'given': 'a WorkRequestProcessor built with a staleness-aware validator (fixed clock, 30 days), the real DuplicateResolver and WorkRequestSorter', 'when': 'process runs on a batch where R-1 (recent, MEDIUM) and R-2 (90 days old, CRITICAL) have the same equipmentId and the same normalized description', 'then': 'the result problems contain exactly one problem for R-2 with code STALE and no DUPLICATE problem, R-1 stays in the queue with priority MEDIUM (no escalation from the stale request), and the trace still has exactly three entries validate/deduplicate/prioritize with validate out == deduplicate in: stale requests are removed before deduplication and never influence later stages'}, {'given': 'a batch containing stale requests, invalid requests and duplicates', 'when': 'the same batch is processed 100 times in shuffled order through a staleness-aware processor with a fixed clock', 'then': 'the accepted queue, the problem list and the trace notes are identical every time; within the validate stage problems are ordered by input index with stale and field problems interleaved in that single index order, and all validate-stage problems precede deduplicate-stage problems'}, {'given': 'a new @ConfigurationProperties("app.staleness") record StalenessProperties(@DefaultValue("30d") Duration maxAge)', 'when': 'the context starts with no app.staleness.* property set, then with app.staleness.max-age=7d, then with app.staleness.max-age=0d', 'then': 'maxAge binds to Duration.ofDays(30), then Duration.ofDays(7), and 0d (or any value under one day, or a negative value) fails startup because the record constructor throws IllegalArgumentException containing "app.staleness.max-age must be at least 1 day"; src/main/resources/application.yml states the 30d default explicitly under app.staleness.max-age'}, {'given': 'the Spring context (ProcessingConfig)', 'when': 'it is started', 'then': 'it contains exactly one java.time.Clock bean named "clock" whose default value is Clock.systemUTC() and which a test configuration can replace with Clock.fixed(...), and the WorkRequestValidator bean is created with that Clock and StalenessProperties.maxAge, so the wired pipeline does flag stale requests; the existing no-arg and multi-arg constructors of WorkRequestValidator, DuplicateResolver, WorkRequestSorter and WorkRequestProcessor remain usable unchanged'}, {'given': 'the running API with a Clock fixed at 2026-10-20T12:00:00Z and the default 30 day max age', 'when': 'a batch containing a request requestedAt 2026-08-01T08:00:00Z is posted, and separately the unchanged 13-request sample batch (all requestedAt 2026-10-20) is processed', 'then': 'the response for the first batch contains a ProblemDto with code "STALE", field "requestedAt" and the stale message, and the request is absent from the returned queue; the sample batch produces no STALE problem and keeps its pinned queue, problems and 13 → 10 → 6 trace counts'}, {'given': 'BatchObserver and its metrics registration', 'when': 'a batch containing a stale request is observed', 'then': 'a counter exists for STALE following the same naming scheme as the other ProblemCode counters and is incremented once per stale problem; counters are derived from ProblemCode.values() so the set stays one counter per code (now six)'}] is too long

Return JSON that matches the schema exactly.

## Output schema

```json
{
  "title": "Acceptance criteria for one user story",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "title",
    "criteria",
    "affectedClasses",
    "outOfScope"
  ],
  "properties": {
    "title": {
      "type": "string",
      "description": "Short imperative title, e.g. 'Flag requests older than 30 days as stale'."
    },
    "criteria": {
      "type": "array",
      "description": "Testable criteria. Name concrete identifiers (enum constants, methods, properties) so the implementation and the independently written tests agree.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": [
          "given",
          "when",
          "then"
        ],
        "properties": {
          "given": {
            "type": "string"
          },
          "when": {
            "type": "string"
          },
          "then": {
            "type": "string"
          }
        }
      }
    },
    "affectedClasses": {
      "type": "array",
      "description": "Simple names of the Java classes to change or create, e.g. 'WorkRequestValidator'.",
      "items": {
        "type": "string"
      }
    },
    "outOfScope": {
      "type": "array",
      "description": "Things this story deliberately does not do.",
      "items": {
        "type": "string"
      }
    }
  }
}
```
