package com.tanya.workrequests.api;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleBatchSourceTest {

    private final SampleBatchSource source = new SampleBatchSource(JsonMapper.builder().build());

    @Test
    void servesTheThirteenRawRequests() {
        ArrayNode batch = source.raw();

        assertThat(batch).hasSize(13);
        assertThat(batch.get(0).get("requestId").textValue()).isEqualTo("REQ-101");
        // Raw means raw: the deliberately broken values are served unchanged.
        assertThat(batch.get(2).get("equipmentId").isNull()).isTrue();
        assertThat(batch.get(5).get("description").textValue()).isEqualTo("   ");
    }

    @Test
    void returnsAnIndependentCopyEachTime() {
        ArrayNode first = source.raw();
        first.removeAll();

        assertThat(source.raw()).hasSize(13);
    }
}
