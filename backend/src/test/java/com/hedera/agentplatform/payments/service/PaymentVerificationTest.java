package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.entity.PaymentStatus;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.MirrorTransaction;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Mirror Node is the source of truth for what happened to a transfer, including one left
 * SUBMITTED because the backend stopped mid-transfer.
 */
@SpringBootTest
@Transactional
class PaymentVerificationTest {

  private static final String SENDER = "0.0.5239440";
  private static final String RECIPIENT = "0.0.10682427";
  private static final String TX = SENDER + "@1790194819.526000707";

  @Autowired private PaymentService service;
  @Autowired private PaymentRepository repository;
  @MockitoBean private PaymentMirrorClient mirror;

  private PaymentEntity payment(PaymentStatus status, Instant updatedAt) {
    PaymentEntity p = new PaymentEntity();
    p.id = "pay_" + status + "_" + updatedAt.toEpochMilli();
    p.amount = new BigDecimal("0.01");
    p.amountUnits = 1_000_000L;
    p.currency = "HBAR";
    p.destination = RECIPIENT;
    p.status = status.name();
    p.transactionId = status == PaymentStatus.REJECTED ? null : TX;
    p.createdAt = updatedAt;
    p.updatedAt = updatedAt;
    return repository.saveAndFlush(p);
  }

  private static Instant stale() {
    return Instant.now().minus(PaymentService.SETTLE_AFTER).minusSeconds(60);
  }

  private void ledgerHas(String result, long received, long paid) {
    when(mirror.findTransaction(anyString()))
        .thenReturn(
            MirrorLookup.found(
                new MirrorTransaction(
                    result,
                    "CRYPTOTRANSFER",
                    "1790194854.872086514",
                    Map.of(RECIPIENT, received, SENDER, -paid),
                    List.of())));
  }

  @Test
  void a_payment_left_submitted_is_confirmed_from_the_ledger() {
    PaymentEntity p = payment(PaymentStatus.SUBMITTED, stale());
    ledgerHas("SUCCESS", 1_000_000L, 1_128_158L);

    PaymentVerification v = service.verify(p.id);

    assertThat(v.verified()).isTrue();
    assertThat(v.paymentStatus()).isEqualTo("CONFIRMED");
    assertThat(v.checks()).extracting("ok").containsOnly(true);
    assertThat(repository.findById(p.id).orElseThrow().sourceAccount).isEqualTo(SENDER);
  }

  @Test
  void a_payment_left_submitted_that_the_network_refused_ends_failed() {
    PaymentEntity p = payment(PaymentStatus.SUBMITTED, stale());
    ledgerHas("INSUFFICIENT_ACCOUNT_BALANCE", 0L, 128_158L);

    PaymentVerification v = service.verify(p.id);

    assertThat(v.paymentStatus()).isEqualTo("FAILED");
    assertThat(repository.findById(p.id).orElseThrow().failureReason)
        .isEqualTo("INSUFFICIENT_ACCOUNT_BALANCE");
    // The ledger agrees with the recorded failure, so the record is consistent.
    assertThat(v.verified()).isTrue();
    assertThat(v.detail()).contains("confirms the transfer failed").contains("fee");
  }

  @Test
  void a_failed_payment_that_the_ledger_shows_as_successful_is_flagged() {
    PaymentEntity p = payment(PaymentStatus.FAILED, Instant.now());
    p.failureReason = "INVALID_ACCOUNT_ID";
    repository.saveAndFlush(p);
    ledgerHas("SUCCESS", 1_000_000L, 1_128_158L);

    PaymentVerification v = service.verify(p.id);

    assertThat(v.verified()).isFalse();
    assertThat(v.checks()).extracting("ok").containsOnly(false);
  }

  @Test
  void a_stale_payment_unknown_to_the_ledger_never_happened() {
    PaymentEntity p = payment(PaymentStatus.SUBMITTED, stale());
    when(mirror.findTransaction(anyString())).thenReturn(MirrorLookup.notFound());

    PaymentVerification v = service.verify(p.id);

    assertThat(v.paymentStatus()).isEqualTo("FAILED");
    assertThat(v.detail()).contains("never reached consensus");
  }

  @Test
  void a_recent_submitted_payment_is_left_alone_while_it_may_still_be_in_flight() {
    PaymentEntity p = payment(PaymentStatus.SUBMITTED, Instant.now());
    when(mirror.findTransaction(anyString())).thenReturn(MirrorLookup.notFound());

    assertThat(service.verify(p.id).paymentStatus()).isEqualTo("SUBMITTED");
  }

  @Test
  void nothing_is_concluded_when_the_mirror_node_is_down() {
    PaymentEntity p = payment(PaymentStatus.SUBMITTED, stale());
    when(mirror.findTransaction(anyString())).thenReturn(MirrorLookup.unavailable());

    PaymentVerification v = service.verify(p.id);

    assertThat(v.paymentStatus()).isEqualTo("SUBMITTED");
    assertThat(v.detail()).contains("could not be reached");
  }

  @Test
  void a_ledger_amount_that_differs_from_the_record_fails_verification() {
    PaymentEntity p = payment(PaymentStatus.CONFIRMED, Instant.now());
    ledgerHas("SUCCESS", 500_000L, 628_158L);

    PaymentVerification v = service.verify(p.id);

    assertThat(v.verified()).isFalse();
    assertThat(v.checks())
        .filteredOn(c -> c.name().equals("Recipient received"))
        .extracting("ok")
        .containsExactly(false);
  }

  @Test
  void a_blocked_payment_says_no_transaction_was_created() {
    PaymentEntity p = payment(PaymentStatus.REJECTED, Instant.now());

    PaymentVerification v = service.verify(p.id);

    assertThat(v.verified()).isFalse();
    assertThat(v.detail()).contains("no transaction was created");
  }

  @Test
  void stale_payments_are_settled_in_bulk() {
    payment(PaymentStatus.SUBMITTED, stale());
    ledgerHas("SUCCESS", 1_000_000L, 1_128_158L);

    assertThat(service.settleStalePayments()).isEqualTo(1);
  }
}
