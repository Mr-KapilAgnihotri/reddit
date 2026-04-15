package com.kapil.reddit.common.exception;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 via GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
