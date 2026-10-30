package com.tanya.workrequests.domain;

public enum ProblemCode {
    NULL_REQUEST,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE,
    CONFLICTING_REQUEST_ID,
    // Appended last so existing ordinals (and anything persisted or asserted against them) stay put.
    OVER_LOW_PRIORITY_CAP
}
