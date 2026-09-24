package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.payments.service.MirrorNodeUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errors specific to Payments, in the same shape as the shared handler's. */
@RestControllerAdvice(assignableTypes = PaymentController.class)
class PaymentsExceptionHandler {

  /** A fact the request needs could not be read from the Mirror Node: retry later, not a 500. */
  @ExceptionHandler(MirrorNodeUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, String> handleMirrorUnavailable(MirrorNodeUnavailableException exception) {
    return Map.of("error", exception.getMessage());
  }
}
