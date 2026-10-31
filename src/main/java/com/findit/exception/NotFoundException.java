package com.findit.exception;

import org.springframework.http.HttpStatus;

/**
 * The resource does not exist (or must not be revealed to the caller).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message);
    }
}
