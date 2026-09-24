package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import com.hedera.agentplatform.policies.service.EnvelopeLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Two payments decided at the same instant must not both spend the same envelope money.
 *
 * <p>Not {@code @Transactional}: the payments run on separate threads and must see each other's
 * committed rows, as in production. The ledger is put back to its opening position afterwards.
 */
@SpringBootTest
class PaymentPolicyConcurrencyTest {

  private static final String TOKEN = "0.0.8888";
  private static final String RECIPIENT = "0.0.8801";

  @Autowired private PaymentService payments;

  @Autowired private PaymentRepository repository;
  @Autowired private EnvelopeLedger ledger;

  /** Token decimals without asking the real Mirror Node. */
  @MockitoBean private TokenDecimals decimals;

  @BeforeEach
  void tokenDecimals() {
    when(decimals.of(null)).thenReturn(TokenDecimals.HBAR);
    when(decimals.of(anyString())).thenReturn(0);
  }

  /**
   * Amounts here are in HBAR: the service converts them to the tinybars the envelopes are held in.
   * The envelope opens at 300 ℏ, so these figures mean what they read as.
   */
  /**
   * Envelopes are a budget in HBAR, so a test about spending one pays in HBAR: a null token id is
   * the native asset. Paying a token here would assert envelope arithmetic on an asset the
   * envelopes deliberately do not count.
   */
  private CreatePaymentRequest essentials(long units) {
    return new CreatePaymentRequest(RECIPIENT, String.valueOf(units), null, "ESSENTIALS", null);
  }

  /**
   * Opening position (essentials 300 ℏ), then a first 1 ℏ payment to the recipient, held and
   * approved: that vouches for the recipient and leaves essentials at 299 ℏ.
   */
  @BeforeEach
  void knownRecipient() {
    ledger.reset();
    payments.approve(payments.create(essentials(1)).id());
  }

  @AfterEach
  void cleanUp() {
    repository.deleteAll(
        repository.findAll().stream().filter(p -> TOKEN.equals(p.currency)).toList());
    ledger.reset();
  }

  @Test
  void concurrent_payments_cannot_together_spend_more_than_the_envelope() throws Exception {
    // Essentials = 299 ℏ after the vouching payment. 140 ℏ is within half of it and is allowed;
    // after it only 159 ℏ is left, and 140 ℏ is more than half of that. So exactly one goes through.
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<PaymentResponse>> results = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Callable<PaymentResponse> task =
          () -> {
            start.await();
            return payments.create(essentials(140));
          };
      results.add(pool.submit(task));
    }
    start.countDown();

    long allowed = 0;
    for (Future<PaymentResponse> r : results) {
      if ("ALLOW".equals(r.get().policyVerdict())) {
        allowed++;
      }
    }
    pool.shutdown();

    assertThat(allowed).as("payments allowed out of %d concurrent ones", threads).isEqualTo(1);
  }

  @Test
  void approving_a_held_payment_after_its_envelope_was_used_up_is_refused() {
    // 200 ℏ is more than half of 299 ℏ: held for a reviewer.
    PaymentResponse held = payments.create(essentials(200));
    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");

    // Meanwhile 149 ℏ is spent from the same envelope (within half of 299: allowed).
    assertThat(payments.create(essentials(149)).policyVerdict()).isEqualTo("ALLOW");

    // Only 150 ℏ is left: the Policies module refuses the approval as no longer affordable.
    PaymentResponse approved = payments.approve(held.id());

    assertThat(approved.status()).isEqualTo("REJECTED");
    assertThat(approved.failureReason()).contains("no longer affordable");
    assertThat(approved.transactionId()).isNull();
  }
}
