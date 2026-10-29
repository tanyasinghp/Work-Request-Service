package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * The 13-request demo batch from {@code sample-batch.json}, served as-is so the UI can post it
 * straight back. Loaded once at startup, so a missing or broken file stops the app early.
 */
public class SampleBatchSource {

    static final String RESOURCE = "/sample-batch.json";

    private final ArrayNode batch;

    public SampleBatchSource(ObjectMapper objectMapper) {
        this.batch = load(objectMapper);
    }

    /** A fresh copy each call, so no caller can change what the next one gets. */
    public ArrayNode raw() {
        return batch.deepCopy();
    }

    private static ArrayNode load(ObjectMapper objectMapper) {
        try (InputStream json = SampleBatchSource.class.getResourceAsStream(RESOURCE)) {
            if (json == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalStateException(RESOURCE + " must contain a JSON array");
            }
            return (ArrayNode) root;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
