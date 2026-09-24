package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.PaymentPreview;
import com.hedera.agentplatform.payments.dto.PaymentPreview.Check;
import com.hedera.agentplatform.payments.entity.PaymentEntity;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountBalances;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenLookup;
import com.hedera.agentplatform.payments.policy.PaymentPolicy;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.PaymentPolicyDecision;
import com.hedera.agentplatform.payments.policy.PaymentPolicy.Verdict;
import com.hedera.agentplatform.shared.config.HederaProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Tells what executing a payment would do, without doing anything: nothing is saved, sent or
 * audited.
 *
 * <p>Two kinds of information, kept apart: the policy's verdict, passed through exactly as the
 * policy returned it, and facts read from the ledger (recipient exists, token association,
 * balance). No rule is invented here. The preview never authorizes execution: executing goes
 * through {@link PaymentService#create}, which asks the policy again.
 */
@Service
public class PaymentPreviewService {

  static final String PASS = "PASS";
  static final String WARN = "WARN";
  static final String FAIL = "FAIL";
  static final String UNKNOWN = "UNKNOWN";
  private static final int HBAR_DECIMALS = 8;
  private static final String NOTE =
      "Preview only: nothing was recorded or sent. Execute checks the policy again.";

  private final PaymentService payments;
  private final PaymentPolicy policy;
  private final HederaPaymentGateway gateway;
  private final PaymentMirrorClient mirror;
  private final HederaProperties properties;
  private final TokenDecimals decimals;

  public PaymentPreviewService(
      PaymentService payments,
      PaymentPolicy policy,
      HederaPaymentGateway gateway,
      PaymentMirrorClient mirror,
      HederaProperties properties,
      TokenDecimals decimals) {
    this.decimals = decimals;
    this.payments = payments;
    this.policy = policy;
    this.gateway = gateway;
    this.mirror = mirror;
    this.properties = properties;
  }

  public PaymentPreview preview(CreatePaymentRequest request) {
    PaymentEntity draft = payments.draft(request);
    PaymentPolicyDecision decision = policy.evaluate(draft);
    int places = decimals.of(draft.tokenId);
    Long left = decision.envelopeBalance();
    PaymentPreview.Policy verdict =
        new PaymentPreview.Policy(
            decision.verdict().name(),
            decision.ruleId(),
            decision.reason(),
            left == null ? null : plain(BigDecimal.valueOf(left, places)),
            left != null && draft.amountUnits > left
                ? plain(BigDecimal.valueOf(draft.amountUnits - left, places))
                : null);
    boolean hbar = draft.tokenId == null;
    String payer = gateway.payerAccount();

    if (payer == null) {
      return new PaymentPreview(
          decision.verdict() == Verdict.DENY ? "BLOCKED" : "SIMULATION",
          decision.verdict() == Verdict.DENY
              ? "Blocked by policy: no Hedera transaction would be created"
              : "Simulation mode: policy and audit would run, nothing would be transferred",
          null,
          draft.destination,
          plain(draft.amount),
          draft.amountUnits,
          draft.tokenId,
          hbar ? "HBAR" : null,
          null,
          null,
          verdict,
          List.of(new Check("Ledger", UNKNOWN, "No Hedera account configured: nothing to check")),
          NOTE);
    }

    List<Check> checks = new ArrayList<>();
    String symbol = hbar ? "HBAR" : draft.tokenId;
    int digits = hbar ? HBAR_DECIMALS : 0;

    checks.add(recipientCheck(draft.destination, payer));

    boolean tokenKnown = hbar;
    if (!hbar) {
      TokenLookup token = mirror.findToken(draft.tokenId);
      switch (token.state()) {
        case FOUND -> {
          tokenKnown = true;
          symbol = token.token().symbol() != null ? token.token().symbol() : draft.tokenId;
          digits = token.token().decimals();
          checks.add(new Check("Token", PASS,
              symbol + " (" + draft.tokenId + "), " + digits + " decimals"));
        }
        case NOT_FOUND -> checks.add(new Check("Token", FAIL,
            "No token " + draft.tokenId + " on " + properties.getNetwork()
                + ": Hedera would refuse with INVALID_TOKEN_ID"));
        case UNAVAILABLE -> checks.add(new Check("Token", UNKNOWN, "Mirror Node could not be reached"));
      }
      if (tokenKnown) {
        checks.add(associationCheck(draft.destination, draft.tokenId, symbol));
      }
    }

    String before = null;
    String after = null;
    BalanceLookup balances = mirror.findBalances(payer);
    if (balances.state() == LookupState.FOUND) {
      Long held = held(balances.balances(), draft.tokenId);
      if (held == null) {
        checks.add(new Check("Balance", FAIL,
            payer + " does not hold " + symbol + ": Hedera would refuse the transfer"));
      } else {
        before = plain(BigDecimal.valueOf(held, digits));
        after = plain(BigDecimal.valueOf(held - draft.amountUnits, digits));
        if (held < draft.amountUnits) {
          after = null;
          checks.add(new Check("Balance", FAIL,
              "Only " + before + " " + symbol + " available: Hedera would refuse with "
                  + (hbar ? "INSUFFICIENT_ACCOUNT_BALANCE" : "INSUFFICIENT_TOKEN_BALANCE")));
        } else {
          checks.add(new Check("Balance", PASS,
              before + " " + symbol + " available"
                  + (hbar ? ", the network fee comes on top" : "")));
        }
      }
    } else {
      checks.add(new Check("Balance", UNKNOWN, "Mirror Node could not be reached"));
    }

    String outcome;
    String summary;
    boolean refused = checks.stream().anyMatch(c -> FAIL.equals(c.status()));
    if (decision.verdict() == Verdict.DENY) {
      outcome = "BLOCKED";
      summary = "Blocked by policy: no Hedera transaction would be created";
    } else if (refused) {
      outcome = "LIKELY_TO_FAIL";
      summary = "Hedera would refuse this transfer, and the network fee would still be charged";
    } else if (decision.verdict() == Verdict.HOLD) {
      outcome = "NEEDS_APPROVAL";
      summary = "The policy holds this payment for human approval before anything is sent";
    } else {
      outcome = "READY";
      summary = "Policy allows it and the ledger checks pass";
    }

    return new PaymentPreview(
        outcome,
        summary,
        payer,
        draft.destination,
        plain(draft.amount),
        draft.amountUnits,
        draft.tokenId,
        symbol,
        before,
        after,
        verdict,
        checks,
        NOTE);
  }

  private Check recipientCheck(String destination, String payer) {
    if (destination.equals(payer)) {
      return new Check("Recipient", WARN, "Recipient is the paying account itself");
    }
    AccountLookup account = mirror.findAccount(destination);
    return switch (account.state()) {
      case FOUND ->
          account.account().deleted()
              ? new Check("Recipient", FAIL, destination + " has been deleted")
              : new Check("Recipient", PASS, destination + " exists on " + properties.getNetwork());
      case NOT_FOUND ->
          new Check("Recipient", FAIL,
              "No account " + destination + " on " + properties.getNetwork()
                  + ": Hedera would refuse with INVALID_ACCOUNT_ID");
      case UNAVAILABLE -> new Check("Recipient", UNKNOWN, "Mirror Node could not be reached");
    };
  }

  /**
   * An unassociated recipient is not necessarily a failure: an account that accepts automatic
   * associations is associated by the transfer itself.
   */
  private Check associationCheck(String destination, String tokenId, String symbol) {
    LookupState associated = mirror.findAssociation(destination, tokenId);
    if (associated == LookupState.FOUND) {
      return new Check("Recipient can receive " + symbol, PASS, "Associated with " + tokenId);
    }
    if (associated == LookupState.UNAVAILABLE) {
      return new Check("Recipient can receive " + symbol, UNKNOWN, "Mirror Node could not be reached");
    }
    AccountLookup account = mirror.findAccount(destination);
    if (account.state() != LookupState.FOUND) {
      return new Check("Recipient can receive " + symbol, UNKNOWN, "Recipient account not readable");
    }
    int slots = account.account().maxAutomaticTokenAssociations();
    if (slots == -1) {
      return new Check("Recipient can receive " + symbol, PASS,
          "Not associated yet, but accepts automatic associations: the transfer associates it");
    }
    if (slots > 0) {
      return new Check("Recipient can receive " + symbol, WARN,
          "Not associated; succeeds only if one of its " + slots
              + " automatic association slots is still free");
    }
    return new Check("Recipient can receive " + symbol, FAIL,
        "Not associated with " + tokenId
            + ": Hedera would refuse with TOKEN_NOT_ASSOCIATED_TO_ACCOUNT");
  }

  /** Smallest units held of the asset; null when a token is not held at all. */
  private static Long held(AccountBalances balances, String tokenId) {
    if (tokenId == null) {
      return balances.tinybars();
    }
    return balances.tokens().stream()
        .filter(t -> t.tokenId().equals(tokenId))
        .map(TokenHolding::balance)
        .findFirst()
        .orElse(null);
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
