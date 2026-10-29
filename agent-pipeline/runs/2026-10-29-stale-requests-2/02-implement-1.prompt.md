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

# Classes named in the criteria that don't exist yet (create them)

- StalenessProperties



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
