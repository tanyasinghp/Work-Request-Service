# implement-2-retry: prompt

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
  "title": "Flag requests older than 30 days as stale and keep them out of the queue",
  "criteria": [
    {
      "given": "the ProblemCode enum",
      "when": "the new code is added",
      "then": "ProblemCode contains a constant named STALE in addition to the existing NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID (order of declaration is irrelevant); because BatchObserver derives one metrics counter per ProblemCode value, no change to BatchObserver is required for a counter named after STALE to appear"
    },
    {
      "given": "the Problem factories",
      "when": "Problem.stale(int inputIndex, String requestId, long maxAgeDays) is called, e.g. Problem.stale(3, \"R-7\", 30)",
      "then": "it returns a Problem with inputIndex 3, requestId \"R-7\", code STALE, null field, null relatedRequestId, null escalatedFrom/escalatedTo, and message exactly \"R-7 is older than 30 days\" (pattern: requestId + \" is older than \" + maxAgeDays + \" days\"); the message never mentions the input index"
    },
    {
      "given": "a WorkRequestValidator built with the existing no-argument constructor new WorkRequestValidator()",
      "when": "validate is called with requests whose requestedAt is arbitrarily far in the past",
      "then": "no STALE problem is ever produced and the stage result is exactly as before this story (staleness checking is off unless a Clock is supplied), so existing direct-construction tests such as SampleBatchTest keep passing regardless of the real date"
    },
    {
      "given": "a validator built with the new overload new WorkRequestValidator(Clock clock, Duration maxAge), clock = Clock.fixed(Instant.parse(\"2026-10-29T00:00:00Z\"), ZoneOffset.UTC) and maxAge = Duration.ofDays(30)",
      "when": "validate is called with an otherwise fully valid request \"R-1\" whose requestedAt is 2026-09-01T00:00:00Z (58 days before now)",
      "then": "the returned StageResult problems contain exactly one problem, Problem.stale(inputIndex, \"R-1\", 30), and the request is NOT in StageResult.requests(), so it never reaches deduplication, prioritization, or the accepted queue"
    },
    {
      "given": "the same clock-backed validator (now = 2026-10-29T00:00:00Z, maxAge = 30 days)",
      "when": "validate is called with valid requests whose requestedAt is exactly 2026-09-29T00:00:00Z (age exactly 30 days), 2026-09-28T23:59:59Z (age 30 days + 1 second), and 2026-11-05T00:00:00Z (dated in the future)",
      "then": "only the 30-days-plus-1-second request is reported STALE and excluded; the exactly-30-days request and the future-dated request are accepted with no problem (the rule is Duration.between(requestedAt, clock.instant()).compareTo(maxAge) > 0)"
    },
    {
      "given": "the clock-backed validator and a request that is both stale and already invalid (for example requestedAt null, or missing equipmentId and dated 100 days ago)",
      "when": "validate is called",
      "then": "only the existing MISSING_FIELD/INVALID_VALUE/NULL_REQUEST problems are reported for that request and no STALE problem is added; staleness is evaluated only for requests that pass all field checks (so a null requestedAt can never trigger a STALE problem)"
    },
    {
      "given": "the clock-backed validator and a batch of 4 valid requests of which 2 are older than maxAge",
      "when": "validate is called",
      "then": "the single validate StageTrace reports stage \"validate\", in = 4, out = 2 (stale requests are counted out), and its notes include one entry per stale request of the exact form requestId + \" rejected: stale\" (e.g. \"R-1 rejected: stale\"); notes remain sorted naturally and contain no input indices, and the result still has exactly the three stages validate, deduplicate, prioritize"
    },
    {
      "given": "a WorkRequestProcessor built over a clock-backed validator, and a batch containing a stale request that is a content duplicate of a fresh kept request plus other requests with validation and duplicate problems",
      "when": "process is called, and again with the same batch shuffled",
      "then": "the stale request produces only the STALE problem (no DUPLICATE problem, because deduplication never sees it), ProcessingResult.problems() lists all validate-stage problems (including STALE) before deduplicate-stage problems and, within the validate stage, in ascending inputIndex order, and the accepted queue, problems and trace are identical for every input order"
    },
    {
      "given": "configuration binding",
      "when": "the context starts with no app.staleness.* properties set",
      "then": "a @ConfigurationProperties(\"app.staleness\") record StalenessProperties(@DefaultValue(\"30d\") Duration maxAge) binds maxAge to Duration.ofDays(30); setting app.staleness.max-age=7d binds Duration.ofDays(7); a non-positive value (0 or negative, e.g. app.staleness.max-age=0s) fails with an IllegalArgumentException whose message is \"app.staleness.max-age must be positive\""
    },
    {
      "given": "the Spring wiring",
      "when": "the application context is started",
      "then": "ProcessingConfig exposes a java.time.Clock bean returning Clock.systemUTC() and builds the WorkRequestValidator bean as new WorkRequestValidator(clock, stalenessProperties.maxAge()), so the deployed service flags staleness while the no-arg constructor stays available for tests; the existing constructors new WorkRequestValidator(), new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter() and new WorkRequestProcessor(validator, resolver, sorter) still compile and behave unchanged"
    },
    {
      "given": "the clock-backed validator with maxAge = Duration.ofDays(7)",
      "when": "validate is called with a valid request dated 10 days before the fixed now",
      "then": "the problem message is exactly requestId + \" is older than 7 days\", i.e. the day count in the message comes from the configured maxAge.toDays() and not from the request's actual age"
    }
  ],
  "affectedClasses": [
    "ProblemCode",
    "Problem",
    "WorkRequestValidator",
    "StalenessProperties",
    "ProcessingConfig"
  ],
  "outOfScope": [
    "Adding a fourth pipeline stage or a fourth StageTrace entry; staleness is reported inside the existing validate stage",
    "Adding fields to Problem, ProblemDto or BatchResponse (such as the request's actual age in days)",
    "An API flag or query parameter to include stale requests in the queue anyway",
    "Per-priority or per-equipment staleness thresholds, or business-day/time-zone aware ageing",
    "Auto-closing, re-dating or archiving stale requests, and any persistence of review decisions",
    "Changes to DuplicateResolver, WorkRequestSorter or DescriptionNormalizer behaviour",
    "Updating the sample-batch.json timestamps or any frontend/UI work"
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
backend/src/test/java/com/tanya/workrequests/config/StalenessPropertiesTest.java
backend/src/test/java/com/tanya/workrequests/config/StalenessWiringTest.java
backend/src/test/java/com/tanya/workrequests/domain/PriorityTest.java
backend/src/test/java/com/tanya/workrequests/domain/ProblemTest.java
backend/src/test/java/com/tanya/workrequests/domain/StalenessProblemTest.java
backend/src/test/java/com/tanya/workrequests/processing/DescriptionNormalizerTest.java
backend/src/test/java/com/tanya/workrequests/processing/DuplicateResolverTest.java
backend/src/test/java/com/tanya/workrequests/processing/EdgeCaseTest.java
backend/src/test/java/com/tanya/workrequests/processing/OrderIndependenceTest.java
backend/src/test/java/com/tanya/workrequests/processing/SampleBatch.java
backend/src/test/java/com/tanya/workrequests/processing/SampleBatchTest.java
backend/src/test/java/com/tanya/workrequests/processing/StaleRequestProcessingTest.java
backend/src/test/java/com/tanya/workrequests/processing/StalenessValidationTest.java
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
    STALE
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
     * A request whose age exceeds the configured maximum. The message quotes the configured
     * threshold, not the request's actual age, so the same request reads the same every day.
     */
    public static Problem stale(int inputIndex, String requestId, long maxAgeDays) {
        return new Problem(inputIndex, requestId, ProblemCode.STALE, null, null, null, null,
                subject(requestId) + " is older than " + maxAgeDays + " days");
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

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Rejects null, incomplete, and unreadable requests. Every missing or invalid field is its
 * own problem, so a request with two bad fields reports both. Blank strings count as missing.
 *
 * <p>When a {@link Clock} and a maximum age are supplied, requests older than that age are
 * also rejected as STALE so they never reach deduplication or the queue. Staleness is checked
 * only for requests that passed every field check, so a null {@code requestedAt} is reported
 * as a missing field and nothing else.
 */
public class WorkRequestValidator {

    static final String STAGE = "validate";

    // Null clock means staleness checking is off; the no-argument constructor keeps the
    // pre-staleness behaviour for callers that have no notion of "now".
    private final Clock clock;
    private final Duration maxAge;

    public WorkRequestValidator() {
        this.clock = null;
        this.maxAge = null;
    }

    public WorkRequestValidator(Clock clock, Duration maxAge) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
    }

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
            if (!requestProblems.isEmpty()) {
                problems.addAll(requestProblems);
                notes.add(rejectionNote(request, requestProblems));
            } else if (isStale(request)) {
                problems.add(Problem.stale(index, request.requestId(), maxAge.toDays()));
                notes.add(request.requestId() + " rejected: stale");
            } else {
                valid.add(new IndexedRequest(index, request));
            }
        }
        // Sorted, and free of input indices, so the trace is identical whatever the input order.
        notes.sort(Comparator.naturalOrder());

        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
        return new StageResult(valid, problems, trace);
    }

    /** Exactly {@code maxAge} old is still fresh; future-dated requests are never stale. */
    private boolean isStale(WorkRequest request) {
        if (clock == null) {
            return false;
        }
        return Duration.between(request.requestedAt(), clock.instant()).compareTo(maxAge) > 0;
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

=== backend/src/main/java/com/tanya/workrequests/config/StalenessProperties.java ===
```java
package com.tanya.workrequests.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Objects;

/**
 * How old a request may be before it is flagged as stale and kept out of the queue.
 *
 * <p>Bound from {@code app.staleness.*}. A non-positive maximum age would reject every
 * request, so it is refused at startup rather than silently emptying the queue.
 */
@ConfigurationProperties("app.staleness")
public record StalenessProperties(@DefaultValue("30d") Duration maxAge) {

    public StalenessProperties {
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("app.staleness.max-age must be positive");
        }
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

import java.time.Clock;

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

    /** UTC everywhere, so ageing does not shift with the server's time zone. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WorkRequestValidator workRequestValidator(Clock clock, StalenessProperties stalenessProperties) {
        return new WorkRequestValidator(clock, stalenessProperties.maxAge());
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

# Classes named in the criteria that don't exist yet (create them)

(none)

# Previous attempt failed

Attempt 1 did not get through. Your earlier diff is already committed on this branch
unless the failure says it was rejected or did not apply; the files above show the current
state. Return a new diff against the files exactly as shown above.

## What failed

```
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.078 s <<< FAILURE! -- in com.tanya.workrequests.processing.OrderIndependenceTest
[ERROR] com.tanya.workrequests.processing.OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem -- Time elapsed: 0.008 s <<< FAILURE!
java.lang.AssertionError: 

Expecting ArrayList:
  [MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    INVALID_VALUE,
    INVALID_VALUE,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    MISSING_FIELD,
    INVALID_VALUE,
    INVALID_VALUE,
    NULL_REQUEST,
    NULL_REQUEST,
    NULL_REQUEST,
    NULL_REQUEST,
    NULL_REQUEST,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    DUPLICATE,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID,
    CONFLICTING_REQUEST_ID]
to contain:
  [NULL_REQUEST,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE,
    CONFLICTING_REQUEST_ID,
    STALE]
but could not find the following element(s):
  [STALE]

	at com.tanya.workrequests.processing.OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem(OrderIndependenceTest.java:59)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)

2026-10-29T22:49:59.631+05:30  INFO 5114 --- [work-request-service] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for test class [com.tanya.workrequests.api.UnexpectedErrorTest]: UnexpectedErrorTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-10-29T22:49:59.643+05:30  INFO 5114 --- [work-request-service] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.tanya.workrequests.WorkRequestServiceApplication for test class com.tanya.workrequests.api.UnexpectedErrorTest

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::               (v3.5.16)

2026-10-29T22:49:59.656+05:30  INFO 5114 --- [work-request-service] [           main] c.t.w.api.UnexpectedErrorTest            : Starting UnexpectedErrorTest using Java 21.0.13.0.3 with PID 5114 (started by tanyasingh in /Users/tanyasingh/Documents/GitHub/Work-order/backend)
2026-10-29T22:49:59.656+05:30  INFO 5114 --- [work-request-service] [           main] c.t.w.api.UnexpectedErrorTest            : No active profile set, falling back to 1 default profile: "default"
2026-10-29T22:49:59.848+05:30  INFO 5114 --- [work-request-service] [           main] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-10-29T22:49:59.848+05:30  INFO 5114 --- [work-request-service] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-10-29T22:49:59.849+05:30  INFO 5114 --- [work-request-service] [           main] o.s.b.a.e.web.EndpointLinksResolver      : Exposing 3 endpoints beneath base path '/actuator'
2026-10-29T22:49:59.850+05:30  INFO 5114 --- [work-request-service] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 2 ms
2026-10-29T22:49:59.861+05:30  INFO 5114 --- [work-request-service] [           main] c.t.w.api.UnexpectedErrorTest            : Started UnexpectedErrorTest in 0.215 seconds (process running for 3.265)
2026-10-29T22:49:59.861+05:30  WARN 5114 --- [work-request-service] [           main] o.s.core.events.SpringDocAppInitializer  : SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production, set the property 'springdoc.api-docs.enabled=false'
2026-10-29T22:49:59.861+05:30  WARN 5114 --- [work-request-service] [           main] o.s.core.events.SpringDocAppInitializer  : SpringDoc /swagger-ui.html endpoint is enabled by default. To disable it in production, set the property 'springdoc.swagger-ui.enabled=false'
2026-10-29T22:49:59.869+05:30 ERROR 5114 --- [work-request-service] [           main] c.t.w.api.ApiExceptionHandler            : Unexpected error while handling a request

java.lang.IllegalStateException: secret internal detail at com.tanya.Something
	at com.tanya.workrequests.processing.WorkRequestProcessor.process(WorkRequestProcessor.java:36) ~[classes/:na]
	at com.tanya.workrequests.api.WorkRequestController.lambda$processBatch$0(WorkRequestController.java:77) ~[classes/:na]
	at com.tanya.workrequests.api.BatchObserver.observe(BatchObserver.java:52) ~[classes/:na]
	at com.tanya.workrequests.api.WorkRequestController.processBatch(WorkRequestController.java:76) ~[classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:991) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:896) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590) ~[tomcat-embed-core-10.1.55.jar:6.0]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885) ~[spring-webmvc-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.TestDispatcherServlet.service(TestDispatcherServlet.java:72) ~[spring-test-6.2.19.jar:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658) ~[tomcat-embed-core-10.1.55.jar:6.0]
	at org.springframework.mock.web.MockFilterChain$ServletFilterProxy.doFilter(MockFilterChain.java:165) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.mock.web.MockFilterChain.doFilter(MockFilterChain.java:132) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.setup.MockMvcFilterDecorator.doFilter(MockMvcFilterDecorator.java:162) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.mock.web.MockFilterChain.doFilter(MockFilterChain.java:132) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.setup.MockMvcFilterDecorator.doFilter(MockMvcFilterDecorator.java:162) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.mock.web.MockFilterChain.doFilter(MockFilterChain.java:132) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.ServerHttpObservationFilter.doFilterInternal(ServerHttpObservationFilter.java:110) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.setup.MockMvcFilterDecorator.doFilter(MockMvcFilterDecorator.java:162) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.mock.web.MockFilterChain.doFilter(MockFilterChain.java:132) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.setup.MockMvcFilterDecorator.doFilter(MockMvcFilterDecorator.java:162) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.mock.web.MockFilterChain.doFilter(MockFilterChain.java:132) ~[spring-test-6.2.19.jar:6.2.19]
	at org.springframework.test.web.servlet.MockMvc.perform(MockMvc.java:201) ~[spring-test-6.2.19.jar:6.2.19]
	at com.tanya.workrequests.api.UnexpectedErrorTest.returnsAGeneric500WithoutLeakingTheCause(UnexpectedErrorTest.java:38) ~[test-classes/:na]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:775) ~[junit-platform-commons-1.12.2.jar:1.12.2]
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:479) ~[junit-platform-commons-1.12.2.jar:1.12.2]
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60) ~[junit-jupiter-engine-5.12.2.jar:5.12.2]
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:131) ~[junit-jupiter-engine-5.12.2.jar:5.12.2]
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:161) ~[junit-jupiter-engine-5.12.2.jar:5.12.2]
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMet
... (truncated)
```

## Your previous diff

```diff
diff --git a/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java b/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
--- a/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
+++ b/backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
@@ -1,9 +1,10 @@
 package com.tanya.workrequests.domain;
 
 public enum ProblemCode {
     NULL_REQUEST,
     MISSING_FIELD,
     INVALID_VALUE,
     DUPLICATE,
-    CONFLICTING_REQUEST_ID
+    CONFLICTING_REQUEST_ID,
+    STALE
 }
diff --git a/backend/src/main/java/com/tanya/workrequests/domain/Problem.java b/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
--- a/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
+++ b/backend/src/main/java/com/tanya/workrequests/domain/Problem.java
@@ -57,6 +57,15 @@ public record Problem(int inputIndex,
     public static Problem conflictingRequestId(int inputIndex, String requestId) {
         return new Problem(inputIndex, requestId, ProblemCode.CONFLICTING_REQUEST_ID, null, null, null, null,
                 "Request id " + requestId + " is used by requests with different content");
     }
 
+    /**
+     * A request whose age exceeds the configured maximum. The message quotes the configured
+     * threshold, not the request's actual age, so the same request reads the same every day.
+     */
+    public static Problem stale(int inputIndex, String requestId, long maxAgeDays) {
+        return new Problem(inputIndex, requestId, ProblemCode.STALE, null, null, null, null,
+                subject(requestId) + " is older than " + maxAgeDays + " days");
+    }
+
     private static String subject(String requestId) {
         return requestId == null ? "Request" : requestId;
     }
diff --git a/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java b/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java
--- a/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java
+++ b/backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java
@@ -6,19 +6,47 @@ import com.tanya.workrequests.domain.ProblemCode;
 import com.tanya.workrequests.domain.StageTrace;
 import com.tanya.workrequests.domain.WorkRequest;
 
+import java.time.Clock;
+import java.time.Duration;
 import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.List;
 import java.util.Map;
+import java.util.Objects;
 import java.util.stream.Collectors;
 
 /**
  * Rejects null, incomplete, and unreadable requests. Every missing or invalid field is its
  * own problem, so a request with two bad fields reports both. Blank strings count as missing.
+ *
+ * <p>When a {@link Clock} and a maximum age are supplied, requests older than that age are
+ * also rejected as STALE so they never reach deduplication or the queue. Staleness is checked
+ * only for requests that passed every field check, so a null {@code requestedAt} is reported
+ * as a missing field and nothing else.
  */
 public class WorkRequestValidator {
 
     static final String STAGE = "validate";
 
+    // Null clock means staleness checking is off; the no-argument constructor keeps the
+    // pre-staleness behaviour for callers that have no notion of "now".
+    private final Clock clock;
+    private final Duration maxAge;
+
+    public WorkRequestValidator() {
+        this.clock = null;
+        this.maxAge = null;
+    }
+
+    public WorkRequestValidator(Clock clock, Duration maxAge) {
+        this.clock = Objects.requireNonNull(clock, "clock");
+        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
+    }
+
     public StageResult validate(List<WorkRequest> batch) {
         return validate(batch, Map.of());
     }
@@ -34,10 +62,15 @@ public class WorkRequestValidator {
         for (int index = 0; index < batch.size(); index++) {
             WorkRequest request = batch.get(index);
             List<InvalidField> invalidFields = invalidFieldsByIndex.getOrDefault(index, List.of());
             List<Problem> requestProblems = problemsFor(index, request, invalidFields);
-            if (requestProblems.isEmpty()) {
-                valid.add(new IndexedRequest(index, request));
-            } else {
+            if (!requestProblems.isEmpty()) {
                 problems.addAll(requestProblems);
                 notes.add(rejectionNote(request, requestProblems));
+            } else if (isStale(request)) {
+                problems.add(Problem.stale(index, request.requestId(), maxAge.toDays()));
+                notes.add(request.requestId() + " rejected: stale");
+            } else {
+                valid.add(new IndexedRequest(index, request));
             }
         }
         // Sorted, and free of input indices, so the trace is identical whatever the input order.
         notes.sort(Comparator.naturalOrder());
 
         StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
         return new StageResult(valid, problems, trace);
     }
 
+    /** Exactly {@code maxAge} old is still fresh; future-dated requests are never stale. */
+    private boolean isStale(WorkRequest request) {
+        if (clock == null) {
+            return false;
+        }
+        return Duration.between(request.requestedAt(), clock.instant()).compareTo(maxAge) > 0;
+    }
+
     private static List<Problem> problemsFor(int index, WorkRequest request, List<InvalidField> invalidFields) {
         if (request == null) {
             return List.of(Problem.nullRequest(index));
         }
diff --git a/backend/src/main/java/com/tanya/workrequests/config/StalenessProperties.java b/backend/src/main/java/com/tanya/workrequests/config/StalenessProperties.java
new file mode 100644
--- /dev/null
+++ b/backend/src/main/java/com/tanya/workrequests/config/StalenessProperties.java
@@ -0,0 +1,25 @@
+package com.tanya.workrequests.config;
+
+import org.springframework.boot.context.properties.ConfigurationProperties;
+import org.springframework.boot.context.properties.bind.DefaultValue;
+
+import java.time.Duration;
+import java.util.Objects;
+
+/**
+ * How old a request may be before it is flagged as stale and kept out of the queue.
+ *
+ * <p>Bound from {@code app.staleness.*}. A non-positive maximum age would reject every
+ * request, so it is refused at startup rather than silently emptying the queue.
+ */
+@ConfigurationProperties("app.staleness")
+public record StalenessProperties(@DefaultValue("30d") Duration maxAge) {
+
+    public StalenessProperties {
+        Objects.requireNonNull(maxAge, "maxAge");
+        if (maxAge.isZero() || maxAge.isNegative()) {
+            throw new IllegalArgumentException("app.staleness.max-age must be positive");
+        }
+    }
+}
diff --git a/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java b/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
--- a/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
+++ b/backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
@@ -1,13 +1,15 @@
 package com.tanya.workrequests.config;
 
 import com.tanya.workrequests.processing.DescriptionNormalizer;
 import com.tanya.workrequests.processing.DuplicateResolver;
 import com.tanya.workrequests.processing.WorkRequestProcessor;
 import com.tanya.workrequests.processing.WorkRequestSorter;
 import com.tanya.workrequests.processing.WorkRequestValidator;
 import org.springframework.context.annotation.Bean;
 import org.springframework.context.annotation.Configuration;
 
+import java.time.Clock;
+
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
 
+    /** UTC everywhere, so ageing does not shift with the server's time zone. */
     @Bean
-    WorkRequestValidator workRequestValidator() {
-        return new WorkRequestValidator();
+    Clock clock() {
+        return Clock.systemUTC();
+    }
+
+    @Bean
+    WorkRequestValidator workRequestValidator(Clock clock, StalenessProperties stalenessProperties) {
+        return new WorkRequestValidator(clock, stalenessProperties.maxAge());
     }
 
     @Bean
     DuplicateResolver duplicateResolver(DescriptionNormalizer descriptionNormalizer) {
         return new DuplicateResolver(descriptionNormalizer);
     }

```

## Other files the failure points at (current contents)

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
