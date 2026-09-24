package com.hedera.agentplatform.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Two payments decided at the same instant must not both spend the same envelope money.
 *
 * <p>Not {@code @Transactional}: the payments run on separate threads and must see each other's
 * committed rows, as in production. The rows are removed afterwards.
 */
@SpringBootTest
@TestPropertySource(properties = "payments.runways=0.0.8888=1000")
class PaymentPolicyConcurrencyTest {

  private static final String TOKEN = "0.0.8888";
  private static final String RECIPIENT = "0.0.8801";

  @Autowired private PaymentService payments;
  @Autowired private PaymentRepository repository;

  /** The recipient has been paid before, so only the envelope rules apply. */
  @BeforeEach
  void knownRecipient() {
    PaymentEntity earlier = new PaymentEntity();
    earlier.id = "pay_" + UUID.randomUUID();
    earlier.destination = RECIPIENT;
    earlier.amount = BigDecimal.ONE;
    earlier.amountUnits = 1L;
    earlier.currency = TOKEN;
    earlier.tokenId = TOKEN;
    earlier.status = "CONFIRMED";
    earlier.createdAt = Instant.now();
    earlier.updatedAt = earlier.createdAt;
    repository.saveAndFlush(earlier);
  }

  @AfterEach
  void cleanUp() {
    repository.deleteAll(
        repository.findAll().stream().filter(p -> TOKEN.equals(p.currency)).toList());
  }

  private CreatePaymentRequest essentials(long units) {
    return new CreatePaymentRequest(RECIPIENT, String.valueOf(units), TOKEN, "ESSENTIALS", null);
  }

  @Test
  void concurrent_payments_cannot_together_spend_more_than_the_envelope() throws Exception {
    // Essentials = 300. One payment of 140 is within 50% of 300 and is allowed; after it only 160
    // is left, and 140 is more than half of that. So exactly one of these may go through.
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
    // 200 is more than half of 300: held for a reviewer.
    PaymentResponse held = payments.create(essentials(200));
    assertThat(held.status()).isEqualTo("AWAITING_APPROVAL");

    // Meanwhile 150 is spent from the same envelope (exactly half of 300: allowed).
    assertThat(payments.create(essentials(150)).policyVerdict()).isEqualTo("ALLOW");

    // Only 150 is left: approving the 200 would overspend, so the policy now refuses it.
    PaymentResponse approved = payments.approve(held.id());

    assertThat(approved.status()).isEqualTo("REJECTED");
    assertThat(approved.policyVerdict()).isEqualTo("DENY");
    assertThat(approved.policyRuleId()).isEqualTo("funds.insufficient");
    assertThat(approved.transactionId()).isNull();
  }
}
