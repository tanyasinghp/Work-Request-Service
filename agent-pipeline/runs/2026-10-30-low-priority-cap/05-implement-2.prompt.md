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
  "title": "Cap Low-priority requests per batch",
  "criteria": [
    {
      "given": "a cap of 2 and a batch of 5 LOW requests with distinct requestedAt plus 3 non-LOW requests, all passing validation and deduplication",
      "when": "WorkRequestProcessor.process(batch) runs",
      "then": "the accepted queue holds the 2 LOW requests with the earliest requestedAt plus all 3 non-LOW requests, and the other 3 LOW requests are reported as problems with code ProblemCode.OVER_LOW_PRIORITY_CAP (never silently dropped); only Priority.LOW counts against the cap"
    },
    {
      "given": "a cap of 1 and LOW requests 'B-2' and 'A-1' both at 2026-10-20T08:00:00Z",
      "when": "the cap is applied",
      "then": "'A-1' is kept and 'B-2' is reported over cap: ties on requestedAt break by requestId ascending, the same ordering the sorter already uses"
    },
    {
      "given": "any batch containing more LOW requests than the cap",
      "when": "the same batch is shuffled and processed 100 times",
      "then": "the accepted queue, the set of over-cap requestIds and the trace are identical every time"
    },
    {
      "given": "a cap of 0",
      "when": "a batch of 2 LOW and 1 HIGH request is processed",
      "then": "only the HIGH request is accepted and both LOW requests get OVER_LOW_PRIORITY_CAP problems; a cap of 0 is legal and means no Low work is accepted"
    },
    {
      "given": "a cap of 0 and two duplicates whose group's highest priority is MEDIUM, so the kept request is escalated from LOW to MEDIUM",
      "when": "the batch is processed",
      "then": "the kept request is accepted with no cap problem: the cap uses the priority as it stands after the deduplicate stage"
    },
    {
      "given": "a LOW request 'WR-9' at input index 4 over a cap of 2",
      "when": "Problem.overLowPriorityCap(4, \"WR-9\", 2) is called",
      "then": "the Problem has inputIndex 4, requestId 'WR-9', code OVER_LOW_PRIORITY_CAP, null field, relatedRequestId, escalatedFrom and escalatedTo, and message exactly \"WR-9 exceeds the per-batch Low-priority cap of 2\"; OVER_LOW_PRIORITY_CAP is appended last in ProblemCode and BatchObserver still increments one counter per ProblemCode value"
    },
    {
      "given": "a cap of 1, a batch that also yields validation and duplicate problems, and over-cap LOW requests 'L-2' at input index 7 and 'L-3' at input index 3",
      "when": "the ProcessingResult is read",
      "then": "cap problems appear in the prioritize stage, after validate and deduplicate problems and ordered by input index (index 3 before index 7); the prioritize StageTrace has stage \"prioritize\", out == in - 2, and notes exactly [\"L-2 dropped: over Low-priority cap of 1\", \"L-3 dropped: over Low-priority cap of 1\"] sorted naturally with no input indices"
    },
    {
      "given": "a batch with no LOW request over the cap, an empty batch, or a null batch",
      "when": "the batch is processed",
      "then": "no OVER_LOW_PRIORITY_CAP problem is produced, the prioritize trace has in == out and no cap note, and the trace still has exactly three entries: validate, deduplicate, prioritize"
    },
    {
      "given": "an application context with no app.batch.max-low-priority configured",
      "when": "BatchProperties is bound",
      "then": "maxLowPriority() is 1000 (@DefaultValue(\"1000\") on the new second component) and maxSize() is still 5000, and ProcessingConfig builds the WorkRequestSorter bean with BatchProperties.maxLowPriority()"
    },
    {
      "given": "app.batch.max-low-priority = -1",
      "when": "BatchProperties is bound or constructed",
      "then": "IllegalArgumentException is thrown with message \"app.batch.max-low-priority must be at least 0, was -1\"; 0 and positive values are accepted"
    },
    {
      "given": "existing callers of new BatchProperties(5000), new WorkRequestSorter(), new WorkRequestProcessor(validator, resolver, sorter) and the 13-request sample batch",
      "when": "they are compiled and run after the change",
      "then": "they still compile and behave as before: BatchProperties(int maxSize) delegates to maxLowPriority 1000, the no-arg WorkRequestSorter() applies no cap, capping is reached only via new WorkRequestSorter(int maxLowPriority), and the sample batch still gives the same queue, problems and counts 13 \u2192 10 \u2192 6"
    }
  ],
  "affectedClasses": [
    "ProblemCode",
    "Problem",
    "WorkRequestSorter",
    "BatchProperties",
    "ProcessingConfig",
    "BatchObserver"
  ],
  "outOfScope": [
    "Caps for MEDIUM, HIGH or CRITICAL priorities",
    "Per-sender or per-equipment quotas rather than a per-batch cap",
    "Changing the existing sort order or deduplication rules",
    "Exposing the cap or remaining Low capacity in the API response DTOs",
    "Rejecting a whole batch when the cap is exceeded",
    "Introducing a Clock bean or any time-based staleness logic"
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
backend/src/test/java/com/tanya/workrequests/api/BatchObserverOverLowPriorityCapTest.java
backend/src/test/java/com/tanya/workrequests/api/BatchObserverTest.java
backend/src/test/java/com/tanya/workrequests/api/CorsAndOpenApiTest.java
backend/src/test/java/com/tanya/workrequests/api/RequestMapperTest.java
backend/src/test/java/com/tanya/workrequests/api/SampleBatchSourceTest.java
backend/src/test/java/com/tanya/workrequests/api/UnexpectedErrorTest.java
backend/src/test/java/com/tanya/workrequests/api/WorkRequestControllerTest.java
backend/src/test/java/com/tanya/workrequests/config/ApplicationWiringTest.java
backend/src/test/java/com/tanya/workrequests/config/BatchPropertiesMaxLowPriorityTest.java
backend/src/test/java/com/tanya/workrequests/config/BatchPropertiesTest.java
backend/src/test/java/com/tanya/workrequests/config/CorsPropertiesTest.java
backend/src/test/java/com/tanya/workrequests/config/ProcessingConfigLowPriorityCapTest.java
backend/src/test/java/com/tanya/workrequests/domain/OverLowPriorityCapProblemTest.java
backend/src/test/java/com/tanya/workrequests/domain/PriorityTest.java
backend/src/test/java/com/tanya/workrequests/domain/ProblemTest.java
backend/src/test/java/com/tanya/workrequests/processing/DescriptionNormalizerTest.java
backend/src/test/java/com/tanya/workrequests/processing/DuplicateResolverTest.java
backend/src/test/java/com/tanya/workrequests/processing/EdgeCaseTest.java
backend/src/test/java/com/tanya/workrequests/processing/LowPriorityCapTest.java
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
    CONFLICTING_REQUEST_ID,
    // Appended last so existing ordinals (and anything persisted or asserted against them) stay put.
    OVER_LOW_PRIORITY_CAP
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

    /**
     * A Low-priority request that did not fit the per-batch Low cap. Reported rather than
     * dropped silently, so a caller can see exactly which work was deferred.
     */
    public static Problem overLowPriorityCap(int inputIndex, String requestId, int cap) {
        return new Problem(inputIndex, requestId, ProblemCode.OVER_LOW_PRIORITY_CAP, null, null, null, null,
                subject(requestId) + " exceeds the per-batch Low-priority cap of " + cap);
    }

    private static String subject(String requestId) {
        return requestId == null ? "Request" : requestId;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
```

=== backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java ===
```java
package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orders the queue: highest priority first, then oldest request, then requestId. The final
 * requestId tiebreaker makes the order total, so output never depends on input order.
 *
 * <p>Optionally caps how many {@link Priority#LOW} requests one batch may accept. The cap is
 * applied after sorting, so the Low requests that survive are the earliest ones (ties broken by
 * requestId, exactly the order this stage already uses) and the rest become problems instead of
 * disappearing. Priorities are read as they stand after deduplication, so an escalated duplicate
 * is no longer Low and never counts against the cap.
 */
public class WorkRequestSorter {

    static final String STAGE = "prioritize";

    /** Sentinel for "no cap configured": no realistic batch can hold this many Low requests. */
    static final int NO_CAP = Integer.MAX_VALUE;

    static final String SORT_NOTE =
            "Sorted by priority (highest first), then requestedAt (earliest first), then requestId";

    static final Comparator<WorkRequest> ORDER = Comparator
            .comparingInt((WorkRequest request) -> request.priority().weight()).reversed()
            .thenComparing(WorkRequest::requestedAt)
            .thenComparing(WorkRequest::requestId);

    private final int maxLowPriority;

    /** No Low-priority cap; every request that reaches this stage is accepted. */
    public WorkRequestSorter() {
        this(NO_CAP);
    }

    public WorkRequestSorter(int maxLowPriority) {
        if (maxLowPriority < 0) {
            throw new IllegalArgumentException("maxLowPriority must be at least 0, was " + maxLowPriority);
        }
        this.maxLowPriority = maxLowPriority;
    }

    public StageResult sort(List<IndexedRequest> requests) {
        List<IndexedRequest> sorted = requests.stream()
                .sorted(Comparator.comparing(IndexedRequest::request, ORDER))
                .toList();

        List<IndexedRequest> accepted = new ArrayList<>(sorted.size());
        List<IndexedRequest> overCap = new ArrayList<>();
        int lowAccepted = 0;
        for (IndexedRequest indexed : sorted) {
            if (indexed.request().priority() == Priority.LOW) {
                if (lowAccepted >= maxLowPriority) {
                    overCap.add(indexed);
                    continue;
                }
                lowAccepted++;
            }
            accepted.add(indexed);
        }

        StageTrace trace = new StageTrace(STAGE, requests.size(), accepted.size(), notes(overCap));
        return new StageResult(List.copyOf(accepted), problems(overCap), trace);
    }

    /** Problems are listed by input index so the stage's report reads in batch order. */
    private List<Problem> problems(List<IndexedRequest> overCap) {
        return overCap.stream()
                .sorted(Comparator.comparingInt(IndexedRequest::inputIndex))
                .map(indexed -> Problem.overLowPriorityCap(indexed.inputIndex(),
                        indexed.request().requestId(), maxLowPriority))
                .toList();
    }

    private List<String> notes(List<IndexedRequest> overCap) {
        if (overCap.isEmpty()) {
            return List.of(SORT_NOTE);
        }
        // Sorted text, no input indices, so the notes don't depend on input order.
        return overCap.stream()
                .map(indexed -> indexed.request().requestId() + " dropped: over Low-priority cap of " + maxLowPriority)
                .sorted()
                .toList();
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
public record BatchProperties(@DefaultValue("5000") int maxSize,
                              @DefaultValue("1000") int maxLowPriority) {

    static final int DEFAULT_MAX_LOW_PRIORITY = 1000;

    public BatchProperties {
        if (maxSize < 1) {
            throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
        }
        // 0 is legal and means "accept no Low work at all"; only negatives are nonsense.
        if (maxLowPriority < 0) {
            throw new IllegalArgumentException(
                    "app.batch.max-low-priority must be at least 0, was " + maxLowPriority);
        }
    }

    /** Kept for callers that only set the size guard; they get the default Low cap. */
    public BatchProperties(int maxSize) {
        this(maxSize, DEFAULT_MAX_LOW_PRIORITY);
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
    WorkRequestSorter workRequestSorter(BatchProperties batchProperties) {
        return new WorkRequestSorter(batchProperties.maxLowPriority());
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

(none)

# Previous attempt failed

Attempt 1 did not get through. Your earlier diff is already committed on this branch
unless the failure says it was rejected or did not apply; the files above show the current
state. Return a new diff against the files exactly as shown above.

## What failed

```
[ERROR] Tests run: 3, Failures: 0, Errors: 3, Skipped: 0, Time elapsed: 1.047 s <<< FAILURE! -- in com.tanya.workrequests.SmokeTest
[ERROR] com.tanya.workrequests.SmokeTest.doesNotExposeSensitiveActuatorEndpoints -- Time elapsed: 0.006 s <<< ERROR!
java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@38a147ba testClass = com.tanya.workrequests.SmokeTest, locations = [], classes = [com.tanya.workrequests.WorkRequestServiceApplication], contextInitializerClasses = [], activeProfiles = [], propertySourceDescriptors = [], propertySourceProperties = ["org.springframework.boot.test.context.SpringBootTestContextBootstrapper=true", "server.port=0"], contextCustomizers = [org.springframework.boot.test.context.filter.ExcludeFilterContextCustomizer@2301b75, org.springframework.boot.test.json.DuplicateJsonObjectContextCustomizerFactory$DuplicateJsonObjectContextCustomizer@6e1d4137, org.springframework.boot.test.mock.mockito.MockitoContextCustomizer@0, org.springframework.boot.test.web.client.TestRestTemplateContextCustomizer@42f9c19a, org.springframework.boot.test.web.reactor.netty.DisableReactorResourceFactoryGlobalResourcesContextCustomizerFactory$DisableReactorResourceFactoryGlobalResourcesContextCustomizerCustomizer@6345e7b9, org.springframework.boot.test.autoconfigure.OnFailureConditionReportContextCustomizerFactory$OnFailureConditionReportContextCustomizer@4cbf4f53, org.springframework.boot.test.autoconfigure.actuate.observability.ObservabilityContextCustomizerFactory$DisableObservabilityContextCustomizer@1f, org.springframework.boot.test.autoconfigure.properties.PropertyMappingContextCustomizer@0, org.springframework.boot.test.autoconfigure.web.servlet.WebDriverContextCustomizer@4e92466a, org.springframework.test.context.support.DynamicPropertiesContextCustomizer@0, org.springframework.boot.test.context.SpringBootTestAnnotation@5a32286], resourceBasePath = "src/main/webapp", contextLoader = org.springframework.boot.test.context.SpringBootContextLoader, parent = null]
	at org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:180)
	at org.springframework.test.context.support.DefaultTestContext.getApplicationContext(DefaultTestContext.java:130)
	at org.springframework.test.context.support.DependencyInjectionTestExecutionListener.injectDependencies(DependencyInjectionTestExecutionListener.java:155)
	at org.springframework.test.context.support.DependencyInjectionTestExecutionListener.prepareTestInstance(DependencyInjectionTestExecutionListener.java:111)
	at org.springframework.test.context.TestContextManager.prepareTestInstance(TestContextManager.java:260)
	at org.springframework.test.context.junit.jupiter.SpringExtension.postProcessTestInstance(SpringExtension.java:159)
	at java.base/java.util.stream.ForEachOps$ForEachOp$OfRef.accept(ForEachOps.java:184)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java:197)
	at java.base/java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:179)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java:197)
	at java.base/java.util.ArrayList$ArrayListSpliterator.forEachRemaining(ArrayList.java:1708)
	at java.base/java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:509)
	at java.base/java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:499)
	at java.base/java.util.stream.ForEachOps$ForEachOp.evaluateSequential(ForEachOps.java:151)
	at java.base/java.util.stream.ForEachOps$ForEachOp$OfRef.evaluateSequential(ForEachOps.java:174)
	at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
	at java.base/java.util.stream.ReferencePipeline.forEach(ReferencePipeline.java:596)
	at java.base/java.util.Optional.orElseGet(Optional.java:364)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'workRequestController' defined in file [/Users/tanyasingh/Documents/GitHub/Work-order/backend/target/classes/com/tanya/workrequests/api/WorkRequestController.class]: Unsatisfied dependency expressed through constructor parameter 1: Error creating bean with name 'workRequestProcessor' defined in class path resource [com/tanya/workrequests/config/ProcessingConfig.class]: Unsatisfied dependency expressed through method 'workRequestProcessor' parameter 2: Error creating bean with name 'workRequestSorter' defined in class path resource [com/tanya/workrequests/config/ProcessingConfig.class]: Unsatisfied dependency expressed through method 'workRequestSorter' parameter 0: Error creating bean with name 'app.batch-com.tanya.workrequests.config.BatchProperties': Failed to instantiate [com.tanya.workrequests.config.BatchProperties]: No default constructor found
	at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:804)
	at org.springframework.beans.factory.support.ConstructorResolver.autowireConstructor(ConstructorResolver.java:240)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.autowireConstructor(AbstractAutowireCapableBeanFactory.java:1393)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:1230)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:569)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:529)
	at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:339)
	at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:373)
	at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:337)
	at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:202)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.instantiateSingleton(DefaultListableBeanFactory.java:1235)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingleton(DefaultListableBeanFactory.java:1201)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingletons(DefaultListableBeanFactory.java:1130)
	at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:991)
	at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:628)
	at org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:146)
	at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:752)
	at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:439)
	at org.springframework.boot.SpringApplication.run(SpringApplication.java:318)
	at org.springframework.boot.test.context.SpringBootContextLoader.lambda$loadContext$3(SpringBootContextLoader.java:151)
	at org.springframework.util.function.ThrowingSupplier.get(ThrowingSupplier.java:58)
	at org.springframework.util.function.ThrowingSupplier.get(ThrowingSupplier.java:46)
	at org.springframework.boot.SpringApplication.withHook(SpringApplication.java:1461)
	at org.springframework.boot.test.context.SpringBootContextLoader$ContextLoaderHook.run(SpringBootContextLoader.java:590)
	at org.springframework.boot.test.context.SpringBootContextLoader.loadContext(SpringBootContextLoader.java:151)
	at org.springframework.boot.test.context.SpringBootContextLoader.loadContext(SpringBootContextLoader.java:110)
	at org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContextInternal(DefaultCacheAwareContextLoaderDelegate.java:225)
	at org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate.loadContext(DefaultCacheAwareContextLoaderDelegate.java:152)
	... 19 more
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'workRequestProcessor' defined in class path resource [com/tanya/workrequests/config/ProcessingConfig.class]: Unsatisfied dependency expressed through method 'workRequestProcessor' parameter 2: Error creating bean with name 'workRequestSorter' defined in class path resource [com/tanya/workrequests/config/ProcessingConfig.class]: Unsatisfied dependency expressed through method 'workRequestSorter' parameter 0: Error creating bean with name 'app.batch-com.tanya.workrequests.config.BatchProperties': Failed to instantiate [com.tanya.workrequests.config.BatchProperties]: No default constructor found
	at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:804)
	at org.springframework.beans.factory.support.ConstructorResolver.instantiateUsingFactoryMethod(ConstructorResolver.java:546)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod(AbstractAutowireCapableBeanFactory.java:1373)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:1203)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:569)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:529)
	at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:339)
	at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:373)
	at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:337)
	at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:202)
	at org.springframework.beans.factory.config.DependencyDescriptor.resolveCandidate(DependencyDescriptor.java:254)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.doResolveDependency(DefaultListableBeanFactory.java:1777)
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.resolveDependency(DefaultListableBeanFactory.java:1660)
	at org.springframework.beans.factory.support.ConstructorResolver.resolveAutowiredArgument(ConstructorResolver.java:913)
	at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:791)
	... 46 more
Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'workRequestSorter' defined in class path resource [com/tanya/workrequests/config/ProcessingConfig.class]: Unsatisfied dependency expressed through method 'workRequestSorter' parameter 0: Error creating bean with name 'app.batch-com.tanya.workrequests.config.BatchProperties': Failed to instantiate [com.tanya.workrequests.config.BatchProperties]: No default constructor found
	at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:804)
	at org.springframework.beans.factory.support.ConstructorResolver.instantiateUsingFactoryMethod(ConstructorResolver.java:546)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateUsingFactoryMethod(AbstractAutowireCapableBeanFactory.java:1373)
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:1203)
	at org.springframework.beans.factory.support.AbstractAutowireCapable
... (truncated)
```

## Your previous diff

```diff
diff --git a/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java b/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
--- a/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
+++ b/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
@@ -1,9 +1,11 @@
 package com.tanya.workrequests.domain;
 
 public enum ProblemCode {
     NULL_REQUEST,
     MISSING_FIELD,
     INVALID_VALUE,
     DUPLICATE,
-    CONFLICTING_REQUEST_ID
+    CONFLICTING_REQUEST_ID,
+    // Appended last so existing ordinals (and anything persisted or asserted against them) stay put.
+    OVER_LOW_PRIORITY_CAP
 }
diff --git a/backend/src/main/java/com/tanya/workrequests/domain/Problem.java b/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
--- a/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
+++ b/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
@@ -57,6 +57,16 @@
     public static Problem conflictingRequestId(int inputIndex, String requestId) {
         return new Problem(inputIndex, requestId, ProblemCode.CONFLICTING_REQUEST_ID, null, null, null, null,
                 "Request id " + requestId + " is used by requests with different content");
     }
 
+    /**
+     * A Low-priority request that did not fit the per-batch Low cap. Reported rather than
+     * dropped silently, so a caller can see exactly which work was deferred.
+     */
+    public static Problem overLowPriorityCap(int inputIndex, String requestId, int cap) {
+        return new Problem(inputIndex, requestId, ProblemCode.OVER_LOW_PRIORITY_CAP, null, null, null, null,
+                subject(requestId) + " exceeds the per-batch Low-priority cap of " + cap);
+    }
+
     private static String subject(String requestId) {
         return requestId == null ? "Request" : requestId;
     }
diff --git a/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java b/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java
--- a/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java
+++ b/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java
@@ -1,30 +1,97 @@
 package com.tanya.workrequests.processing;
 
+import com.tanya.workrequests.domain.Priority;
+import com.tanya.workrequests.domain.Problem;
 import com.tanya.workrequests.domain.StageTrace;
 import com.tanya.workrequests.domain.WorkRequest;
 
+import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.List;
 
 /**
  * Orders the queue: highest priority first, then oldest request, then requestId. The final
  * requestId tiebreaker makes the order total, so output never depends on input order.
+ *
+ * <p>Optionally caps how many {@link Priority#LOW} requests one batch may accept. The cap is
+ * applied after sorting, so the Low requests that survive are the earliest ones (ties broken by
+ * requestId, exactly the order this stage already uses) and the rest become problems instead of
+ * disappearing. Priorities are read as they stand after deduplication, so an escalated duplicate
+ * is no longer Low and never counts against the cap.
  */
 public class WorkRequestSorter {
 
     static final String STAGE = "prioritize";
 
+    /** Sentinel for "no cap configured": no realistic batch can hold this many Low requests. */
+    static final int NO_CAP = Integer.MAX_VALUE;
+
+    static final String SORT_NOTE =
+            "Sorted by priority (highest first), then requestedAt (earliest first), then requestId";
+
     static final Comparator<WorkRequest> ORDER = Comparator
             .comparingInt((WorkRequest request) -> request.priority().weight()).reversed()
             .thenComparing(WorkRequest::requestedAt)
             .thenComparing(WorkRequest::requestId);
 
+    private final int maxLowPriority;
+
+    /** No Low-priority cap; every request that reaches this stage is accepted. */
+    public WorkRequestSorter() {
+        this(NO_CAP);
+    }
+
+    public WorkRequestSorter(int maxLowPriority) {
+        if (maxLowPriority < 0) {
+            throw new IllegalArgumentException("maxLowPriority must be at least 0, was " + maxLowPriority);
+        }
+        this.maxLowPriority = maxLowPriority;
+    }
+
     public StageResult sort(List<IndexedRequest> requests) {
         List<IndexedRequest> sorted = requests.stream()
                 .sorted(Comparator.comparing(IndexedRequest::request, ORDER))
                 .toList();
-        List<String> notes = List.of("Sorted by priority (highest first), then requestedAt (earliest first), then requestId");
-        StageTrace trace = new StageTrace(STAGE, requests.size(), sorted.size(), notes);
-        return new StageResult(sorted, List.of(), trace);
+
+        List<IndexedRequest> accepted = new ArrayList<>(sorted.size());
+        List<IndexedRequest> overCap = new ArrayList<>();
+        int lowAccepted = 0;
+        for (IndexedRequest indexed : sorted) {
+            if (indexed.request().priority() == Priority.LOW) {
+                if (lowAccepted >= maxLowPriority) {
+                    overCap.add(indexed);
+                    continue;
+                }
+                lowAccepted++;
+            }
+            accepted.add(indexed);
+        }
+
+        StageTrace trace = new StageTrace(STAGE, requests.size(), accepted.size(), notes(overCap));
+        return new StageResult(List.copyOf(accepted), problems(overCap), trace);
+    }
+
+    /** Problems are listed by input index so the stage's report reads in batch order. */
+    private List<Problem> problems(List<IndexedRequest> overCap) {
+        return overCap.stream()
+                .sorted(Comparator.comparingInt(IndexedRequest::inputIndex))
+                .map(indexed -> Problem.overLowPriorityCap(indexed.inputIndex(),
+                        indexed.request().requestId(), maxLowPriority))
+                .toList();
+    }
+
+    private List<String> notes(List<IndexedRequest> overCap) {
+        if (overCap.isEmpty()) {
+            return List.of(SORT_NOTE);
+        }
+        // Sorted text, no input indices, so the notes don't depend on input order.
+        return overCap.stream()
+                .map(indexed -> indexed.request().requestId() + " dropped: over Low-priority cap of " + maxLowPriority)
+                .sorted()
+                .toList();
     }
 }
diff --git a/backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java b/backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java
--- a/backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java
+++ b/backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java
@@ -1,16 +1,32 @@
 package com.tanya.workrequests.config;
 
 import org.springframework.boot.context.properties.ConfigurationProperties;
 import org.springframework.boot.context.properties.bind.DefaultValue;
 
 /** {@code app.batch.*}: guards that protect the service from oversized input. */
 @ConfigurationProperties("app.batch")
-public record BatchProperties(@DefaultValue("5000") int maxSize) {
+public record BatchProperties(@DefaultValue("5000") int maxSize,
+                              @DefaultValue("1000") int maxLowPriority) {
+
+    static final int DEFAULT_MAX_LOW_PRIORITY = 1000;
 
     public BatchProperties {
         if (maxSize < 1) {
             throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
         }
+        // 0 is legal and means "accept no Low work at all"; only negatives are nonsense.
+        if (maxLowPriority < 0) {
+            throw new IllegalArgumentException(
+                    "app.batch.max-low-priority must be at least 0, was " + maxLowPriority);
+        }
+    }
+
+    /** Kept for callers that only set the size guard; they get the default Low cap. */
+    public BatchProperties(int maxSize) {
+        this(maxSize, DEFAULT_MAX_LOW_PRIORITY);
     }
 }
diff --git a/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java b/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
--- a/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
+++ b/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
@@ -28,8 +28,8 @@
     DuplicateResolver duplicateResolver(DescriptionNormalizer descriptionNormalizer) {
         return new DuplicateResolver(descriptionNormalizer);
     }
 
     @Bean
-    WorkRequestSorter workRequestSorter() {
-        return new WorkRequestSorter();
+    WorkRequestSorter workRequestSorter(BatchProperties batchProperties) {
+        return new WorkRequestSorter(batchProperties.maxLowPriority());
     }

```

## Other files the failure points at (current contents)

=== backend/src/test/java/com/tanya/workrequests/config/BatchPropertiesMaxLowPriorityTest.java ===
```java
package com.tanya.workrequests.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** app.batch.max-low-priority: default, bounds, and the old single-argument constructor. */
class BatchPropertiesMaxLowPriorityTest {

    @Test
    void defaultsMaxLowPriorityToOneThousandWhenNotConfigured() {
        assertThat(bind(Map.of()).maxLowPriority()).isEqualTo(1000);
    }

    @Test
    void keepsTheMaxSizeDefaultOfFiveThousandWhenNotConfigured() {
        assertThat(bind(Map.of()).maxSize()).isEqualTo(5000);
    }

    @Test
    void bindsAConfiguredMaxLowPriority() {
        assertThat(bind(Map.of("app.batch.max-low-priority", "25")).maxLowPriority()).isEqualTo(25);
    }

    @Test
    void rejectsANegativeMaxLowPriorityWhenConstructed() {
        assertThatThrownBy(() -> new BatchProperties(5000, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-low-priority must be at least 0, was -1");
    }

    @Test
    void rejectsANegativeMaxLowPriorityWhenBound() {
        Throwable thrown = catchThrowable(() -> bind(Map.of("app.batch.max-low-priority", "-1")));

        assertThat(rootCauseOf(thrown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-low-priority must be at least 0, was -1");
    }

    @Test
    void acceptsAMaxLowPriorityOfZero() {
        assertThat(new BatchProperties(5000, 0).maxLowPriority()).isZero();
    }

    @Test
    void acceptsAPositiveMaxLowPriority() {
        assertThat(new BatchProperties(5000, 7).maxLowPriority()).isEqualTo(7);
    }

    @Test
    void singleArgumentConstructorStillDefaultsMaxLowPriorityToOneThousand() {
        assertThat(new BatchProperties(5000).maxLowPriority()).isEqualTo(1000);
    }

    @Test
    void singleArgumentConstructorKeepsTheMaxSizeItIsGiven() {
        assertThat(new BatchProperties(5000).maxSize()).isEqualTo(5000);
    }

    @Test
    void stillRejectsAMaxSizeBelowOne() {
        assertThatThrownBy(() -> new BatchProperties(0, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.batch.max-size must be at least 1, was 0");
    }

    private static BatchProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("app.batch", BatchProperties.class);
    }

    /** The exception itself when it has no cause, so either way of failing is accepted. */
    private static Throwable rootCauseOf(Throwable thrown) {
        Throwable cause = thrown;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
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

=== backend/src/test/java/com/tanya/workrequests/SmokeTest.java ===
```java
package com.tanya.workrequests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the real server on a random port and talks to it over HTTP, like a client would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmokeTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsAndReportsHealthUp() {
        ResponseEntity<JsonNode> health = http.getForEntity("/actuator/health", JsonNode.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void processesTheSampleAndCountsItsProblemsInMetrics() {
        double duplicatesBefore = rejected("DUPLICATE");
        JsonNode sample = http.getForObject("/api/work-requests/sample", JsonNode.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.postForEntity("/api/work-requests/batch",
                new HttpEntity<>(sample.toString(), headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accepted")).hasSize(6);
        assertThat(rejected("DUPLICATE")).isEqualTo(duplicatesBefore + 2);
        assertThat(http.getForObject("/actuator/metrics/workrequests.batch.duration", JsonNode.class)
                .at("/measurements/0/value").asDouble()).isPositive();
    }

    @Test
    void doesNotExposeSensitiveActuatorEndpoints() {
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/actuator/heapdump", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private double rejected(String code) {
        return http.getForObject("/actuator/metrics/workrequests.rejected?tag=code:" + code, JsonNode.class)
                .at("/measurements/0/value").asDouble();
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/api/ApiErrorHandlingTest.java ===
```java
package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A small limit keeps the oversized-batch cases cheap; the default 5000 is checked elsewhere. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.batch.max-size=3")
class ApiErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"[{\"requestId\": \"REQ-1\",", "not json", "[1, 2,]", "{\"a\" 1}"})
    void rejectsMalformedJson(String body) throws Exception {
        postBatch(body)
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body is missing or is not valid JSON"));
    }

    @Test
    void rejectsAMissingBody() throws Exception {
        postBatch("")
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body is missing or is not valid JSON"));
    }

    @ParameterizedTest(name = "body: {0}")
    @ValueSource(strings = {"{\"requestId\": \"REQ-1\"}", "\"REQ-1\"", "42", "true", "null"})
    void rejectsABodyThatIsNotAnArray(String body) throws Exception {
        postBatch(body)
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Request body must be a JSON array of work requests"));
    }

    @Test
    void rejectsABatchOverTheLimit() throws Exception {
        postBatch("[{}, {}, {}, {}]")
                .andExpect(status().isBadRequest())
                .andExpect(errorBody(400, "Bad Request", "Batch has 4 requests; the maximum is 3"));
    }

    @Test
    void acceptsABatchExactlyAtTheLimit() throws Exception {
        postBatch("[{}, {}, {}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace[0].in").value(3));
    }

    @Test
    void keepsTheStatusOfFrameworkErrors() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.TEXT_PLAIN).content("[]"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
        mockMvc.perform(get("/api/work-requests/batch"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(get("/api/work-requests/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$", aMapWithSize(3)));
    }

    private ResultActions postBatch(String body) throws Exception {
        return mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Exactly {status, error, message}, with nothing that looks like a stack trace or parser detail. */
    private static ResultMatcher errorBody(int status, String error, String message) {
        return result -> {
            jsonPath("$", aMapWithSize(3)).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.error").value(error).match(result);
            jsonPath("$.message").value(message).match(result);
            content().string(not(containsString("Exception"))).match(result);
            content().string(not(containsString("com.fasterxml"))).match(result);
        };
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/api/CorsAndOpenApiTest.java ===
```java
package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsAndOpenApiTest {

    private static final String FRONT_END = "http://localhost:5173";
    private static final String OTHER_SITE = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsPreflightFromTheFrontEnd() throws Exception {
        mockMvc.perform(preflight(FRONT_END))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END));
    }

    @Test
    void refusesPreflightFromAnyOtherOrigin() throws Exception {
        mockMvc.perform(preflight(OTHER_SITE))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void refusesACrossOriginPostFromAnyOtherOrigin() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").header(HttpHeaders.ORIGIN, OTHER_SITE)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/work-requests/batch").header(HttpHeaders.ORIGIN, FRONT_END)
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT_END));
    }

    @Test
    void publishesTheApiDescription() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Work Request Service"))
                .andExpect(jsonPath("$.paths['/api/work-requests/batch'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/work-requests/batch'].post.requestBody.content['application/json'].examples")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/work-requests/sample'].get").exists());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void documentedExampleBehavesAsItsNameSays() throws Exception {
        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(WorkRequestController.EXAMPLE_BATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-1")))
                .andExpect(jsonPath("$.accepted[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.problems[*].code").value(contains("INVALID_VALUE", "DUPLICATE")))
                .andExpect(jsonPath("$.problems[0].message").value(containsString("invalid priority 'URGENT'")))
                .andExpect(jsonPath("$.problems[1].escalatedTo").value("CRITICAL"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder preflight(String origin) {
        return options("/api/work-requests/batch")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/api/UnexpectedErrorTest.java ===
```java
package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.ParsedBatch;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnexpectedErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkRequestProcessor processor;

    @Test
    void returnsAGeneric500WithoutLeakingTheCause() throws Exception {
        when(processor.process(any(ParsedBatch.class)))
                .thenThrow(new IllegalStateException("secret internal detail at com.tanya.Something"));

        mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/api/WorkRequestControllerTest.java ===
```java
package com.tanya.workrequests.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void processesTheSampleBatchIntoTheKnownQueue() throws Exception {
        postBatch(sampleJson())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accepted[*].requestId")
                        .value(contains("REQ-101", "REQ-102", "REQ-104", "REQ-110", "REQ-111", "REQ-112")))
                .andExpect(jsonPath("$.accepted[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.problems", hasSize(7)))
                .andExpect(jsonPath("$.problems[?(@.code == 'MISSING_FIELD')]", hasSize(3)))
                .andExpect(jsonPath("$.problems[?(@.code == 'DUPLICATE')]", hasSize(2)))
                .andExpect(jsonPath("$.problems[?(@.code == 'CONFLICTING_REQUEST_ID')]", hasSize(2)))
                .andExpect(jsonPath("$.trace[*].stage").value(contains("validate", "deduplicate", "prioritize")))
                .andExpect(jsonPath("$.trace[*].in").value(contains(13, 10, 6)))
                .andExpect(jsonPath("$.trace[*].out").value(contains(10, 6, 6)));
    }

    @Test
    void writesTimesAsIsoInstantsAndPrioritiesAsNames() throws Exception {
        postBatch(sampleJson())
                .andExpect(jsonPath("$.accepted[0].requestedAt").value("2026-10-20T08:15:00Z"))
                .andExpect(jsonPath("$.accepted[5].priority").value("LOW"));
    }

    @Test
    void includesFieldsThatDoNotApplyAsNulls() throws Exception {
        // A stable shape lets the UI read every field without existence checks.
        postBatch(sampleJson())
                .andExpect(jsonPath("$.problems[0].relatedRequestId").value(nullValue()))
                .andExpect(jsonPath("$.problems[0].escalatedFrom").value(nullValue()));
    }

    @Test
    void sampleEndpointReturnsTheThirteenRawRequests() throws Exception {
        mockMvc.perform(get("/api/work-requests/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[0].requestId").value("REQ-101"))
                .andExpect(jsonPath("$[2].equipmentId").value(nullValue()))
                .andExpect(jsonPath("$[5].description").value("   "));
    }

    @Test
    void processesAnEmptyArray() throws Exception {
        postBatch("[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted", hasSize(0)))
                .andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.trace", hasSize(3)));
    }

    @Test
    void reportsUnknownPriorityAsInvalidValue() throws Exception {
        postBatch("""
                [{"requestId": "REQ-7", "equipmentId": "PUMP-7", "priority": "URGENT",
                  "requestedAt": "2026-10-20T08:15:00Z", "description": "Pump leaking"},
                 {"requestId": "REQ-8", "equipmentId": "FAN-1", "priority": "high",
                  "requestedAt": "2026-10-20T09:00:00Z", "description": "Belt squeal"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-8")))
                .andExpect(jsonPath("$.accepted[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.problems", hasSize(1)))
                .andExpect(jsonPath("$.problems[0].inputIndex").value(0))
                .andExpect(jsonPath("$.problems[0].requestId").value("REQ-7"))
                .andExpect(jsonPath("$.problems[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.problems[0].field").value("priority"))
                .andExpect(jsonPath("$.problems[0].message").value("REQ-7 has invalid priority 'URGENT'"));
    }

    @Test
    void reportsUnreadableValuesWithoutShiftingIndices() throws Exception {
        postBatch("""
                [42,
                 {"requestId": "REQ-2", "equipmentId": "PUMP-7", "priority": 3,
                  "requestedAt": "yesterday", "description": "Pump leaking", "site": "North"},
                 null,
                 {"requestId": "REQ-4", "equipmentId": "FAN-1", "priority": "LOW",
                  "requestedAt": "2026-10-20T09:00:00+05:30", "description": "Belt squeal"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted[*].requestId").value(contains("REQ-4")))
                .andExpect(jsonPath("$.accepted[0].requestedAt").value("2026-10-20T03:30:00Z"))
                .andExpect(jsonPath("$.problems[*].inputIndex").value(contains(0, 1, 1, 2)))
                .andExpect(jsonPath("$.problems[*].code")
                        .value(contains("NULL_REQUEST", "INVALID_VALUE", "INVALID_VALUE", "NULL_REQUEST")))
                .andExpect(jsonPath("$.problems[1].message").value("REQ-2 has invalid priority '3'"))
                .andExpect(jsonPath("$.problems[2].message").value("REQ-2 has invalid requestedAt 'yesterday'"));
    }

    @Test
    void exposesEscalationFieldsOnTheEscalatingDuplicateOnly() throws Exception {
        postBatch(sampleJson())
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].escalatedFrom").value(contains("MEDIUM")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].escalatedTo").value(contains("CRITICAL")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-108')].relatedRequestId").value(contains("REQ-101")))
                .andExpect(jsonPath("$.problems[?(@.requestId == 'REQ-107')].escalatedTo").value(contains(nullValue())))
                .andExpect(jsonPath("$.problems[?(@.escalatedTo != null)]", hasSize(1)));
    }

    @Test
    void rejectsABatchOverTheDefaultLimitOf5000() throws Exception {
        String oversized = "[" + String.join(",", Collections.nCopies(5001, "{}")) + "]";

        postBatch(oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Batch has 5001 requests; the maximum is 5000"));
    }

    private ResultActions postBatch(String json) throws Exception {
        return mockMvc.perform(post("/api/work-requests/batch").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String sampleJson() throws IOException {
        try (InputStream json = WorkRequestControllerTest.class.getResourceAsStream("/sample-batch.json")) {
            return new String(json.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

=== backend/src/test/java/com/tanya/workrequests/config/ApplicationWiringTest.java ===
```java
package com.tanya.workrequests.config;

import com.tanya.workrequests.api.RequestMapper;
import com.tanya.workrequests.api.SampleBatchSource;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.WorkRequest;
import com.tanya.workrequests.processing.WorkRequestProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApplicationWiringTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BatchProperties batchProperties;

    @Test
    void createsOneSharedProcessor() {
        assertThat(context.getBean(WorkRequestProcessor.class)).isSameAs(context.getBean(WorkRequestProcessor.class));
    }

    @Test
    void wiresTheRealStagesSoTheSampleProducesTheKnownQueue() {
        RequestMapper mapper = context.getBean(RequestMapper.class);
        SampleBatchSource sample = context.getBean(SampleBatchSource.class);

        ProcessingResult result = context.getBean(WorkRequestProcessor.class).process(mapper.map(sample.raw()));

        assertThat(result.accepted()).extracting(WorkRequest::requestId)
                .containsExactly("REQ-101", "REQ-102", "REQ-104", "REQ-110", "REQ-111", "REQ-112");
    }

    @Test
    void bindsTheBatchSizeLimitWithItsDefault() {
        assertThat(batchProperties.maxSize()).isEqualTo(5000);
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
