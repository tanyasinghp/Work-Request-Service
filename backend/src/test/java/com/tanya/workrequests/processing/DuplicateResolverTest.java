package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.WorkRequest;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.tanya.workrequests.domain.Priority.CRITICAL;
import static com.tanya.workrequests.domain.Priority.HIGH;
import static com.tanya.workrequests.domain.Priority.LOW;
import static com.tanya.workrequests.domain.Priority.MEDIUM;
import static com.tanya.workrequests.processing.TestRequests.indexed;
import static com.tanya.workrequests.processing.TestRequests.request;
import static com.tanya.workrequests.processing.TestRequests.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DuplicateResolverTest {

    private static final String T0800 = "2026-10-20T08:00:00Z";
    private static final String T0900 = "2026-10-20T09:00:00Z";
    private static final String T1000 = "2026-10-20T10:00:00Z";

    private final DuplicateResolver resolver = new DuplicateResolver(new DescriptionNormalizer());

    @Test
    void keepsDistinctRequests() {
        List<IndexedRequest> input = indexed(valid("REQ-1"), valid("REQ-2"), valid("REQ-3"));

        StageResult result = resolver.resolve(input);

        assertThat(result.requests()).isEqualTo(input);
        assertThat(result.problems()).isEmpty();
        assertThat(result.trace().notes()).isEmpty();
    }

    @Test
    void treatsCaseAndWhitespaceVariantsAsDuplicates() {
        WorkRequest original = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest variant = request("REQ-2", " pump-7 ", MEDIUM, T0900, "  pump   LEAKING ");

        StageResult result = resolver.resolve(indexed(original, variant));

        assertThat(result.requests()).extracting(IndexedRequest::request).containsExactly(original);
        assertThat(result.problems()).containsExactly(Problem.duplicate(1, "REQ-2", "REQ-1"));
    }

    @Test
    void doesNotCollapseInternalWhitespaceInEquipmentId() {
        WorkRequest first = request("REQ-1", "PUMP 7", MEDIUM, T0800, "Pump leaking");
        WorkRequest second = request("REQ-2", "PUMP  7", MEDIUM, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(first, second));

        assertThat(result.requests()).hasSize(2);
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void keepsSameDescriptionOnDifferentEquipment() {
        WorkRequest pump7 = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest pump8 = request("REQ-2", "PUMP-8", MEDIUM, T0800, "Pump leaking");

        StageResult result = resolver.resolve(indexed(pump7, pump8));

        assertThat(result.requests()).extracting(IndexedRequest::request).containsExactly(pump7, pump8);
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void keepsEarliestDuplicateAndEscalatesPriority() {
        WorkRequest original = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest critical = request("REQ-2", "PUMP-7", CRITICAL, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(original, critical));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, original.withPriority(CRITICAL)));
        assertThat(result.problems()).containsExactly(
                Problem.duplicateWithEscalation(1, "REQ-2", "REQ-1", MEDIUM, CRITICAL));
        assertThat(result.problems().get(0).message())
                .isEqualTo("Duplicate of REQ-1; priority escalated MEDIUM to CRITICAL");
    }

    @Test
    void doesNotLowerPriorityOfKeptRequest() {
        WorkRequest original = request("REQ-1", "PUMP-7", HIGH, T0800, "Pump leaking");
        WorkRequest lower = request("REQ-2", "PUMP-7", LOW, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(original, lower));

        assertThat(result.requests()).extracting(IndexedRequest::request).containsExactly(original);
        assertThat(result.problems()).containsExactly(Problem.duplicate(1, "REQ-2", "REQ-1"));
        assertThat(result.trace().notes()).containsExactly("REQ-1 kept; merged duplicates REQ-2");
    }

    @Test
    void breaksRequestedAtTieByLowestRequestId() {
        WorkRequest later = request("REQ-2", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest lower = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");

        StageResult result = resolver.resolve(indexed(later, lower));

        assertThat(result.requests()).containsExactly(new IndexedRequest(1, lower));
        assertThat(result.problems()).containsExactly(Problem.duplicate(0, "REQ-2", "REQ-1"));
    }

    @Test
    void keepsEarliestOfThreeWayDuplicateAndReportsTwo() {
        WorkRequest second = request("REQ-2", "PUMP-7", MEDIUM, T0900, "Pump leaking");
        WorkRequest earliest = request("REQ-3", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest third = request("REQ-1", "PUMP-7", MEDIUM, T1000, "Pump leaking");

        StageResult result = resolver.resolve(indexed(second, earliest, third));

        assertThat(result.requests()).containsExactly(new IndexedRequest(1, earliest));
        assertThat(result.problems()).containsExactly(
                Problem.duplicate(0, "REQ-2", "REQ-3"),
                Problem.duplicate(2, "REQ-1", "REQ-3"));
    }

    @Test
    void pointsRelatedRequestIdAtKeptRequest() {
        WorkRequest kept = request("REQ-9", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest dropped = request("REQ-1", "PUMP-7", MEDIUM, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(dropped, kept));

        assertThat(result.problems())
                .extracting(Problem::requestId, Problem::code, Problem::relatedRequestId)
                .containsExactly(tuple("REQ-1", ProblemCode.DUPLICATE, "REQ-9"));
    }

    @Test
    void treatsExactRepeatAsDuplicate() {
        WorkRequest request = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");

        StageResult result = resolver.resolve(indexed(request, request));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, request));
        assertThat(result.problems())
                .extracting(Problem::inputIndex, Problem::requestId, Problem::code,
                        Problem::relatedRequestId, Problem::message)
                .containsExactly(tuple(1, "REQ-1", ProblemCode.DUPLICATE, "REQ-1", "Exact repeat of REQ-1"));
    }

    @Test
    void exactRepeatInEscalatedGroupKeepsItsOwnMessage() {
        WorkRequest original = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest critical = request("REQ-2", "PUMP-7", CRITICAL, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(original, critical, original));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, original.withPriority(CRITICAL)));
        assertThat(result.problems()).containsExactly(
                Problem.duplicateWithEscalation(1, "REQ-2", "REQ-1", MEDIUM, CRITICAL),
                Problem.exactRepeat(2, "REQ-1"));
    }

    @Test
    void reportsConflictingRequestId() {
        WorkRequest chiller = request("REQ-9", "CHILLER-1", MEDIUM, T0800, "Compressor noise");
        WorkRequest roof = request("REQ-9", "ROOF-2", LOW, T1000, "Gutter blocked");

        StageResult result = resolver.resolve(indexed(valid("REQ-1"), chiller, roof));

        assertThat(result.requests()).extracting(IndexedRequest::inputIndex).containsExactly(0);
        assertThat(result.problems()).containsExactly(
                Problem.conflictingRequestId(1, "REQ-9"),
                Problem.conflictingRequestId(2, "REQ-9"));
        assertThat(result.problems().get(0).message())
                .isEqualTo("Request id REQ-9 is used by requests with different content");
        assertThat(result.trace().notes())
                .containsExactly("REQ-9 rejected: 2 requests share this id with different content");
    }

    @Test
    void reportsConflictWhenOnlyOneFieldDiffers() {
        WorkRequest medium = request("REQ-9", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest high = request("REQ-9", "PUMP-7", HIGH, T0800, "Pump leaking");

        StageResult result = resolver.resolve(indexed(medium, high));

        assertThat(result.requests()).isEmpty();
        assertThat(result.problems()).extracting(Problem::code)
                .containsExactly(ProblemCode.CONFLICTING_REQUEST_ID, ProblemCode.CONFLICTING_REQUEST_ID);
    }

    @Test
    void conflictingGroupWithTwoIdenticalAndOneDifferentRejectsAll() {
        WorkRequest repeated = request("REQ-9", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest different = request("REQ-9", "PUMP-7", MEDIUM, T0800, "Pump leaking badly");

        StageResult result = resolver.resolve(indexed(repeated, repeated, different));

        assertThat(result.requests()).isEmpty();
        assertThat(result.problems()).containsExactly(
                Problem.conflictingRequestId(0, "REQ-9"),
                Problem.conflictingRequestId(1, "REQ-9"),
                Problem.conflictingRequestId(2, "REQ-9"));
    }

    @Test
    void excludesConflictingRequestsFromDeduplication() {
        // REQ-9 is earlier and Critical, so it would knock out and escalate REQ-1 if it took part.
        WorkRequest original = request("REQ-1", "PUMP-7", MEDIUM, T0900, "Pump leaking");
        WorkRequest conflictA = request("REQ-9", "PUMP-7", CRITICAL, T0800, "Pump leaking");
        WorkRequest conflictB = request("REQ-9", "ROOF-2", LOW, T1000, "Gutter blocked");

        StageResult result = resolver.resolve(indexed(original, conflictA, conflictB));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, original));
        assertThat(result.problems()).extracting(Problem::code)
                .containsOnly(ProblemCode.CONFLICTING_REQUEST_ID);
    }

    @Test
    void escalationMessageGoesToLowestIdWhenDroppedRequestsTiePriority() {
        WorkRequest original = request("REQ-1", "PUMP-7", LOW, T0800, "Pump leaking");
        WorkRequest criticalHigherId = request("REQ-5", "PUMP-7", CRITICAL, T0900, "Pump leaking");
        WorkRequest criticalLowerId = request("REQ-3", "PUMP-7", CRITICAL, T1000, "Pump leaking");
        WorkRequest high = request("REQ-2", "PUMP-7", HIGH, T0900, "Pump leaking");

        StageResult result = resolver.resolve(indexed(original, criticalHigherId, criticalLowerId, high));

        assertThat(result.requests()).containsExactly(new IndexedRequest(0, original.withPriority(CRITICAL)));
        assertThat(result.problems()).containsExactly(
                Problem.duplicate(1, "REQ-5", "REQ-1"),
                Problem.duplicateWithEscalation(2, "REQ-3", "REQ-1", LOW, CRITICAL),
                Problem.duplicate(3, "REQ-2", "REQ-1"));
    }

    @Test
    void notesEscalationInTrace() {
        WorkRequest original = request("REQ-101", "PUMP-7", MEDIUM, T0800, "Pump leaking");
        WorkRequest variant = request("REQ-107", " pump-7 ", MEDIUM, T0900, "pump  LEAKING");
        WorkRequest critical = request("REQ-108", "PUMP-7", CRITICAL, T1000, "Pump leaking");
        WorkRequest other = valid("REQ-200");

        StageResult result = resolver.resolve(indexed(critical, other, variant, original));

        assertThat(result.trace().stage()).isEqualTo("deduplicate");
        assertThat(result.trace().in()).isEqualTo(4);
        assertThat(result.trace().out()).isEqualTo(2);
        assertThat(result.trace().notes()).containsExactly(
                "REQ-101 kept; merged duplicates REQ-107, REQ-108",
                "REQ-101 escalated MEDIUM to CRITICAL (from REQ-108)");
    }

    @Test
    void keepsSurvivorsInOriginalInputOrder() {
        // The duplicate group is first seen at index 0, but its survivor sits at index 3.
        WorkRequest laterDuplicate = request("REQ-4", "PUMP-7", MEDIUM, T1000, "Pump leaking");
        WorkRequest b = valid("REQ-2");
        WorkRequest c = valid("REQ-3");
        WorkRequest survivor = request("REQ-1", "PUMP-7", MEDIUM, T0800, "Pump leaking");

        StageResult result = resolver.resolve(indexed(laterDuplicate, b, c, survivor));

        assertThat(result.requests()).extracting(IndexedRequest::inputIndex).containsExactly(1, 2, 3);
    }

    @Test
    void resultIsIdenticalWhenInputOrderIsReversed() {
        List<WorkRequest> batch = List.of(
                request("REQ-101", "PUMP-7", MEDIUM, T0800, "Pump leaking"),
                request("REQ-107", " pump-7 ", MEDIUM, T0900, "  pump   LEAKING "),
                request("REQ-108", "PUMP-7", CRITICAL, T1000, "Pump leaking"),
                request("REQ-109", "CHILLER-1", MEDIUM, T0800, "Compressor noise"),
                request("REQ-109", "ROOF-2", LOW, T1000, "Gutter blocked"),
                request("REQ-110", "DOOR-5", HIGH, T0800, "Lock jammed"),
                request("REQ-111", "DOOR-5", HIGH, T0800, "lock jammed"),
                request("REQ-112", "DOOR-5", HIGH, T0900, "Lock jammed"));
        List<WorkRequest> reversed = new ArrayList<>(batch);
        Collections.reverse(reversed);

        StageResult forward = resolver.resolve(indexed(batch.toArray(WorkRequest[]::new)));
        StageResult backward = resolver.resolve(indexed(reversed.toArray(WorkRequest[]::new)));

        // Kept order follows input order by design (sorting is the next stage), and inputIndex
        // moves with the input, so compare kept requests as a set and problems without index.
        assertThat(requestsOf(backward)).containsExactlyInAnyOrderElementsOf(requestsOf(forward));
        assertThat(problemsWithoutIndex(backward)).containsExactlyInAnyOrderElementsOf(problemsWithoutIndex(forward));
        assertThat(backward.trace()).isEqualTo(forward.trace());
        assertThat(requestsOf(forward)).extracting(WorkRequest::requestId).containsExactlyInAnyOrder("REQ-101", "REQ-110");
    }

    private static List<WorkRequest> requestsOf(StageResult result) {
        return result.requests().stream().map(IndexedRequest::request).toList();
    }

    private static List<Tuple> problemsWithoutIndex(StageResult result) {
        return result.problems().stream()
                .map(problem -> tuple(problem.requestId(), problem.code(), problem.field(),
                        problem.relatedRequestId(), problem.message()))
                .toList();
    }
}
