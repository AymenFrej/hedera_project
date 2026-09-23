package com.hedera.agentplatform.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * After a restart, settles payments that were left SUBMITTED by asking the Mirror Node what
 * happened to them. Without this they would stay "in progress" forever.
 */
@Component
class StalePaymentSettler {

  private static final Logger log = LoggerFactory.getLogger(StalePaymentSettler.class);

  private final PaymentService service;

  StalePaymentSettler(PaymentService service) {
    this.service = service;
  }

  @EventListener(ApplicationReadyEvent.class)
  void settleOnStartup() {
    try {
      int settled = service.settleStalePayments();
      if (settled > 0) {
        log.info("Settled {} payment(s) left SUBMITTED before the last shutdown", settled);
      }
    } catch (RuntimeException e) {
      // Never block startup on this; verification settles them later anyway.
      log.warn("Could not settle payments left SUBMITTED: {}", e.getMessage());
    }
  }
}
