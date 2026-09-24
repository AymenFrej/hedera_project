package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.payments.service.MirrorNodeUnavailableException;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errors specific to Payments, in the same shape as the shared handler's. Scoped to the Payments
 * controller so these mappings never change how other modules' errors are answered.
 */
@RestControllerAdvice(assignableTypes = PaymentController.class)
class PaymentsExceptionHandler {

  /** The payment is not in a state that allows the action, e.g. approving a payment already sent. */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, String> handleIllegalState(IllegalStateException exception) {
    return Map.of("error", exception.getMessage());
  }

  /** Two requests changed the same payment at once (e.g. two approvals); the caller should reload. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, String> handleConcurrentUpdate(OptimisticLockingFailureException exception) {
    return Map.of("error", "The payment was changed by another request, reload and retry");
  }

  /** A fact the request needs could not be read from the Mirror Node: retry later, not a 500. */
  @ExceptionHandler(MirrorNodeUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, String> handleMirrorUnavailable(MirrorNodeUnavailableException exception) {
    return Map.of("error", exception.getMessage());
  }
}
