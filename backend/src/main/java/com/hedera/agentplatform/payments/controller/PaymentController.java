package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @GetMapping
  public List<PaymentResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public PaymentResponse findById(@PathVariable String id) {
    return service.findById(id);
  }

  /** Checks the policy, then sends the transfer or holds it for approval. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
    return service.create(request);
  }

  @PostMapping("/{id}/approve")
  public PaymentResponse approve(@PathVariable String id) {
    return service.approve(id);
  }

  @PostMapping("/{id}/reject")
  public PaymentResponse reject(@PathVariable String id) {
    return service.reject(id);
  }

  /** Tells the UI whether payments really reach Hedera. */
  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("ledgerActive", service.isLedgerActive());
  }
}
