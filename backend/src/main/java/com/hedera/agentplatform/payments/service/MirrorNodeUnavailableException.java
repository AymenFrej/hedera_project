package com.hedera.agentplatform.payments.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A fact needed to handle the request could not be read from the Mirror Node right now. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MirrorNodeUnavailableException extends RuntimeException {
  public MirrorNodeUnavailableException(String message) {
    super(message);
  }
}
