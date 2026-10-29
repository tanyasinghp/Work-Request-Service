# write-tests: prompt

## System

You write JUnit 5 tests for acceptance criteria, before seeing any implementation. Your tests
are the specification check: they must fail if a criterion is not met and must not depend on
how it is implemented beyond the identifiers the criteria name.

Return a unified diff that only adds new test files under `backend/src/test/java/` (git-style
headers, `new file mode 100644`, `--- /dev/null`, paths starting with `backend/src/test/`).
Do not modify existing files. Use AssertJ, one behaviour per test, names that describe
behaviour, and fixed clocks or explicit inputs instead of the real time. Place tests in the
package of the code they test; to use the `TestRequests` helper, the test must be in
`com.tanya.workrequests.processing`.

Return JSON with: `diff` and `summary` (what the tests cover, criterion by criterion).


## User

# Acceptance criteria

```json
{
  "title": "Flag requests older than 30 days as STALE and keep them out of the queue",
  "criteria": [
    {
      "given": "the ProblemCode enum",
      "when": "the story is implemented",
      "then": "a new constant STALE exists, declared last (after CONFLICTING_REQUEST_ID), so ProblemCode.values() is NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID, STALE"
    },
    {
      "given": "the Problem record",
      "when": "Problem.stale(7, \"WR-100\", 45L) is called (signature: static Problem stale(int inputIndex, String requestId, long ageInDays))",
      "then": "it returns a Problem with inputIndex 7, requestId \"WR-100\", code STALE, field \"requestedAt\", relatedRequestId null, escalatedFrom null, escalatedTo null, and message exactly \"WR-100 is stale: requested 45 days before processing\" (no input index in the message)"
    },
    {
      "given": "a WorkRequestValidator built with new WorkRequestValidator(Clock.fixed(Instant.parse(\"2026-10-29T00:00:00Z\"), ZoneOffset.UTC), 30) and a batch of one otherwise-valid request whose requestedAt is \"2026-09-01T00:00:00Z\" (58 days earlier)",
      "when": "validate(batch) is called",
      "then": "the StageResult requests list is empty, problems contains exactly one Problem equal to Problem.stale(0, thatRequestId, 58L), and the trace is new StageTrace(\"validate\", 1, 0, List.of(\"<requestId> rejected: stale (58 days old)\"))"
    },
    {
      "given": "a validator with a fixed clock at \"2026-10-29T00:00:00Z\" and maxAgeDays 30",
      "when": "it validates otherwise-valid requests whose requestedAt is exactly 30 days earlier (\"2026-09-29T00:00:00Z\"), 30 days minus one second earlier, and 30 days plus one second earlier",
      "then": "only the 30-days-plus-one-second request is flagged STALE (staleness is strictly greater than maxAgeDays, measured as Duration.between(requestedAt, clock.instant()).compareTo(Duration.ofDays(maxAgeDays)) > 0); the other two are kept in the requests list with no problems"
    },
    {
      "given": "a validator with a fixed clock and maxAgeDays 30",
      "when": "it validates an otherwise-valid request whose requestedAt is after the clock instant (future-dated)",
      "then": "the request is kept and no STALE problem is reported (negative age is never stale)"
    },
    {
      "given": "a validator with a fixed clock and maxAgeDays 30",
      "when": "it validates a request that is 60 days old but also has a null equipmentId, and separately a null request, and separately a request with requestedAt reported as an unreadable InvalidField",
      "then": "only the existing MISSING_FIELD / NULL_REQUEST / INVALID_VALUE problems are reported and no STALE problem is added; the staleness check runs only on requests that have no other validation problem, so it never dereferences a null requestedAt"
    },
    {
      "given": "existing callers that construct the validator with the no-argument constructor new WorkRequestValidator() (and processors built with new WorkRequestProcessor(validator, resolver, sorter))",
      "when": "any batch is validated, including requests years old",
      "then": "no STALE problem is produced and no request is dropped for age: the no-arg constructor leaves the staleness check disabled, and the clock/threshold constructor WorkRequestValidator(Clock clock, int maxAgeDays) is the only way to enable it; the clock is rejected with NullPointerException when null and maxAgeDays < 1 is rejected with IllegalArgumentException"
    },
    {
      "given": "a WorkRequestProcessor whose validator has a fixed clock at \"2026-10-29T00:00:00Z\" and maxAgeDays 30, and a batch containing one stale request (90 days old) that is otherwise valid and is a content duplicate of a fresh request, plus two fresh non-duplicate requests",
      "when": "process(batch) is called",
      "then": "the queue contains only the fresh requests (the stale one is excluded), the STALE problem appears among the validate-stage problems ordered by input index before any deduplicate-stage problem, the stale request produces no DUPLICATE problem and causes no priority escalation of the kept request, and the trace still has exactly three entries validate/deduplicate/prioritize with the validate out count excluding the stale request"
    },
    {
      "given": "a batch containing stale and fresh requests and a processor with a fixed clock",
      "when": "the same batch is shuffled and processed repeatedly",
      "then": "the queue, the problem list and the trace notes are identical every time (staleness depends only on requestedAt and the clock, and the stale note is sorted into the validate notes with no input index in it)"
    },
    {
      "given": "the Spring context with default configuration",
      "when": "it starts",
      "then": "a Clock bean returning Clock.systemUTC() is defined in ProcessingConfig, a StalenessProperties record @ConfigurationProperties(\"app.staleness\") with component maxAgeDays and @DefaultValue(\"30\") binds app.staleness.max-age-days, the workRequestValidator bean is built from that Clock and StalenessProperties.maxAgeDays, and application.yml contains app.staleness.max-age-days: 30"
    },
    {
      "given": "the property app.staleness.max-age-days bound to 7",
      "when": "the context starts and a request 10 days old is processed through the wired processor",
      "then": "the request is reported STALE and excluded, showing the threshold is configurable; binding a value below 1 fails with IllegalArgumentException whose message is \"app.staleness.max-age-days must be at least 1, was <value>\""
    },
    {
      "given": "the BatchObserver metrics wiring",
      "when": "a processing result containing two STALE problems is observed",
      "then": "a counter for ProblemCode.STALE is registered using the same naming scheme as the existing per-code counters and is incremented by 2, so there is still exactly one counter per ProblemCode value"
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
    "Adding a fourth pipeline stage or a fourth trace entry for staleness",
    "Changing the timestamps in sample-batch.json or the sample batch's pinned expectations",
    "An API parameter or request header that overrides the staleness threshold per call",
    "A review/acknowledge workflow, un-staling, or persistence of stale requests",
    "Rejecting or warning about future-dated requestedAt values",
    "Per-priority or per-equipment staleness thresholds",
    "Frontend display of the new problem code"
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
backend/src/main/java/com/tanya/workrequests/config/StalenessProperties.java
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

# Current code of the classes named in the criteria (before the change)

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

# Test helpers

=== backend/src/test/java/com/tanya/workrequests/processing/TestRequests.java ===
```java
package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.WorkRequest;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/** Builders that keep test inputs short and readable. */
final class TestRequests {

    static final String DEFAULT_TIME = "2026-10-20T08:00:00Z";

    private TestRequests() {
    }

    static WorkRequest request(String requestId, String equipmentId, Priority priority,
                               String requestedAt, String description) {
        Instant time = requestedAt == null ? null : Instant.parse(requestedAt);
        return new WorkRequest(requestId, equipmentId, priority, time, description);
    }

    /** Pairs each request with its position, as the validator would. */
    static List<IndexedRequest> indexed(WorkRequest... requests) {
        return IntStream.range(0, requests.length)
                .mapToObj(index -> new IndexedRequest(index, requests[index]))
                .toList();
    }

    /** A complete, valid request whose content is unique to its id. */
    static WorkRequest valid(String requestId) {
        return request(requestId, "EQ-" + requestId, Priority.MEDIUM, DEFAULT_TIME, "Work for " + requestId);
    }
}
```


## Output schema

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": [
    "diff",
    "summary"
  ],
  "properties": {
    "diff": {
      "type": "string",
      "description": "Unified diff adding new test files under backend/src/test/."
    },
    "summary": {
      "type": "string"
    }
  }
}
```
