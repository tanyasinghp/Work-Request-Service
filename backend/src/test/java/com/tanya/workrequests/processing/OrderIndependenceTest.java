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
    // Far enough ahead that the system clock reads it as future for the life of this codebase.
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");
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

        assertThat(batch).hasSize(201);
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
     * 201 requests built from a fixed seed: 100 distinct, 40 duplicates (some three-way, some
     * tied on time), 15 exact repeats, 23 in conflict groups, 17 invalid (including unreadable
     * priority and date values), 1 dated in the future, 5 null. Times sit on a 15-minute grid
     * so priority and time ties are common.
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

        // Otherwise valid, but dated in the future: rejected in validate, so it never reaches dedup.
        batch.add(new WorkRequest("FUT-000", "FEQ-0", Priority.CRITICAL, FAR_FUTURE, "Future task"));

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
