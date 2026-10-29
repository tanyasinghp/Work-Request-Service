package com.tanya.workrequests.api.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** The one error body every failed request gets. Never contains stack traces or internals. */
public record ApiError(int status, String error, String message) {

    public static ApiError of(HttpStatusCode status, String message) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return new ApiError(status.value(), known == null ? "Error" : known.getReasonPhrase(), message);
    }
}
