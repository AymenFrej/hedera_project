package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.payments.dto.PaymentResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    public List<PaymentResponse> findAll() {
        return List.of(new PaymentResponse("pay_demo", "50", "HBAR", "0.0.12345", "MOCK"));
    }
}
