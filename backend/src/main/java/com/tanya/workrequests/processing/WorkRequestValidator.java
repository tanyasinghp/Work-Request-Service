package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rejects null and incomplete requests. Every missing field is its own problem, so a
 * request missing two fields reports both. Blank strings count as missing.
 */
public class WorkRequestValidator {

    static final String STAGE = "validate";

    public StageResult validate(List<WorkRequest> batch) {
        List<IndexedRequest> valid = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (int index = 0; index < batch.size(); index++) {
            WorkRequest request = batch.get(index);
            List<Problem> requestProblems = problemsFor(index, request);
            if (requestProblems.isEmpty()) {
                valid.add(new IndexedRequest(index, request));
            } else {
                problems.addAll(requestProblems);
                notes.add(rejectionNote(index, requestProblems));
            }
        }

        StageTrace trace = new StageTrace(STAGE, batch.size(), valid.size(), notes);
        return new StageResult(valid, problems, trace);
    }

    private static List<Problem> problemsFor(int index, WorkRequest request) {
        if (request == null) {
            return List.of(Problem.nullRequest(index));
        }
        // A blank id is reported as no id at all, matching "blank counts as missing".
        String requestId = isBlank(request.requestId()) ? null : request.requestId();

        List<Problem> problems = new ArrayList<>();
        if (requestId == null) {
            problems.add(Problem.missingField(index, null, "requestId"));
        }
        if (isBlank(request.equipmentId())) {
            problems.add(Problem.missingField(index, requestId, "equipmentId"));
        }
        if (request.priority() == null) {
            problems.add(Problem.missingField(index, requestId, "priority"));
        }
        if (request.requestedAt() == null) {
            problems.add(Problem.missingField(index, requestId, "requestedAt"));
        }
        if (isBlank(request.description())) {
            problems.add(Problem.missingField(index, requestId, "description"));
        }
        return problems;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rejectionNote(int index, List<Problem> problems) {
        Problem first = problems.get(0);
        String label = first.requestId() != null ? first.requestId() : "Request at index " + index;
        String reasons = problems.stream()
                .map(problem -> problem.field() != null ? "missing " + problem.field() : "null request")
                .collect(Collectors.joining(", "));
        return label + " rejected: " + reasons;
    }
}
