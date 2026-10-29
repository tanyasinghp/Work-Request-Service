# summarize: prompt

## System

You write the pull-request description for a change produced by an automated pipeline, for a
human reviewer who will decide whether to merge it. Be factual and brief; never claim more
than the diff and the test results show.

Use these sections in Markdown: "## What changed", "## Criteria coverage" (each criterion and
the test that checks it, or "not covered"), "## Test results" (each gate attempt), and
"## Worth a closer look" (assumptions, risky spots, anything the reviewer should verify).
Return JSON with one field, `markdown`.


## User

# Story

# Report future timestamps

As an integrator sending batches from several sites, I want a SUSPICIOUS_TIMESTAMP problem
for any request whose requestedAt is in the future, so that clock or time-zone bugs in a
sending system are caught instead of distorting the queue order.

"In the future" is relative to the time the batch is processed, and the check must be
testable without depending on the real clock.

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

# Gate attempts

- Attempt 1: passed

# Tests added (written from the criteria only)

- SuspiciousTimestampProblemTest.declaresSuspiciousTimestampLastAfterConflictingRequestId
- SuspiciousTimestampProblemTest.buildsASuspiciousTimestampProblemForTheRequestedAtField
- SuspiciousTimestampProblemTest.keepsTheInputIndexAndRequestIdItWasGiven
- SuspiciousTimestampProblemTest.leavesRelatedRequestIdAndEscalationFieldsNull
- SuspiciousTimestampProblemTest.namesTheRequestAndTheInstantInTheMessage
- SuspiciousTimestampProblemTest.fallsBackToAGenericSubjectWhenTheRequestIdIsNull
- SuspiciousTimestampProblemTest.rendersTheInstantWithInstantToStringSoSubSecondPrecisionIsKept
- FutureRequestedAtValidationTest.reportsOneSuspiciousTimestampProblemForAFutureDatedRequest
- FutureRequestedAtValidationTest.dropsTheFutureDatedRequestFromTheStageResultRequests
- FutureRequestedAtValidationTest.countsOnlyAcceptedRequestsInTheValidateTrace
- FutureRequestedAtValidationTest.acceptsARequestDatedExactlyAtTheClockInstant
- FutureRequestedAtValidationTest.acceptsARequestDatedOneMillisecondBeforeTheClockInstant
- FutureRequestedAtValidationTest.flagsARequestDatedOneMillisecondAfterTheClockInstantSoThereIsNoToleranceWindow
- FutureRequestedAtValidationTest.reportsOnlyMissingFieldWhenRequestedAtIsNull
- FutureRequestedAtValidationTest.reportsOnlyInvalidValueWhenRequestedAtCouldNotBeParsed
- FutureRequestedAtValidationTest.reportsExistingFieldProblemsBeforeTheSuspiciousTimestampProblem
- FutureRequestedAtValidationTest.reportsBothProblemsForTheSameInputIndex
- FutureRequestedAtValidationTest.writesAnIndexFreeNoteListingMissingAndFutureReasons
- FutureRequestedAtValidationTest.writesAFutureRequestedAtNoteForAnOtherwiseValidRequest
- FutureRequestedAtValidationTest.labelsABlankIdRequestAsRequestWithoutIdInTheNote
- FutureRequestedAtValidationTest.keepsNotesSortedWhenAFutureRequestIsRejected
- FutureRequestedAtValidationTest.noArgConstructorFlagsARequestDatedAfterTheRealNow
- FutureRequestedAtValidationTest.noArgConstructorAcceptsARequestDatedBeforeTheRealNow
- FutureRequestedAtValidationTest.acceptsSampleBatchDatesUnderTheSystemClockBecauseTheyAreInThePast
- FutureRequestedAtValidationTest.rejectsANullClock
- FutureRequestedAtValidationTest.readsTheClockOncePerValidateCall
- FutureRequestedAtValidationTest.comparesEveryRequestInABatchAgainstTheSameInstant
- FutureRequestedAtPipelineTest.reportsOnlySuspiciousTimestampProblemsForTheFutureDatedRequests
- FutureRequestedAtPipelineTest.reportsNoDuplicateProblemForTheFutureTwin
- FutureRequestedAtPipelineTest.neverEscalatesAPriorityFromTheFutureTwin
- FutureRequestedAtPipelineTest.keepsTheThreeStageTraceWhenAFutureRequestIsRejected
- FutureRequestedAtPipelineTest.passesOnlyTheAcceptedRequestsIntoDeduplicate
- FutureRequestedAtPipelineTest.producesTheSameQueueForEveryInputOrder
- FutureRequestedAtPipelineTest.producesTheSameProblemsForEveryInputOrder
- FutureRequestedAtPipelineTest.producesTheSameTraceNotesForEveryInputOrder
- FutureRequestedAtPipelineTest.ordersSuspiciousTimestampProblemsByInputIndex
- FutureRequestedAtPipelineTest.keepsEveryProblemCodeIncludingSuspiciousTimestampReachable
- ClockBeanWiringTest.exposesAClockBeanNamedClock
- ClockBeanWiringTest.theClockBeanIsTheSystemUtcClock
- ClockBeanWiringTest.buildsTheValidatorWithTheContextClockSoFutureRequestsAreFlagged
- ClockBeanWiringTest.buildsTheValidatorWithTheContextClockSoPastRequestsAreAccepted
- BatchObserverSuspiciousTimestampTest.registersOneRejectionCounterPerProblemCode
- BatchObserverSuspiciousTimestampTest.countsAFutureDatedRequestUnderTheSuspiciousTimestampCode

# Final diff against main

```diff
diff --git a/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.prompt.md b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.prompt.md
new file mode 100644
index 0000000..588b39f
--- /dev/null
+++ b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.prompt.md
@@ -0,0 +1,567 @@
+# enrich: prompt
+
+## System
+
+You turn a plain-English user story for a Java service into acceptance criteria that two
+people could implement and test independently and still agree.
+
+Return only JSON matching the given schema. Each criterion is a concrete, testable
+given/when/then. Name the exact identifiers the change introduces (enum constants, method or
+factory names, configuration property keys and their defaults, problem messages where they
+matter), because the implementation and the tests are written separately from these criteria
+alone. State every behavioural decision the story leaves open. List in affectedClasses the
+simple names of the classes that must change or be created, including wiring/config classes.
+Keep the scope to what the story asks; put tempting extras in outOfScope.
+
+
+## User
+
+# Story
+
+# Report future timestamps
+
+As an integrator sending batches from several sites, I want a SUSPICIOUS_TIMESTAMP problem
+for any request whose requestedAt is in the future, so that clock or time-zone bugs in a
+sending system are caught instead of distorting the queue order.
+
+"In the future" is relative to the time the batch is processed, and the check must be
+testable without depending on the real clock.
+
+# Codebase notes
+
+# Work Request Service: what you need to know
+
+Java 21, Spring Boot 3.5, Maven. Source in `backend/src/main/java/com/tanya/workrequests/`,
+tests in `backend/src/test/java/com/tanya/workrequests/`. Packages: `domain`, `processing`,
+`api` (+ `api.dto`), `config`.
+
+## Domain (records and enums, immutable)
+
+- `WorkRequest(requestId, equipmentId, Priority priority, Instant requestedAt, String description)`,
+  with `withPriority(Priority)`.
+- `Priority`: LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4); compare with `weight()`, never `ordinal()`.
+- `ProblemCode`: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID.
+- `Problem(inputIndex, requestId, code, field, relatedRequestId, escalatedFrom, escalatedTo, message)`,
+  built through static factories (`Problem.missingField(...)`, `Problem.duplicate(...)`, ...).
+  `escalatedFrom`/`escalatedTo` are set together or not at all. Messages name request ids but
+  never the input index.
+- `StageTrace(stage, in, out, notes)`; `ProcessingResult(accepted, problems, trace)`.
+
+## Processing pipeline
+
+`WorkRequestProcessor(validator, duplicateResolver, sorter)` runs three stages in order and
+returns a `ProcessingResult` whose trace always has exactly three entries: `validate`,
+`deduplicate`, `prioritize`.
+
+- `WorkRequestValidator.validate(List<WorkRequest>)` and `validate(List, Map<Integer, List<InvalidField>>)`
+  return a `StageResult(List<IndexedRequest> requests, List<Problem> problems, StageTrace trace)`.
+- `DuplicateResolver(DescriptionNormalizer).resolve(List<IndexedRequest>)`: request-id conflicts,
+  then duplicates (earliest kept, priority escalated to the group's highest).
+- `WorkRequestSorter.sort(List<IndexedRequest>)`: priority weight desc, requestedAt asc, requestId asc.
+- `IndexedRequest(inputIndex, request)` carries the original position through the stages.
+
+Rules the tests pin down:
+
+- Problems are data, never exceptions; one bad request never fails a batch.
+- Output must be identical regardless of input order (tests shuffle batches 100 times). Trace
+  notes are sorted text and contain no input indices. Problems are listed in stage order, then
+  by input index within a stage.
+- Validation runs before deduplication.
+
+## Wiring and configuration
+
+- `config/ProcessingConfig` (`@Configuration(proxyBeanMethods = false)`) creates every stage and
+  the processor as `@Bean`s. Main code has no `@Component` annotations.
+- Settings are `@ConfigurationProperties` records with `@DefaultValue`, e.g.
+  `BatchProperties(@DefaultValue("5000") int maxSize)` bound from `app.batch.*` in
+  `src/main/resources/application.yml`; the application class has `@ConfigurationPropertiesScan`.
+- There is no `java.time.Clock` bean yet. Code that needs "now" should take a `Clock` rather than
+  calling `Instant.now()` directly, so tests can use `Clock.fixed(...)`.
+
+## Compatibility hazards
+
+- Many existing tests call `new WorkRequestValidator()`, `new DuplicateResolver(new DescriptionNormalizer())`,
+  `new WorkRequestSorter()` and `new WorkRequestProcessor(validator, resolver, sorter)` directly.
+  Keep those constructors working (add overloads rather than changing signatures).
+- `SampleBatchTest` pins the 13-request sample batch (`src/main/resources/sample-batch.json`,
+  all requestedAt on 2026-10-20) to an exact queue, exact problems, and trace counts 13 → 10 → 6.
+- `OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem` asserts that a generated batch
+  produces every `ProblemCode` value, and `BatchObserverTest` expects one metrics counter per code.
+- `EdgeCaseTest`, `WorkRequestControllerTest` and `ApiErrorHandlingTest` pin API and edge-case behaviour.
+
+## Test conventions
+
+JUnit 5, AssertJ, Mockito. Test names describe behaviour (`keepsEarliestDuplicateAndEscalatesPriority`).
+In package `com.tanya.workrequests.processing` there is a package-private helper `TestRequests`:
+`request(id, equipmentId, priority, "2026-10-20T08:00:00Z", description)`, `valid(id)`, and
+`indexed(WorkRequest...)`. Test classes must end in `Test` to run.
+
+## Code conventions
+
+Records for immutable data, no Lombok, constructor injection, small single-purpose classes,
+meaningful names, comments that explain why rather than what.
+
+
+# Source files in the backend
+
+backend/src/main/java/com/tanya/workrequests/WorkRequestServiceApplication.java
+backend/src/main/java/com/tanya/workrequests/api/ApiExceptionHandler.java
+backend/src/main/java/com/tanya/workrequests/api/BatchObserver.java
+backend/src/main/java/com/tanya/workrequests/api/InvalidBatchException.java
+backend/src/main/java/com/tanya/workrequests/api/RequestMapper.java
+backend/src/main/java/com/tanya/workrequests/api/SampleBatchSource.java
+backend/src/main/java/com/tanya/workrequests/api/WorkRequestController.java
+backend/src/main/java/com/tanya/workrequests/api/dto/ApiError.java
+backend/src/main/java/com/tanya/workrequests/api/dto/BatchResponse.java
+backend/src/main/java/com/tanya/workrequests/api/dto/ProblemDto.java
+backend/src/main/java/com/tanya/workrequests/api/dto/StageTraceDto.java
+backend/src/main/java/com/tanya/workrequests/api/dto/WorkRequestDto.java
+backend/src/main/java/com/tanya/workrequests/config/ApiConfig.java
+backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java
+backend/src/main/java/com/tanya/workrequests/config/CorsConfig.java
+backend/src/main/java/com/tanya/workrequests/config/CorsProperties.java
+backend/src/main/java/com/tanya/workrequests/config/MetricsConfig.java
+backend/src/main/java/com/tanya/workrequests/config/OpenApiConfig.java
+backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java
+backend/src/main/java/com/tanya/workrequests/domain/InvalidField.java
+backend/src/main/java/com/tanya/workrequests/domain/ParsedBatch.java
+backend/src/main/java/com/tanya/workrequests/domain/Priority.java
+backend/src/main/java/com/tanya/workrequests/domain/Problem.java
+backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java
+backend/src/main/java/com/tanya/workrequests/domain/ProcessingResult.java
+backend/src/main/java/com/tanya/workrequests/domain/StageTrace.java
+backend/src/main/java/com/tanya/workrequests/domain/WorkRequest.java
+backend/src/main/java/com/tanya/workrequests/processing/DescriptionNormalizer.java
+backend/src/main/java/com/tanya/workrequests/processing/DuplicateResolver.java
+backend/src/main/java/com/tanya/workrequests/processing/IndexedRequest.java
+backend/src/main/java/com/tanya/workrequests/processing/StageResult.java
+backend/src/main/java/com/tanya/workrequests/processing/WorkRequestProcessor.java
+backend/src/main/java/com/tanya/workrequests/processing/WorkRequestSorter.java
+backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java
+backend/src/test/java/com/tanya/workrequests/SmokeTest.java
+backend/src/test/java/com/tanya/workrequests/api/ApiErrorHandlingTest.java
+backend/src/test/java/com/tanya/workrequests/api/BatchObserverTest.java
+backend/src/test/java/com/tanya/workrequests/api/CorsAndOpenApiTest.java
+backend/src/test/java/com/tanya/workrequests/api/RequestMapperTest.java
+backend/src/test/java/com/tanya/workrequests/api/SampleBatchSourceTest.java
+backend/src/test/java/com/tanya/workrequests/api/UnexpectedErrorTest.java
+backend/src/test/java/com/tanya/workrequests/api/WorkRequestControllerTest.java
+backend/src/test/java/com/tanya/workrequests/config/ApplicationWiringTest.java
+backend/src/test/java/com/tanya/workrequests/config/BatchPropertiesTest.java
+backend/src/test/java/com/tanya/workrequests/config/CorsPropertiesTest.java
+backend/src/test/java/com/tanya/workrequests/domain/PriorityTest.java
+backend/src/test/java/com/tanya/workrequests/domain/ProblemTest.java
+backend/src/test/java/com/tanya/workrequests/processing/DescriptionNormalizerTest.java
+backend/src/test/java/com/tanya/workrequests/processing/DuplicateResolverTest.java
+backend/src/test/java/com/tanya/workrequests/processing/EdgeCaseTest.java
+backend/src/test/java/com/tanya/workrequests/processing/OrderIndependenceTest.java
+backend/src/test/java/com/tanya/workrequests/processing/SampleBatch.java
+backend/src/test/java/com/tanya/workrequests/processing/SampleBatchTest.java
+backend/src/test/java/com/tanya/workrequests/processing/TestRequests.java
+backend/src/test/java/com/tanya/workrequests/processing/WorkRequestProcessorTest.java
+backend/src/test/java/com/tanya/workrequests/processing/WorkRequestSorterTest.java
+backend/src/test/java/com/tanya/workrequests/processing/WorkRequestValidatorTest.java
+
+# Key existing files
+
+=== backend/src/main/java/com/tanya/workrequests/domain/ProblemCode.java ===
+```java
+package com.tanya.workrequests.domain;
+
+public enum ProblemCode {
+    NULL_REQUEST,
+    MISSING_FIELD,
+    INVALID_VALUE,
+    DUPLICATE,
+    CONFLICTING_REQUEST_ID
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/domain/Problem.java ===
+```java
+package com.tanya.workrequests.domain;
+
+import java.util.Objects;
+
+/**
+ * Something wrong with one request in a batch. Problems are returned as data so that one
+ * bad request never fails the whole batch.
+ *
+ * <p>{@code requestId}, {@code field}, {@code relatedRequestId}, {@code escalatedFrom}, and
+ * {@code escalatedTo} are null when they don't apply. Messages never mention the input index,
+ * so the same problem reads the same no matter where the request sat in the batch.
+ *
+ * <p>{@code escalatedFrom}/{@code escalatedTo} are set only on the duplicate whose priority
+ * raised the kept request, so the UI can show escalations without parsing message text.
+ */
+public record Problem(int inputIndex,
+                      String requestId,
+                      ProblemCode code,
+                      String field,
+                      String relatedRequestId,
+                      Priority escalatedFrom,
+                      Priority escalatedTo,
+                      String message) {
+
+    // Long enough to recognise a bad value, short enough that a huge payload can't bloat the response.
+    static final int MAX_VALUE_LENGTH = 40;
+
+    public Problem {
+        Objects.requireNonNull(code, "code");
+        Objects.requireNonNull(message, "message");
+        if ((escalatedFrom == null) != (escalatedTo == null)) {
+            throw new IllegalArgumentException("escalatedFrom and escalatedTo must be set together");
+        }
+    }
+
+    public static Problem nullRequest(int inputIndex) {
+        return new Problem(inputIndex, null, ProblemCode.NULL_REQUEST, null, null, null, null,
+                "Request is null");
+    }
+
+    public static Problem missingField(int inputIndex, String requestId, String field) {
+        return new Problem(inputIndex, requestId, ProblemCode.MISSING_FIELD, field, null, null, null,
+                subject(requestId) + " is missing " + field);
+    }
+
+    public static Problem invalidValue(int inputIndex, String requestId, String field, String rawValue) {
+        return new Problem(inputIndex, requestId, ProblemCode.INVALID_VALUE, field, null, null, null,
+                subject(requestId) + " has invalid " + field + " '" + truncate(rawValue) + "'");
+    }
+
+    public static Problem duplicate(int inputIndex, String requestId, String keptRequestId) {
+        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, null, null,
+                "Duplicate of " + keptRequestId);
+    }
+
+    /** A byte-for-byte resend of a request that was kept; it points at itself. */
+    public static Problem exactRepeat(int inputIndex, String requestId) {
+        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, requestId, null, null,
+                "Exact repeat of " + requestId);
+    }
+
+    public static Problem duplicateWithEscalation(int inputIndex, String requestId, String keptRequestId,
+                                                  Priority from, Priority to) {
+        return new Problem(inputIndex, requestId, ProblemCode.DUPLICATE, null, keptRequestId, from, to,
+                "Duplicate of " + keptRequestId + "; priority escalated " + from + " to " + to);
+    }
+
+    public static Problem conflictingRequestId(int inputIndex, String requestId) {
+        return new Problem(inputIndex, requestId, ProblemCode.CONFLICTING_REQUEST_ID, null, null, null, null,
+                "Request id " + requestId + " is used by requests with different content");
+    }
+
+    private static String subject(String requestId) {
+        return requestId == null ? "Request" : requestId;
+    }
+
+    private static String truncate(String value) {
+        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
+    }
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/domain/WorkRequest.java ===
+```java
+package com.tanya.workrequests.domain;
+
+import java.time.Instant;
+
+/**
+ * A maintenance work request as received. Fields may be null or blank here on purpose:
+ * the validator reports those as problems instead of the constructor throwing.
+ */
+public record WorkRequest(String requestId,
+                          String equipmentId,
+                          Priority priority,
+                          Instant requestedAt,
+                          String description) {
+
+    public WorkRequest withPriority(Priority newPriority) {
+        return new WorkRequest(requestId, equipmentId, newPriority, requestedAt, description);
+    }
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/processing/WorkRequestValidator.java ===
+```java
+package com.tanya.workrequests.processing;
+
+import com.tanya.workrequests.domain.InvalidField;
+import com.tanya.workrequests.domain.Problem;
+import com.tanya.workrequests.domain.ProblemCode;
+import com.tanya.workrequests.domain.StageTrace;
+import com.tanya.workrequests.domain.WorkRequest;
+
+import java.util.ArrayList;
+import java.util.Comparator;
+import java.util.List;
+import java.util.Map;
+import java.util.stream.Collectors;
+
+/**
+ * Rejects null, incomplete, and unreadable requests. Every missing or invalid field is its
+ * own problem, so a request with two bad fields reports both. Blank strings count as missing.
+ */
+public class WorkRequestValidator {
+
+    static final String STAGE = "validate";
+
+    public StageResult validate(List<WorkRequest> batch) {
+        return validate(batch, Map.of());
+    }
+
+    /**
+     * @param invalidFieldsByIndex fields that were present in the raw input but unreadable;
+     *        they are null in {@code batch} and are reported as INVALID_VALUE, not MISSING_FIELD
+     */
+    public StageResult validate(List<WorkRequest> batch, Map<Integer, List<InvalidField>> invalidFieldsByIndex) {
+        List<IndexedRequest> valid = new ArrayList<>();
+        List<Problem> problems = new ArrayList<>();
+        List<String> notes = new ArrayList<>();
+
+        for (int index = 0; index < batch.size(); index++) {
+            WorkRequest request = batch.get(index);
+            List<InvalidField> invalidFields = invalidFieldsByIndex.getOrDefault(index, List.of());
+            List<Problem> requestProblems = problemsFor(index, request, invalidFields);
+            if (requestProblems.isEmpty()) {
+                valid.add(new IndexedRequest(index, request));
+            } else {
+                problems.addAll(requestProblems);
+                notes.add(rejectionNote(request, requestProblems));
+            }
+        }
+        // Sorted, and free of input indices, so the trace is identical whatever the input order.
+        notes.sort(Comparator.naturalOrder());
+
+        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
+        return new StageResult(valid, problems, trace);
+    }
+
+    private static List<Problem> problemsFor(int index, WorkRequest request, List<InvalidField> invalidFields) {
+        if (request == null) {
+            return List.of(Problem.nullRequest(index));
+        }
+        Map<String, String> invalid = invalidFields.stream()
+                .collect(Collectors.toMap(InvalidField::field, InvalidField::rawValue, (first, second) -> first));
+        // A blank id is reported as no id at all, matching "blank counts as missing".
+        String requestId = isBlank(request.requestId()) ? null : request.requestId();
+
+        List<Problem> problems = new ArrayList<>();
+        check(problems, index, requestId, "requestId", requestId == null, invalid);
+        check(problems, index, requestId, "equipmentId", isBlank(request.equipmentId()), invalid);
+        check(problems, index, requestId, "priority", request.priority() == null, invalid);
+        check(problems, index, requestId, "requestedAt", request.requestedAt() == null, invalid);
+        check(problems, index, requestId, "description", isBlank(request.description()), invalid);
+        return problems;
+    }
+
+    private static void check(List<Problem> problems, int index, String requestId, String field,
+                              boolean missing, Map<String, String> invalid) {
+        if (invalid.containsKey(field)) {
+            problems.add(Problem.invalidValue(index, requestId, field, invalid.get(field)));
+        } else if (missing) {
+            problems.add(Problem.missingField(index, requestId, field));
+        }
+    }
+
+    private static boolean isBlank(String value) {
+        return value == null || value.isBlank();
+    }
+
+    private static String rejectionNote(WorkRequest request, List<Problem> problems) {
+        if (request == null) {
+            return "Null request rejected";
+        }
+        Problem first = problems.get(0);
+        String label = first.requestId() != null ? first.requestId() : "Request without id";
+        String reasons = problems.stream()
+                .map(problem -> (problem.code() == ProblemCode.INVALID_VALUE ? "invalid " : "missing ") + problem.field())
+                .collect(Collectors.joining(", "));
+        return label + " rejected: " + reasons;
+    }
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/processing/WorkRequestProcessor.java ===
+```java
+package com.tanya.workrequests.processing;
+
+import com.tanya.workrequests.domain.ParsedBatch;
+import com.tanya.workrequests.domain.ProcessingResult;
+import com.tanya.workrequests.domain.WorkRequest;
+
+import java.util.List;
+import java.util.Objects;
+
+/**
+ * Runs the stages in order: validate, deduplicate, prioritize. Each stage only sees what
+ * the previous stage passed on; the processor just wires them and merges their output.
+ */
+public class WorkRequestProcessor {
+
+    private final WorkRequestValidator validator;
+    private final DuplicateResolver duplicateResolver;
+    private final WorkRequestSorter sorter;
+
+    public WorkRequestProcessor(WorkRequestValidator validator,
+                                DuplicateResolver duplicateResolver,
+                                WorkRequestSorter sorter) {
+        this.validator = Objects.requireNonNull(validator, "validator");
+        this.duplicateResolver = Objects.requireNonNull(duplicateResolver, "duplicateResolver");
+        this.sorter = Objects.requireNonNull(sorter, "sorter");
+    }
+
+    public ProcessingResult process(List<WorkRequest> batch) {
+        // Null is treated as empty and still runs every stage, so the trace always has three entries.
+        List<WorkRequest> requests = batch == null ? List.of() : batch;
+        return runFrom(validator.validate(requests));
+    }
+
+    /** Processes a batch read from raw input, reporting unreadable fields as INVALID_VALUE. */
+    public ProcessingResult process(ParsedBatch batch) {
+        Objects.requireNonNull(batch, "batch");
+        return runFrom(validator.validate(batch.requests(), batch.invalidFieldsByIndex()));
+    }
+
+    private ProcessingResult runFrom(StageResult validated) {
+        StageResult deduplicated = duplicateResolver.resolve(validated.requests());
+        StageResult prioritized = sorter.sort(deduplicated.requests());
+        List<StageResult> stages = List.of(validated, deduplicated, prioritized);
+
+        // Each stage already orders its own problems by input index; concatenating keeps stage order.
+        return new ProcessingResult(
+                prioritized.requests().stream().map(IndexedRequest::request).toList(),
+                stages.stream().flatMap(stage -> stage.problems().stream()).toList(),
+                stages.stream().map(StageResult::trace).toList());
+    }
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/config/ProcessingConfig.java ===
+```java
+package com.tanya.workrequests.config;
+
+import com.tanya.workrequests.processing.DescriptionNormalizer;
+import com.tanya.workrequests.processing.DuplicateResolver;
+import com.tanya.workrequests.processing.WorkRequestProcessor;
+import com.tanya.workrequests.processing.WorkRequestSorter;
+import com.tanya.workrequests.processing.WorkRequestValidator;
+import org.springframework.context.annotation.Bean;
+import org.springframework.context.annotation.Configuration;
+
+/**
+ * Wires the framework-free processing classes. Every stage is stateless, so one shared
+ * instance of each (Spring's default singleton scope) is safe across concurrent requests.
+ */
+@Configuration(proxyBeanMethods = false)
+public class ProcessingConfig {
+
+    @Bean
+    DescriptionNormalizer descriptionNormalizer() {
+        return new DescriptionNormalizer();
+    }
+
+    @Bean
+    WorkRequestValidator workRequestValidator() {
+        return new WorkRequestValidator();
+    }
+
+    @Bean
+    DuplicateResolver duplicateResolver(DescriptionNormalizer descriptionNormalizer) {
+        return new DuplicateResolver(descriptionNormalizer);
+    }
+
+    @Bean
+    WorkRequestSorter workRequestSorter() {
+        return new WorkRequestSorter();
+    }
+
+    @Bean
+    WorkRequestProcessor workRequestProcessor(WorkRequestValidator validator,
+                                              DuplicateResolver duplicateResolver,
+                                              WorkRequestSorter sorter) {
+        return new WorkRequestProcessor(validator, duplicateResolver, sorter);
+    }
+}
+```
+
+=== backend/src/main/java/com/tanya/workrequests/config/BatchProperties.java ===
+```java
+package com.tanya.workrequests.config;
+
+import org.springframework.boot.context.properties.ConfigurationProperties;
+import org.springframework.boot.context.properties.bind.DefaultValue;
+
+/** {@code app.batch.*}: guards that protect the service from oversized input. */
+@ConfigurationProperties("app.batch")
+public record BatchProperties(@DefaultValue("5000") int maxSize) {
+
+    public BatchProperties {
+        if (maxSize < 1) {
+            throw new IllegalArgumentException("app.batch.max-size must be at least 1, was " + maxSize);
+        }
+    }
+}
+```
+
+
+## Output schema
+
+```json
+{
+  "title": "Acceptance criteria for one user story",
+  "type": "object",
+  "additionalProperties": false,
+  "required": [
+    "title",
+    "criteria",
+    "affectedClasses",
+    "outOfScope"
+  ],
+  "properties": {
+    "title": {
+      "type": "string",
+      "description": "Short imperative title, e.g. 'Flag requests older than 30 days as stale'."
+    },
+    "criteria": {
+      "type": "array",
+      "description": "Testable criteria. Name concrete identifiers (enum constants, methods, properties) so the implementation and the independently written tests agree.",
+      "items": {
+        "type": "object",
+        "additionalProperties": false,
+        "required": [
+          "given",
+          "when",
+          "then"
+        ],
+        "properties": {
+          "given": {
+            "type": "string"
+          },
+          "when": {
+            "type": "string"
+          },
+          "then": {
+            "type": "string"
+          }
+        }
+      }
+    },
+    "affectedClasses": {
+      "type": "array",
+      "description": "Simple names of the Java classes to change or create, e.g. 'WorkRequestValidator'.",
+      "items": {
+        "type": "string"
+      }
+    },
+    "outOfScope": {
+      "type": "array",
+      "description": "Things this story deliberately does not do.",
+      "items": {
+        "type": "string"
+      }
+    }
+  }
+}
+```
diff --git a/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.response.json b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.response.json
new file mode 100644
index 0000000..f76c970
--- /dev/null
+++ b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/01-enrich.response.json
@@ -0,0 +1,266 @@
+{
+  "model": "claude-opus-5",
+  "costUsd": 0.26431400000000005,
+  "text": "{\"criteria\":[{\"given\":\"a validator built with Clock.fixed(Instant.parse(\\\"2026-10-20T12:00:00Z\\\"), ZoneOffset.UTC) and a batch containing one otherwise-valid request whose requestedAt is 2026-10-20T12:00:01Z\",\"when\":\"validate(batch) runs\",\"then\":\"the StageResult problems contain exactly one Problem with code ProblemCode.SUSPICIOUS_TIMESTAMP, field \\\"requestedAt\\\", the request's requestId, inputIndex 0, null relatedRequestId/escalatedFrom/escalatedTo, and the request is NOT in StageResult.requests()\"},{\"given\":\"a validator built with a clock fixed at 2026-10-20T12:00:00Z\",\"when\":\"validating requests whose requestedAt is exactly 2026-10-20T12:00:00Z, or earlier (e.g. 2026-10-20T11:59:59.999Z)\",\"then\":\"no SUSPICIOUS_TIMESTAMP problem is reported and the requests are accepted; only an instant strictly after the clock instant (including 2026-10-20T12:00:00.000000001Z) is treated as future \u2014 there is no tolerance window\"},{\"given\":\"the clock instant is 2026-10-20T12:00:00Z\",\"when\":\"a request WR-1 with requestedAt 2026-10-21T08:30:00Z is validated\",\"then\":\"the problem message is exactly \\\"WR-1 has requestedAt 2026-10-21T08:30:00Z in the future\\\" (requestId, then \\\" has requestedAt \\\", the Instant.toString() of requestedAt, then \\\" in the future\\\"), and it never mentions the input index\"},{\"given\":\"a request whose requestId is null or blank and whose requestedAt is in the future\",\"when\":\"it is validated\",\"then\":\"a SUSPICIOUS_TIMESTAMP problem is still reported with requestId null and message \\\"Request has requestedAt <instant> in the future\\\", in addition to the MISSING_FIELD problem for requestId\"},{\"given\":\"a request with requestedAt null, or with requestedAt supplied through invalidFieldsByIndex as an unreadable raw value\",\"when\":\"it is validated\",\"then\":\"only MISSING_FIELD (respectively INVALID_VALUE) is reported for field \\\"requestedAt\\\" and no SUSPICIOUS_TIMESTAMP problem is produced, because the future check needs a parsed instant\"},{\"given\":\"a request that is missing equipmentId and also has a future requestedAt\",\"when\":\"it is validated\",\"then\":\"both problems are reported for that input index, with the MISSING_FIELD problems (produced in field order requestId, equipmentId, priority, requestedAt, description) first and the SUSPICIOUS_TIMESTAMP problem last for that request\"},{\"given\":\"a batch where a future-dated request is rejected\",\"when\":\"validate completes\",\"then\":\"the validate StageTrace has in = batch size, out = number of accepted requests (the future-dated one excluded), and notes contain the sorted, index-free entry \\\"WR-1 rejected: future requestedAt\\\" (label \\\"Request without id\\\" when the id is blank), reusing the existing \\\"<label> rejected: <reasons>\\\" format with reasons joined by \\\", \\\"\"},{\"given\":\"a batch containing two requests with identical content, one dated in the past and one dated in the future, processed by WorkRequestProcessor with a fixed clock\",\"when\":\"process(batch) runs\",\"then\":\"the future-dated request is rejected in the validate stage with SUSPICIOUS_TIMESTAMP, it never reaches the deduplicate stage (no DUPLICATE problem and no priority escalation is reported for it), and the trace still has exactly three entries validate, deduplicate, prioritize\"},{\"given\":\"a batch containing several future-dated requests at different positions, shuffled repeatedly\",\"when\":\"process(batch) runs with the same fixed clock\",\"then\":\"the accepted queue, the problem list and the trace notes are identical for every ordering; the SUSPICIOUS_TIMESTAMP problems appear in the validate-stage block ordered by inputIndex\"},{\"given\":\"the clock is read for the batch\",\"when\":\"validate(List) or validate(List, Map) is called\",\"then\":\"clock.instant() is consulted once per call so every request in one batch is compared against the same instant\"},{\"given\":\"existing code and tests that call new WorkRequestValidator()\",\"when\":\"the class is compiled and used\",\"then\":\"the no-arg constructor still exists and behaves as if constructed with Clock.systemUTC(); a new constructor WorkRequestValidator(Clock clock) is added and throws NullPointerException with message \\\"clock\\\" when given null\"},{\"given\":\"the Spring application context\",\"when\":\"it starts with no overriding bean\",\"then\":\"ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC(), and the workRequestValidator bean is built with that Clock; no new configuration property is introduced\"},{\"given\":\"the ProblemCode enum\",\"when\":\"ProblemCode.values() is read\",\"then\":\"it contains SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID), so existing constants keep their order, BatchObserver gets one counter per code including the new one, and ProblemDto serialises the code as the string \\\"SUSPICIOUS_TIMESTAMP\\\" with field \\\"requestedAt\\\"\"},{\"given\":\"the pinned 13-request sample batch (all requestedAt on 2026-10-20) processed with the production system clock after 2026-10-20\",\"when\":\"process runs\",\"then\":\"no SUSPICIOUS_TIMESTAMP problem is produced and the pinned queue, problems and trace counts 13 \u2192 10 \u2192 6 are unchanged\"}],\"affectedClasses\":[\"ProblemCode\",\"Problem\",\"WorkRequestValidator\",\"ProcessingConfig\"],\"outOfScope\":[\"Any configurable clock-skew tolerance or grace window (e.g. app.validation.future-tolerance); the check is strictly > now\",\"Flagging suspiciously old timestamps or implausible date ranges\",\"Accepting future-dated requests with a warning instead of rejecting them\",\"Checking timestamps in the deduplicate or prioritize stages, or changing the sort order\",\"Changing WorkRequestProcessor, DuplicateResolver, WorkRequestSorter or RequestMapper signatures or behaviour\",\"Rewriting sample-batch.json to include a future-dated request\",\"Per-sending-site clock diagnostics, aggregate clock-drift metrics, or new API endpoints/fields beyond the existing problem representation\",\"Time-zone conversion or normalising requestedAt values\"],\"title\":\"Flag requests with a future requestedAt as SUSPICIOUS_TIMESTAMP\"}",
+  "data": {
+    "criteria": [
+      {
+        "given": "a validator built with Clock.fixed(Instant.parse(\"2026-10-20T12:00:00Z\"), ZoneOffset.UTC) and a batch containing one otherwise-valid request whose requestedAt is 2026-10-20T12:00:01Z",
+        "when": "validate(batch) runs",
+        "then": "the StageResult problems contain exactly one Problem with code ProblemCode.SUSPICIOUS_TIMESTAMP, field \"requestedAt\", the request's requestId, inputIndex 0, null relatedRequestId/escalatedFrom/escalatedTo, and the request is NOT in StageResult.requests()"
+      },
+      {
+        "given": "a validator built with a clock fixed at 2026-10-20T12:00:00Z",
+        "when": "validating requests whose requestedAt is exactly 2026-10-20T12:00:00Z, or earlier (e.g. 2026-10-20T11:59:59.999Z)",
+        "then": "no SUSPICIOUS_TIMESTAMP problem is reported and the requests are accepted; only an instant strictly after the clock instant (including 2026-10-20T12:00:00.000000001Z) is treated as future \u2014 there is no tolerance window"
+      },
+      {
+        "given": "the clock instant is 2026-10-20T12:00:00Z",
+        "when": "a request WR-1 with requestedAt 2026-10-21T08:30:00Z is validated",
+        "then": "the problem message is exactly \"WR-1 has requestedAt 2026-10-21T08:30:00Z in the future\" (requestId, then \" has requestedAt \", the Instant.toString() of requestedAt, then \" in the future\"), and it never mentions the input index"
+      },
+      {
+        "given": "a request whose requestId is null or blank and whose requestedAt is in the future",
+        "when": "it is validated",
+        "then": "a SUSPICIOUS_TIMESTAMP problem is still reported with requestId null and message \"Request has requestedAt <instant> in the future\", in addition to the MISSING_FIELD problem for requestId"
+      },
+      {
+        "given": "a request with requestedAt null, or with requestedAt supplied through invalidFieldsByIndex as an unreadable raw value",
+        "when": "it is validated",
+        "then": "only MISSING_FIELD (respectively INVALID_VALUE) is reported for field \"requestedAt\" and no SUSPICIOUS_TIMESTAMP problem is produced, because the future check needs a parsed instant"
+      },
+      {
+        "given": "a request that is missing equipmentId and also has a future requestedAt",
+        "when": "it is validated",
+        "then": "both problems are reported for that input index, with the MISSING_FIELD problems (produced in field order requestId, equipmentId, priority, requestedAt, description) first and the SUSPICIOUS_TIMESTAMP problem last for that request"
+      },
+      {
+        "given": "a batch where a future-dated request is rejected",
+        "when": "validate completes",
+        "then": "the validate StageTrace has in = batch size, out = number of accepted requests (the future-dated one excluded), and notes contain the sorted, index-free entry \"WR-1 rejected: future requestedAt\" (label \"Request without id\" when the id is blank), reusing the existing \"<label> rejected: <reasons>\" format with reasons joined by \", \""
+      },
+      {
+        "given": "a batch containing two requests with identical content, one dated in the past and one dated in the future, processed by WorkRequestProcessor with a fixed clock",
+        "when": "process(batch) runs",
+        "then": "the future-dated request is rejected in the validate stage with SUSPICIOUS_TIMESTAMP, it never reaches the deduplicate stage (no DUPLICATE problem and no priority escalation is reported for it), and the trace still has exactly three entries validate, deduplicate, prioritize"
+      },
+      {
+        "given": "a batch containing several future-dated requests at different positions, shuffled repeatedly",
+        "when": "process(batch) runs with the same fixed clock",
+        "then": "the accepted queue, the problem list and the trace notes are identical for every ordering; the SUSPICIOUS_TIMESTAMP problems appear in the validate-stage block ordered by inputIndex"
+      },
+      {
+        "given": "the clock is read for the batch",
+        "when": "validate(List) or validate(List, Map) is called",
+        "then": "clock.instant() is consulted once per call so every request in one batch is compared against the same instant"
+      },
+      {
+        "given": "existing code and tests that call new WorkRequestValidator()",
+        "when": "the class is compiled and used",
+        "then": "the no-arg constructor still exists and behaves as if constructed with Clock.systemUTC(); a new constructor WorkRequestValidator(Clock clock) is added and throws NullPointerException with message \"clock\" when given null"
+      },
+      {
+        "given": "the Spring application context",
+        "when": "it starts with no overriding bean",
+        "then": "ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC(), and the workRequestValidator bean is built with that Clock; no new configuration property is introduced"
+      },
+      {
+        "given": "the ProblemCode enum",
+        "when": "ProblemCode.values() is read",
+        "then": "it contains SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID), so existing constants keep their order, BatchObserver gets one counter per code including the new one, and ProblemDto serialises the code as the string \"SUSPICIOUS_TIMESTAMP\" with field \"requestedAt\""
+      },
+      {
+        "given": "the pinned 13-request sample batch (all requestedAt on 2026-10-20) processed with the production system clock after 2026-10-20",
+        "when": "process runs",
+        "then": "no SUSPICIOUS_TIMESTAMP problem is produced and the pinned queue, problems and trace counts 13 \u2192 10 \u2192 6 are unchanged"
+      }
+    ],
+    "affectedClasses": [
+      "ProblemCode",
+      "Problem",
+      "WorkRequestValidator",
+      "ProcessingConfig"
+    ],
+    "outOfScope": [
+      "Any configurable clock-skew tolerance or grace window (e.g. app.validation.future-tolerance); the check is strictly > now",
+      "Flagging suspiciously old timestamps or implausible date ranges",
+      "Accepting future-dated requests with a warning instead of rejecting them",
+      "Checking timestamps in the deduplicate or prioritize stages, or changing the sort order",
+      "Changing WorkRequestProcessor, DuplicateResolver, WorkRequestSorter or RequestMapper signatures or behaviour",
+      "Rewriting sample-batch.json to include a future-dated request",
+      "Per-sending-site clock diagnostics, aggregate clock-drift metrics, or new API endpoints/fields beyond the existing problem representation",
+      "Time-zone conversion or normalising requestedAt values"
+    ],
+    "title": "Flag requests with a future requestedAt as SUSPICIOUS_TIMESTAMP"
+  },
+  "raw": {
+    "is_error": false,
+    "duration_api_ms": 62476,
+    "num_turns": 2,
+    "stop_reason": "tool_use",
+    "session_id": "3a5b1136-baf5-4430-9481-aac8478bbc85",
+    "total_cost_usd": 0.26431400000000005,
+    "usage": {
+      "input_tokens": 2,
+      "cache_creation_input_tokens": 13269,
+      "cache_read_input_tokens": 0,
+      "output_tokens": 4962,
+      "output_tokens_details": {
+        "thinking_tokens": 2543
+      },
+      "server_tool_use": {
+        "web_search_requests": 0,
+        "web_fetch_requests": 0
+      },
+      "service_tier": "standard",
+      "cache_creation": {
+        "ephemeral_1h_input_tokens": 13269,
+        "ephemeral_5m_input_tokens": 0
+      },
+      "inference_geo": "global",
+      "iterations": [
+        {
+          "input_tokens": 2,
+          "output_tokens": 4962,
+          "cache_read_input_tokens": 0,
+          "cache_creation_input_tokens": 13269,
+          "cache_creation": {
+            "ephemeral_5m_input_tokens": 0,
+            "ephemeral_1h_input_tokens": 13269
+          },
+          "type": "message"
+        }
+      ],
+      "speed": "standard"
+    },
+    "modelUsage": {
+      "claude-haiku-4-5-20251001": {
+        "inputTokens": 7504,
+        "outputTokens": 12,
+        "cacheReadInputTokens": 0,
+        "cacheCreationInputTokens": 0,
+        "webSearchRequests": 0,
+        "costUSD": 0.007564,
+        "contextWindow": 200000,
+        "maxOutputTokens": 32000,
+        "canonicalModel": "claude-haiku-4-5",
+        "provider": "firstParty"
+      },
+      "claude-opus-5": {
+        "inputTokens": 2,
+        "outputTokens": 4962,
+        "cacheReadInputTokens": 0,
+        "cacheCreationInputTokens": 13269,
+        "webSearchRequests": 0,
+        "costUSD": 0.25675000000000003,
+        "contextWindow": 1000000,
+        "maxOutputTokens": 64000,
+        "canonicalModel": "claude-opus-5",
+        "provider": "firstParty"
+      }
+    },
+    "permission_denials": [],
+    "terminal_reason": "completed",
+    "fast_mode_state": "off",
+    "subtype": "success",
+    "api_error_status": null,
+    "result": "{\"criteria\":[{\"given\":\"a validator built with Clock.fixed(Instant.parse(\\\"2026-10-20T12:00:00Z\\\"), ZoneOffset.UTC) and a batch containing one otherwise-valid request whose requestedAt is 2026-10-20T12:00:01Z\",\"when\":\"validate(batch) runs\",\"then\":\"the StageResult problems contain exactly one Problem with code ProblemCode.SUSPICIOUS_TIMESTAMP, field \\\"requestedAt\\\", the request's requestId, inputIndex 0, null relatedRequestId/escalatedFrom/escalatedTo, and the request is NOT in StageResult.requests()\"},{\"given\":\"a validator built with a clock fixed at 2026-10-20T12:00:00Z\",\"when\":\"validating requests whose requestedAt is exactly 2026-10-20T12:00:00Z, or earlier (e.g. 2026-10-20T11:59:59.999Z)\",\"then\":\"no SUSPICIOUS_TIMESTAMP problem is reported and the requests are accepted; only an instant strictly after the clock instant (including 2026-10-20T12:00:00.000000001Z) is treated as future \u2014 there is no tolerance window\"},{\"given\":\"the clock instant is 2026-10-20T12:00:00Z\",\"when\":\"a request WR-1 with requestedAt 2026-10-21T08:30:00Z is validated\",\"then\":\"the problem message is exactly \\\"WR-1 has requestedAt 2026-10-21T08:30:00Z in the future\\\" (requestId, then \\\" has requestedAt \\\", the Instant.toString() of requestedAt, then \\\" in the future\\\"), and it never mentions the input index\"},{\"given\":\"a request whose requestId is null or blank and whose requestedAt is in the future\",\"when\":\"it is validated\",\"then\":\"a SUSPICIOUS_TIMESTAMP problem is still reported with requestId null and message \\\"Request has requestedAt <instant> in the future\\\", in addition to the MISSING_FIELD problem for requestId\"},{\"given\":\"a request with requestedAt null, or with requestedAt supplied through invalidFieldsByIndex as an unreadable raw value\",\"when\":\"it is validated\",\"then\":\"only MISSING_FIELD (respectively INVALID_VALUE) is reported for field \\\"requestedAt\\\" and no SUSPICIOUS_TIMESTAMP problem is produced, because the future check needs a parsed instant\"},{\"given\":\"a request that is missing equipmentId and also has a future requestedAt\",\"when\":\"it is validated\",\"then\":\"both problems are reported for that input index, with the MISSING_FIELD problems (produced in field order requestId, equipmentId, priority, requestedAt, description) first and the SUSPICIOUS_TIMESTAMP problem last for that request\"},{\"given\":\"a batch where a future-dated request is rejected\",\"when\":\"validate completes\",\"then\":\"the validate StageTrace has in = batch size, out = number of accepted requests (the future-dated one excluded), and notes contain the sorted, index-free entry \\\"WR-1 rejected: future requestedAt\\\" (label \\\"Request without id\\\" when the id is blank), reusing the existing \\\"<label> rejected: <reasons>\\\" format with reasons joined by \\\", \\\"\"},{\"given\":\"a batch containing two requests with identical content, one dated in the past and one dated in the future, processed by WorkRequestProcessor with a fixed clock\",\"when\":\"process(batch) runs\",\"then\":\"the future-dated request is rejected in the validate stage with SUSPICIOUS_TIMESTAMP, it never reaches the deduplicate stage (no DUPLICATE problem and no priority escalation is reported for it), and the trace still has exactly three entries validate, deduplicate, prioritize\"},{\"given\":\"a batch containing several future-dated requests at different positions, shuffled repeatedly\",\"when\":\"process(batch) runs with the same fixed clock\",\"then\":\"the accepted queue, the problem list and the trace notes are identical for every ordering; the SUSPICIOUS_TIMESTAMP problems appear in the validate-stage block ordered by inputIndex\"},{\"given\":\"the clock is read for the batch\",\"when\":\"validate(List) or validate(List, Map) is called\",\"then\":\"clock.instant() is consulted once per call so every request in one batch is compared against the same instant\"},{\"given\":\"existing code and tests that call new WorkRequestValidator()\",\"when\":\"the class is compiled and used\",\"then\":\"the no-arg constructor still exists and behaves as if constructed with Clock.systemUTC(); a new constructor WorkRequestValidator(Clock clock) is added and throws NullPointerException with message \\\"clock\\\" when given null\"},{\"given\":\"the Spring application context\",\"when\":\"it starts with no overriding bean\",\"then\":\"ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC(), and the workRequestValidator bean is built with that Clock; no new configuration property is introduced\"},{\"given\":\"the ProblemCode enum\",\"when\":\"ProblemCode.values() is read\",\"then\":\"it contains SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID), so existing constants keep their order, BatchObserver gets one counter per code including the new one, and ProblemDto serialises the code as the string \\\"SUSPICIOUS_TIMESTAMP\\\" with field \\\"requestedAt\\\"\"},{\"given\":\"the pinned 13-request sample batch (all requestedAt on 2026-10-20) processed with the production system clock after 2026-10-20\",\"when\":\"process runs\",\"then\":\"no SUSPICIOUS_TIMESTAMP problem is produced and the pinned queue, problems and trace counts 13 \u2192 10 \u2192 6 are unchanged\"}],\"affectedClasses\":[\"ProblemCode\",\"Problem\",\"WorkRequestValidator\",\"ProcessingConfig\"],\"outOfScope\":[\"Any configurable clock-skew tolerance or grace window (e.g. app.validation.future-tolerance); the check is strictly > now\",\"Flagging suspiciously old timestamps or implausible date ranges\",\"Accepting future-dated requests with a warning instead of rejecting them\",\"Checking timestamps in the deduplicate or prioritize stages, or changing the sort order\",\"Changing WorkRequestProcessor, DuplicateResolver, WorkRequestSorter or RequestMapper signatures or behaviour\",\"Rewriting sample-batch.json to include a future-dated request\",\"Per-sending-site clock diagnostics, aggregate clock-drift metrics, or new API endpoints/fields beyond the existing problem representation\",\"Time-zone conversion or normalising requestedAt values\"],\"title\":\"Flag requests with a future requestedAt as SUSPICIOUS_TIMESTAMP\"}",
+    "structured_output": {
+      "criteria": [
+        {
+          "given": "a validator built with Clock.fixed(Instant.parse(\"2026-10-20T12:00:00Z\"), ZoneOffset.UTC) and a batch containing one otherwise-valid request whose requestedAt is 2026-10-20T12:00:01Z",
+          "when": "validate(batch) runs",
+          "then": "the StageResult problems contain exactly one Problem with code ProblemCode.SUSPICIOUS_TIMESTAMP, field \"requestedAt\", the request's requestId, inputIndex 0, null relatedRequestId/escalatedFrom/escalatedTo, and the request is NOT in StageResult.requests()"
+        },
+        {
+          "given": "a validator built with a clock fixed at 2026-10-20T12:00:00Z",
+          "when": "validating requests whose requestedAt is exactly 2026-10-20T12:00:00Z, or earlier (e.g. 2026-10-20T11:59:59.999Z)",
+          "then": "no SUSPICIOUS_TIMESTAMP problem is reported and the requests are accepted; only an instant strictly after the clock instant (including 2026-10-20T12:00:00.000000001Z) is treated as future \u2014 there is no tolerance window"
+        },
+        {
+          "given": "the clock instant is 2026-10-20T12:00:00Z",
+          "when": "a request WR-1 with requestedAt 2026-10-21T08:30:00Z is validated",
+          "then": "the problem message is exactly \"WR-1 has requestedAt 2026-10-21T08:30:00Z in the future\" (requestId, then \" has requestedAt \", the Instant.toString() of requestedAt, then \" in the future\"), and it never mentions the input index"
+        },
+        {
+          "given": "a request whose requestId is null or blank and whose requestedAt is in the future",
+          "when": "it is validated",
+          "then": "a SUSPICIOUS_TIMESTAMP problem is still reported with requestId null and message \"Request has requestedAt <instant> in the future\", in addition to the MISSING_FIELD problem for requestId"
+        },
+        {
+          "given": "a request with requestedAt null, or with requestedAt supplied through invalidFieldsByIndex as an unreadable raw value",
+          "when": "it is validated",
+          "then": "only MISSING_FIELD (respectively INVALID_VALUE) is reported for field \"requestedAt\" and no SUSPICIOUS_TIMESTAMP problem is produced, because the future check needs a parsed instant"
+        },
+        {
+          "given": "a request that is missing equipmentId and also has a future requestedAt",
+          "when": "it is validated",
+          "then": "both problems are reported for that input index, with the MISSING_FIELD problems (produced in field order requestId, equipmentId, priority, requestedAt, description) first and the SUSPICIOUS_TIMESTAMP problem last for that request"
+        },
+        {
+          "given": "a batch where a future-dated request is rejected",
+          "when": "validate completes",
+          "then": "the validate StageTrace has in = batch size, out = number of accepted requests (the future-dated one excluded), and notes contain the sorted, index-free entry \"WR-1 rejected: future requestedAt\" (label \"Request without id\" when the id is blank), reusing the existing \"<label> rejected: <reasons>\" format with reasons joined by \", \""
+        },
+        {
+          "given": "a batch containing two requests with identical content, one dated in the past and one dated in the future, processed by WorkRequestProcessor with a fixed clock",
+          "when": "process(batch) runs",
+          "then": "the future-dated request is rejected in the validate stage with SUSPICIOUS_TIMESTAMP, it never reaches the deduplicate stage (no DUPLICATE problem and no priority escalation is reported for it), and the trace still has exactly three entries validate, deduplicate, prioritize"
+        },
+        {
+          "given": "a batch containing several future-dated requests at different positions, shuffled repeatedly",
+          "when": "process(batch) runs with the same fixed clock",
+          "then": "the accepted queue, the problem list and the trace notes are identical for every ordering; the SUSPICIOUS_TIMESTAMP problems appear in the validate-stage block ordered by inputIndex"
+        },
+        {
+          "given": "the clock is read for the batch",
+          "when": "validate(List) or validate(List, Map) is called",
+          "then": "clock.instant() is consulted once per call so every request in one batch is compared against the same instant"
+        },
+        {
+          "given": "existing code and tests that call new WorkRequestValidator()",
+          "when": "the class is compiled and used",
+          "then": "the no-arg constructor still exists and behaves as if constructed with Clock.systemUTC(); a new constructor WorkRequestValidator(Clock clock) is added and throws NullPointerException with message \"clock\" when given null"
+        },
+        {
+          "given": "the Spring application context",
+          "when": "it starts with no overriding bean",
+          "then": "ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC(), and the workRequestValidator bean is built with that Clock; no new configuration property is introduced"
+        },
+        {
+          "given": "the ProblemCode enum",
+          "when": "ProblemCode.values() is read",
+          "then": "it contains SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID), so existing constants keep their order, BatchObserver gets one counter per code including the new one, and ProblemDto serialises the code as the string \"SUSPICIOUS_TIMESTAMP\" with field \"requestedAt\""
+        },
+        {
+          "given": "the pinned 13-request sample batch (all requestedAt on 2026-10-20) processed with the production system clock after 2026-10-20",
+          "when": "process runs",
+          "then": "no SUSPICIOUS_TIMESTAMP problem is produced and the pinned queue, problems and trace counts 13 \u2192 10 \u2192 6 are unchanged"
+        }
+      ],
+      "affectedClasses": [
+        "ProblemCode",
+        "Problem",
+        "WorkRequestValidator",
+        "ProcessingConfig"
+      ],
+      "outOfScope": [
+        "Any configurable clock-skew tolerance or grace window (e.g. app.validation.future-tolerance); the check is strictly > now",
+        "Flagging suspiciously old timestamps or implausible date ranges",
+        "Accepting future-dated requests with a warning instead of rejecting them",
+        "Checking timestamps in the deduplicate or prioritize stages, or changing the sort order",
+        "Changing WorkRequestProcessor, DuplicateResolver, WorkRequestSorter or RequestMapper signatures or behaviour",
+        "Rewriting sample-batch.json to include a future-dated request",
+        "Per-sending-site clock diagnostics, aggregate clock-drift metrics, or new API endpoints/fields beyond the existing problem representation",
+        "Time-zone conversion or normalising requestedAt values"
+      ],
+      "title": "Flag requests with a future requestedAt as SUSPICIOUS_TIMESTAMP"
+    },
+    "ttft_ms": 35892,
+    "ttft_stream_ms": 1389,
+    "time_to_request_ms": 14,
+    "type": "result",
+    "duration_ms": 61604,
+    "uuid": "3c19438b-277a-46c7-84e1-e6bea529a2ca"
+  }
+}
diff --git a/agent-pipeline/runs/2026-10-29-suspicious-timestamp/02-enrich-retry.prompt.md b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/02-enrich-retry.prompt.md
new file mode 100644
index 0000000..def4f98
--- /dev/null
+++ b/agent-pipeline/runs/2026-10-29-suspicious-timestamp/02-enrich-retry.prompt.md
@@ -0,0 +1,573 @@
+# enrich-retry: prompt
+
+## System
+
+You turn a plain-English user story for a Java service into acceptance criteria that two
+people could implement and test independently and still agree.
+
+Return only JSON matching the given schema. Each criterion is a concrete, testable
+given/when/then. Name the exact identifiers the change introduces (enum constants, method or
+factory names, configuration property keys and their defaults, problem messages where they
+matter), because the implementation and the tests are written separately from these criteria
+alone. State every behavioural decision the story leaves open. List in affectedClasses the
+simple names of the classes that must change or be created, including wiring/config classes.
+Keep the scope to what the story asks; put tempting extras in outOfScope.
+
+
+## User
+
+# Story
+
+# Report future timestamps
+
+As an integrator sending batches from several sites, I want a SUSPICIOUS_TIMESTAMP problem
+for any request whose requestedAt is in the future, so that clock or time-zone bugs in a
+sending system are caught instead of distorting the queue order.
+
+"In the future" is relative to the time the batch is processed, and the check must be
+testable without depending on the real clock.
+
+# Codebase notes
+
+# Work Request Service: what you need to know
+
+Java 21, Spring Boot 3.5, Maven. Source in `backend/src/main/java/com/tanya/workrequests/`,
+tests in `backend/src/test/java/com/tanya/workrequests/`. Packages: `domain`, `processing`,
+`api` (+ `api.dto`), `config`.
+
+## Domain (records and enums, immutable)
+
+- `WorkRequest(requestId, equipmentId, Priority priority, Instant requestedAt, String description)`,
+  with `withPriority(Priority)`.
+- `Priority`: LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4); compare with `weight()`, never `ordinal()`.
+- `ProblemCode`: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID.
+- `Problem(inputIndex, requestId, code, field, relatedRequestId, escalatedFrom, escalatedTo, message)`,
+  built through static factories (`Problem.missingField(...)`, `Problem.duplicate(...)`, ...).
+  `escalatedFrom`/`escalatedTo` are set together or not at all. Messages name request ids but
+  never the input index.
+- `StageTrace(stage, in, out, notes)`; `ProcessingResult(accepted, problems, trace)`.
+
+## Processing pipeline
+
+`WorkRequestProcessor(validator, duplicateResolver, sorter)` runs three stages in order and
+returns a `ProcessingResult` whose trace always has exactly three entries: `validate`,
+`deduplicate`, `prioritize`.
+
+- `WorkRequestValidator.validate(List<WorkRequest>)` and `validate(List, Map<Integer, List<InvalidField>>)`
+  return a `StageResult(List<IndexedRequest> requests, List<Problem> problems, StageTrace trace)`.
+- `DuplicateResolver(DescriptionNormalizer).resolve(List<IndexedRequest>)`: request-id conflicts,
+  then duplicates (earliest kept, priority escalated to the group's highest).
+- `WorkRequestSorter.sort(List<IndexedRequest>)`: priority weight desc, requestedAt asc, requestId asc.
+- `IndexedRequest(inputIndex, request)` carries the original position through the stages.
+
+Rules the tests pin down:
+
+- Problems are data, never exceptions; one bad request never fails a batch.
+- Output must be identical regardless of input order (tests shuffle batches 100 times). Trace
+  notes are sorted text and contain no input indices. Problems are listed in stage order, then
+  by input index within a stage.
+- Validation runs before deduplication.
+
+## Wiring and configuration
+
+- `config/ProcessingConfig` (`@Configuration(proxyBeanMethods = false)`) creates every stage and
+  the processor as `@Bean`s. Main code has no `@Component` annotations.
+- Settings are `@ConfigurationProperties` records with `@DefaultValue`, e.g.
+  `BatchProperties(@DefaultValue("5000") int maxSize)` bound from `app.batch.*` in
+  `src/main/resources/application.yml`; the application class has `@ConfigurationPropertiesScan`.
+- There is no `java.time.Clock` bean yet. Code that needs "now" should take a `Clock` rather than
+  calling `Instant.now()` directly, so tests can use `Clock.fixed(...)`.
+
+## Compatibility hazards
+
+- Many existing tests call `new WorkRequestValidator()`, `new DuplicateResolver(new DescriptionNormalizer())`,
+  `new WorkRequestSorter()` and `new WorkRequestProcessor(validator, resolver, so
... (diff truncated)
```


## Output schema

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": [
    "markdown"
  ],
  "properties": {
    "markdown": {
      "type": "string"
    }
  }
}
```
