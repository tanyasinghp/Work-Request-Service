package com.tanya.workrequests.domain;

import java.util.Objects;

/** A field that was present in the input but couldn't be read, e.g. priority "URGENT". */
public record InvalidField(String field, String rawValue) {

    public InvalidField {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(rawValue, "rawValue");
    }
}
