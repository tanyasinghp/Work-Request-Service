package com.tanya.workrequests.processing;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tanya.workrequests.api.RequestMapper;
import com.tanya.workrequests.api.SampleBatchSource;
import com.tanya.workrequests.domain.WorkRequest;

import java.util.List;

/**
 * Test shortcut: the demo batch as the API reads it, via the main-code {@link SampleBatchSource}
 * and {@link RequestMapper}. The sample has no unreadable values, so the requests are enough.
 */
final class SampleBatch {

    private SampleBatch() {
    }

    static List<WorkRequest> load() {
        SampleBatchSource source = new SampleBatchSource(JsonMapper.builder().findAndAddModules().build());
        return new RequestMapper().map(source.raw()).requests();
    }
}
