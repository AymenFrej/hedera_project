package com.hedera.agentplatform.tokens.service;

/** A fact the request needs could not be read from the Mirror Node: answered 503, retry later. */
public class TokenMirrorUnavailableException extends RuntimeException {
  public TokenMirrorUnavailableException(String message) {
    super(message);
  }
}
