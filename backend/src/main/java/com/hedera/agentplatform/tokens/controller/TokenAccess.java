package com.hedera.agentplatform.tokens.controller;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.tokens.dto.TokenViews.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Who sees which token operations. The Accounts interceptor already lets only USER and ADMIN reach
 * /tokens. Ledger facts (portfolio, passport) are public on Hedera anyway; the operations a person
 * asked for are theirs: a USER sees their own, an ADMIN sees all.
 */
@Component
class TokenAccess {

  private final AuthSessionService sessions;

  TokenAccess(AuthSessionService sessions) {
    this.sessions = sessions;
  }

  private UserEntity user(HttpServletRequest request) {
    return sessions.require(request.getHeader("Authorization"));
  }

  /** null for an ADMIN (everything), the user's id otherwise. */
  String scope(HttpServletRequest request) {
    UserEntity u = user(request);
    return "ADMIN".equals(u.role) ? null : u.id;
  }

  /** 404 rather than 403: a user is not told that someone else's operation exists. */
  void requireVisible(HttpServletRequest request, Operation op) {
    String scope = scope(request);
    if (scope != null && !scope.equals(op.requestedById())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown token operation: " + op.id());
    }
  }
}
