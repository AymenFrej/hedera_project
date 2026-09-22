package com.hedera.agentplatform.payments.dto;

public record PaymentResponse(String id, String amount, String currency, String destination, String status) {}
