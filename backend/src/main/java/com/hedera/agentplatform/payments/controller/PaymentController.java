package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.audit.dto.AuditEventResponse;
import com.hedera.agentplatform.audit.mirror.VerificationResult;
import com.hedera.agentplatform.payments.dto.BalanceResponse;
import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.dto.PaymentPreview;
import com.hedera.agentplatform.payments.entity.ContactEntity;
import com.hedera.agentplatform.payments.dto.PaymentReceipt;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.dto.PaymentVerification;
import com.hedera.agentplatform.payments.service.ContactService;
import com.hedera.agentplatform.payments.service.IntentService;
import com.hedera.agentplatform.payments.service.PaymentPreviewService;
import com.hedera.agentplatform.payments.service.PaymentReceiptService;
import com.hedera.agentplatform.payments.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
  private final PaymentService service;
  private final PaymentAccess access;
  private final PaymentPreviewService previews;
  private final PaymentReceiptService receipts;
  private final IntentService intents;
  private final ContactService contacts;
  private final String demoTokenId;
  private final String demoRecipientId;

  public PaymentController(
      PaymentService service,
      PaymentAccess access,
      PaymentPreviewService previews,
      PaymentReceiptService receipts,
      IntentService intents,
      ContactService contacts,
      @Value("${payments.demo-token-id:}") String demoTokenId,
      @Value("${payments.demo-recipient-id:}") String demoRecipientId) {
    this.service = service;
    this.access = access;
    this.previews = previews;
    this.receipts = receipts;
    this.intents = intents;
    this.contacts = contacts;
    this.demoTokenId = demoTokenId.isBlank() ? null : demoTokenId.trim();
    this.demoRecipientId = demoRecipientId.isBlank() ? null : demoRecipientId.trim();
  }

  @GetMapping
  public List<PaymentResponse> findAll(HttpServletRequest http) {
    boolean all = access.seesEverything(http);
    return service.findAll().stream().filter(p -> all || access.canSee(http, p)).toList();
  }

  @GetMapping("/{id}")
  public PaymentResponse findById(@PathVariable String id, HttpServletRequest http) {
    return visible(id, http);
  }

  /** Checks the policy, then sends the transfer or holds it for approval. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentResponse create(
      @Valid @RequestBody CreatePaymentRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return service.create(request, idempotencyKey);
  }

  /**
   * What executing this payment would do: policy verdict and ledger facts. Nothing is recorded,
   * sent or audited, and it authorizes nothing: executing asks the policy again.
   */
  @PostMapping("/preview")
  public PaymentPreview preview(@Valid @RequestBody CreatePaymentRequest request) {
    return previews.preview(request);
  }

  /**
   * Resolves what someone wants to pay (a contact name, a token symbol, a condition) into a payment
   * request, saying where each value came from. Nothing is recorded or sent.
   */
  @PostMapping("/intent/understand")
  public IntentUnderstanding understand(@RequestBody PaymentIntent intent) {
    return intents.understand(intent);
  }

  @GetMapping("/contacts")
  public List<ContactEntity> contacts() {
    return contacts.list();
  }

  @PostMapping("/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  public ContactEntity addContact(@RequestBody Map<String, String> body) {
    return contacts.add(body.get("name"), body.get("accountId"));
  }

  @DeleteMapping("/contacts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteContact(@PathVariable String id) {
    contacts.delete(id);
  }

  @PostMapping("/{id}/approve")
  public PaymentResponse approve(@PathVariable String id, HttpServletRequest http) {
    access.requireReviewer(http);
    return service.approve(id);
  }

  @PostMapping("/{id}/reject")
  public PaymentResponse reject(@PathVariable String id, HttpServletRequest http) {
    access.requireReviewer(http);
    return service.reject(id);
  }

  /**
   * Everything the result screen shows: status, the Mirror Node's view of the transaction, the
   * timeline built from the payment's audit events, and each event verified on HCS.
   */
  @GetMapping("/{id}/result")
  public PaymentReceipt result(@PathVariable String id, HttpServletRequest http) {
    visible(id, http);
    return receipts.receipt(id);
  }

  /** The audit events this payment wrote. */
  @GetMapping("/{id}/audit")
  public List<AuditEventResponse> audit(@PathVariable String id, HttpServletRequest http) {
    visible(id, http);
    return receipts.auditEvents(id);
  }

  /** Reads one of this payment's audit events back from HCS. */
  @GetMapping("/{id}/audit/{eventId}/verification")
  public VerificationResult verifyAudit(
      @PathVariable String id, @PathVariable String eventId, HttpServletRequest http) {
    visible(id, http);
    return receipts.verifyAuditEvent(id, eventId);
  }

  /** Balances of the account payments leave from, from the Mirror Node. Facts only. */
  @GetMapping("/balance")
  public BalanceResponse balance() {
    return service.balance();
  }

  /** Reads the transfer back from the Mirror Node and compares it with the payment. */
  @GetMapping("/{id}/verification")
  public PaymentVerification verify(@PathVariable String id, HttpServletRequest http) {
    visible(id, http);
    return service.verify(id);
  }

  /** The payment, if the caller may see it; otherwise 404, as if it did not exist. */
  private PaymentResponse visible(String id, HttpServletRequest http) {
    PaymentResponse payment = service.findById(id);
    access.requireVisible(http, payment);
    return payment;
  }

  /**
   * Tells the UI whether payments really reach Hedera, and which demo token and associated
   * recipient are configured (null when none).
   */
  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("ledgerActive", service.isLedgerActive());
    status.put("demoTokenId", demoTokenId);
    status.put("demoRecipientId", demoRecipientId);
    return status;
  }
}
