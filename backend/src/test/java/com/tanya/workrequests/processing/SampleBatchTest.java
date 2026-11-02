package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.tanya.workrequests.domain.Priority.CRITICAL;
import static com.tanya.workrequests.domain.Priority.HIGH;
import static com.tanya.workrequests.domain.Priority.LOW;
import static com.tanya.workrequests.domain.Priority.MEDIUM;
import static com.tanya.workrequests.processing.TestRequests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pins the demo batch the UI steps through. If these numbers change, the UI mock in
 * docs/SPEC.md must change with them.
 */
class SampleBatchTest {

    private final List<WorkRequest> batch = SampleBatch.load();
    private final ProcessingResult result = new WorkRequestProcessor(
            new WorkRequestValidator(),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter()).process(batch);

    @Test
    void loadsThirteenRequestsFromJson() {
        assertThat(batch).hasSize(13);
        assertThat(batch.get(0)).isEqualTo(
                request("REQ-101", "AHU-3", MEDIUM, "2026-10-20T08:15:00Z", "Water leak above Ward 4B"));
        assertThat(batch.get(2).equipmentId()).isNull();
        assertThat(batch.get(4).requestedAt()).isNull();
        assertThat(batch.get(5).description()).isEqualTo("   ");
        assertThat(batch.get(6).equipmentId()).isEqualTo(" ahu-3 ");
    }

    @Test
    void processesSampleBatchIntoExpectedQueue() {
        assertThat(result.accepted()).containsExactly(
                request("REQ-101", "AHU-3", CRITICAL, "2026-10-20T08:15:00Z", "Water leak above Ward 4B"),
                request("REQ-102", "BOILER-1", HIGH, "2026-10-20T07:30:00Z", "Boiler pressure low in east wing"),
                request("REQ-104", "ELEV-2", HIGH, "2026-10-20T07:30:00Z", "Elevator doors not closing on floor 3"),
                request("REQ-110", "DOOR-5", MEDIUM, "2026-10-20T08:00:00Z", "Card reader failing at main entrance"),
                request("REQ-111", "FUME-HOOD-4", MEDIUM, "2026-10-20T13:00:00Z", "Fume hood airflow alarm in chemistry lab"),
                request("REQ-112", "BUS-14", LOW, "2026-10-20T14:00:00Z", "Torn seat on route 14 bus"));
    }

    @Test
    void showsAllFourPriorityColoursInTheQueue() {
        assertThat(result.accepted()).extracting(WorkRequest::priority)
                .containsOnly(CRITICAL, HIGH, MEDIUM, LOW);
    }

    @Test
    void reportsExpectedSampleProblems() {
        assertThat(result.problems()).containsExactly(
                Problem.missingField(2, "REQ-103", "equipmentId"),
                Problem.missingField(4, "REQ-105", "requestedAt"),
                Problem.missingField(5, "REQ-106", "description"),
                Problem.duplicate(6, "REQ-107", "REQ-101"),
                Problem.duplicateWithEscalation(7, "REQ-108", "REQ-101", MEDIUM, CRITICAL),
                Problem.conflictingRequestId(8, "REQ-109"),
                Problem.conflictingRequestId(9, "REQ-109"));
    }

    @Test
    void traceShowsCountsPerStage() {
        assertThat(result.trace()).extracting(StageTrace::stage, StageTrace::in, StageTrace::out).containsExactly(
                tuple("validate", 13, 10),
                tuple("deduplicate", 10, 6),
                tuple("prioritize", 6, 6));
        assertThat(result.trace().get(0).notes()).containsExactly(
                "REQ-103 rejected: missing equipmentId",
                "REQ-105 rejected: missing requestedAt",
                "REQ-106 rejected: missing description");
        assertThat(result.trace().get(1).notes()).containsExactly(
                "REQ-109 rejected: 2 requests share this id with different content",
                "REQ-101 kept; merged duplicates REQ-107, REQ-108",
                "REQ-101 escalated MEDIUM to CRITICAL (from REQ-108)");
    }

    @Test
    void escalatedRequestKeepsItsOriginalTime() {
        WorkRequest escalated = result.accepted().get(0);

        assertThat(escalated.requestedAt()).isEqualTo(Instant.parse("2026-10-20T08:15:00Z"));
    }

    @Test
    void exposesTheEscalationAsStructuredFieldsOnOneProblemOnly() {
        assertThat(result.problems())
                .filteredOn(problem -> problem.escalatedTo() != null)
                .extracting(Problem::requestId, Problem::relatedRequestId, Problem::escalatedFrom, Problem::escalatedTo)
                .containsExactly(tuple("REQ-108", "REQ-101", MEDIUM, CRITICAL));
    }
}
