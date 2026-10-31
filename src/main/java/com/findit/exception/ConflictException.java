package com.findit.exception;

import org.springframework.http.HttpStatus;

/**
 * The action conflicts with the current state of the resource
 * (for example a duplicate claim or an illegal status transition).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }
}
