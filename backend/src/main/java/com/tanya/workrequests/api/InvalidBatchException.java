package com.tanya.workrequests.api;

/**
 * The request as a whole can't be processed (not a JSON array, or too many elements).
 * Problems with individual requests are never exceptions; they are returned as data.
 */
public class InvalidBatchException extends RuntimeException {

    public InvalidBatchException(String message) {
        super(message);
    }
}
