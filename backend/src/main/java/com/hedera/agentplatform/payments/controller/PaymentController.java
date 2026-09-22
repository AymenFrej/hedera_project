package com.hedera.agentplatform.payments.controller;

import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.service.PaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    @GetMapping public List<PaymentResponse> findAll() { return service.findAll(); }
}
