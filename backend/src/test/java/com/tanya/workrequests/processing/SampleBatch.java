package com.tanya.workrequests.processing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tanya.workrequests.domain.WorkRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Loads the demo batch from {@code sample-batch.json}, the same file the API and UI will use. */
final class SampleBatch {

    private static final String RESOURCE = "/sample-batch.json";

    private SampleBatch() {
    }

    static List<WorkRequest> load() {
        JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        try (InputStream json = SampleBatch.class.getResourceAsStream(RESOURCE)) {
            if (json == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            return mapper.readValue(json, new TypeReference<List<WorkRequest>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
