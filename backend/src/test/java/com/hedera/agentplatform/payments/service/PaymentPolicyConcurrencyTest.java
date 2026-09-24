package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

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

  private CreatePaymentRequest essentials(long units) {
    return new CreatePaymentRequest(RECIPIENT, String.valueOf(units), TOKEN, "ESSENTIALS", null);
  }

  /**
   * Opening position (essentials 300), then a first 1-unit payment to the recipient, held and
   * approved: that vouches for the recipient and leaves essentials at 299.
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
    // Essentials = 299 after the vouching payment. 140 is within half of it and is allowed; after
    // it only 159 is left, and 140 is more than half of that. So exactly one may go through.
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
    // 200 is more than half of 299: held for a reviewer.
    PaymentResponse held = payments.create(essentials(200));
    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");

    // Meanwhile 149 is spent from the same envelope (within half of 299: allowed).
    assertThat(payments.create(essentials(149)).policyVerdict()).isEqualTo("ALLOW");

    // Only 150 is left: the Policies module refuses the approval as no longer affordable.
    PaymentResponse approved = payments.approve(held.id());

    assertThat(approved.status()).isEqualTo("REJECTED");
    assertThat(approved.failureReason()).contains("no longer affordable");
    assertThat(approved.transactionId()).isNull();
  }
}
