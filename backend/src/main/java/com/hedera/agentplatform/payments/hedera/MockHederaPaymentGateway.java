package com.hedera.agentplatform.payments.hedera;

import org.springframework.stereotype.Component;

@Component
public class MockHederaPaymentGateway implements HederaPaymentGateway {
    public PaymentResult transferHbar(String destination, String amount) { return mock(); }
    public PaymentResult transferToken(String tokenId, String destination, String amount) { return mock(); }
    private PaymentResult mock() { return new PaymentResult("0.0.mock@0", "CONFIRMED", true); }
}
