package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tanya.workrequests.domain.Priority.CRITICAL;
import static com.tanya.workrequests.domain.Priority.HIGH;
import static com.tanya.workrequests.domain.Priority.LOW;
import static com.tanya.workrequests.domain.Priority.MEDIUM;
import static com.tanya.workrequests.processing.TestRequests.indexed;
import static com.tanya.workrequests.processing.TestRequests.request;
import static org.assertj.core.api.Assertions.assertThat;

class WorkRequestSorterTest {

    private static final String T0800 = "2026-10-20T08:00:00Z";
    private static final String T0900 = "2026-10-20T09:00:00Z";

    private final WorkRequestSorter sorter = new WorkRequestSorter();

    @Test
    void sortsByPriorityWeightDescending() {
        StageResult result = sorter.sort(indexed(
                request("REQ-1", "EQ-1", LOW, T0800, "a"),
                request("REQ-2", "EQ-2", CRITICAL, T0800, "b"),
                request("REQ-3", "EQ-3", MEDIUM, T0800, "c"),
                request("REQ-4", "EQ-4", HIGH, T0800, "d")));

        assertThat(idsOf(result)).containsExactly("REQ-2", "REQ-4", "REQ-3", "REQ-1");
    }

    @Test
    void sortsEqualPriorityByEarliestRequestedAt() {
        StageResult result = sorter.sort(indexed(
                request("REQ-1", "EQ-1", HIGH, T0900, "a"),
                request("REQ-2", "EQ-2", HIGH, T0800, "b")));

        assertThat(idsOf(result)).containsExactly("REQ-2", "REQ-1");
    }

    @Test
    void sortsEqualPriorityAndTimeByRequestId() {
        StageResult result = sorter.sort(indexed(
                request("REQ-3", "EQ-3", HIGH, T0800, "c"),
                request("REQ-1", "EQ-1", HIGH, T0800, "a"),
                request("REQ-2", "EQ-2", HIGH, T0800, "b")));

        assertThat(idsOf(result)).containsExactly("REQ-1", "REQ-2", "REQ-3");
    }

    @Test
    void comparesRequestIdsAsText() {
        // Ids are opaque strings, so "REQ-10" sorts before "REQ-9". Documented assumption.
        StageResult result = sorter.sort(indexed(
                request("REQ-9", "EQ-9", HIGH, T0800, "a"),
                request("REQ-10", "EQ-10", HIGH, T0800, "b")));

        assertThat(idsOf(result)).containsExactly("REQ-10", "REQ-9");
    }

    @Test
    void keepsInputIndexWithEachRequest() {
        StageResult result = sorter.sort(indexed(
                request("REQ-1", "EQ-1", LOW, T0800, "a"),
                request("REQ-2", "EQ-2", CRITICAL, T0800, "b")));

        assertThat(result.requests()).extracting(IndexedRequest::inputIndex).containsExactly(1, 0);
    }

    @Test
    void recordsTraceWithoutDroppingRequests() {
        StageResult result = sorter.sort(indexed(
                request("REQ-1", "EQ-1", LOW, T0800, "a"),
                request("REQ-2", "EQ-2", CRITICAL, T0800, "b")));

        assertThat(result.problems()).isEmpty();
        assertThat(result.trace().stage()).isEqualTo("prioritize");
        assertThat(result.trace().in()).isEqualTo(2);
        assertThat(result.trace().out()).isEqualTo(2);
        assertThat(result.trace().notes()).hasSize(1);
    }

    private static List<String> idsOf(StageResult result) {
        return result.requests().stream()
                .map(IndexedRequest::request)
                .map(WorkRequest::requestId)
                .toList();
    }
}
