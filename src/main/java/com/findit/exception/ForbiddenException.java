package com.findit.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated but not allowed to perform the action.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "forbidden", message);
    }
}
