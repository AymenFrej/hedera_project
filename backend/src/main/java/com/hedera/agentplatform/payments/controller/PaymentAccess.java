package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.entity.UserEntity;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Who may see and decide what, inside Payments. The Accounts module's interceptor already lets only
 * USER and ADMIN reach /payments; this adds the rules specific to payments:
 *
 * <ul>
 *   <li>A USER sees only the payments they requested; an ADMIN sees all.
 *   <li>Only an ADMIN answers a held payment. A policy HOLD means "a human other than the requester
 *       must agree": letting the requester approve their own payment would defeat it.
 * </ul>
 */
@Component
class PaymentAccess {

  private final AuthSessionService sessions;

  PaymentAccess(AuthSessionService sessions) {
    this.sessions = sessions;
  }

  private UserEntity user(HttpServletRequest request) {
    return sessions.require(request.getHeader("Authorization"));
  }

  boolean seesEverything(HttpServletRequest request) {
    return "ADMIN".equals(user(request).role);
  }

  boolean canSee(HttpServletRequest request, PaymentResponse payment) {
    UserEntity u = user(request);
    return "ADMIN".equals(u.role) || u.id.equals(payment.requestedById());
  }

  /** 404 rather than 403: a user is not told that someone else's payment exists. */
  void requireVisible(HttpServletRequest request, PaymentResponse payment) {
    if (!canSee(request, payment)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown payment: " + payment.id());
    }
  }

  void requireReviewer(HttpServletRequest request) {
    if (!"ADMIN".equals(user(request).role)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only an administrator can answer a held payment");
    }
  }
}
