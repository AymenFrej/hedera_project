package com.hedera.agentplatform.payments.hedera;

public interface HederaPaymentGateway {
    PaymentResult transferHbar(String destination, String amount);
    PaymentResult transferToken(String tokenId, String destination, String amount);
    record PaymentResult(String transactionId, String status, boolean mock) {}
}
