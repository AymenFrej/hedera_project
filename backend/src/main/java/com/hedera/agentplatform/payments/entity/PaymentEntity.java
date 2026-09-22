package com.hedera.agentplatform.payments.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "payments")
public class PaymentEntity {
    @Id public String id;
    public BigDecimal amount;
    public String currency;
    public String destination;
    public String status;
}
