package com.hedera.agentplatform.shared.exception;

import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** The resource is not in a state that allows the action, e.g. approving a sent payment. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> handleIllegalState(IllegalStateException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** Someone else changed the same record at the same time; the caller should reload. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> handleConcurrentUpdate(OptimisticLockingFailureException exception) {
        return Map.of("error", "The record was changed by another request, reload and retry");
    }
}
