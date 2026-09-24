package com.hedera.agentplatform.tokens.controller;

import com.hedera.agentplatform.tokens.service.TokenMirrorUnavailableException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors specific to Tokens, in the same shape as the shared handler's. Scoped to the Tokens
 * controller so these mappings never change how other modules' errors are answered.
 */
@RestControllerAdvice(assignableTypes = TokenController.class)
class TokensExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map<String, String> handleNotFound(NoSuchElementException exception) {
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, String> handleConcurrentUpdate(OptimisticLockingFailureException exception) {
    return Map.of("error", "The operation was changed by another request, reload and retry");
  }

  @ExceptionHandler(TokenMirrorUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, String> handleMirrorUnavailable(TokenMirrorUnavailableException exception) {
    return Map.of("error", exception.getMessage());
  }
}
