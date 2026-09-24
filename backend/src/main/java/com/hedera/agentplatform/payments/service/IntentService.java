package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding.Resolution;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.entity.ContactEntity;
import com.hedera.agentplatform.payments.hedera.HederaPaymentGateway;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Turns a {@link PaymentIntent} into a payment request, one field at a time, saying where each
 * value came from. It does not guess: a name that is not a contact, or a symbol the paying account
 * does not hold, is reported as not understood rather than matched approximately.
 */
@Service
public class IntentService {

  private final ContactService contacts;
  private final HederaPaymentGateway gateway;
  private final PaymentMirrorClient mirror;

  public IntentService(
      ContactService contacts, HederaPaymentGateway gateway, PaymentMirrorClient mirror) {
    this.contacts = contacts;
    this.gateway = gateway;
    this.mirror = mirror;
  }

  public IntentUnderstanding understand(PaymentIntent intent) {
    List<Resolution> steps = new ArrayList<>();
    List<String> problems = new ArrayList<>();

    // Recipient: an account id as is, otherwise a contact name.
    String recipient = trim(intent.recipient());
    String destination = null;
    String recipientName = null;
    if (recipient == null) {
      problems.add("No recipient");
    } else if (recipient.matches(CreatePaymentRequest.ACCOUNT_ID)) {
      destination = recipient;
      steps.add(new Resolution("Recipient", recipient, recipient, "account id as given"));
    } else {
      Optional<ContactEntity> contact = contacts.byName(recipient);
      if (contact.isPresent()) {
        destination = contact.get().accountId;
        recipientName = contact.get().name;
        steps.add(new Resolution("Recipient", recipient, destination, "your contacts"));
      } else {
        steps.add(new Resolution("Recipient", recipient, null, "your contacts"));
        problems.add("\"" + recipient + "\" is not one of your contacts");
      }
    }

    // Amount: validated here, converted to units only when the payment is created.
    String amount = trim(intent.amount());
    if (amount == null || !amount.matches(CreatePaymentRequest.DECIMAL) || isZero(amount)) {
      problems.add("The amount must be a positive number");
    } else {
      steps.add(new Resolution("Amount", amount, amount, "as given"));
    }

    // Asset: HBAR, a token id, or the symbol of a token the paying account holds.
    String asset = trim(intent.asset());
    String tokenId = null;
    String symbol = null;
    if (asset == null || asset.equalsIgnoreCase("HBAR") || asset.equals("ℏ")) {
      symbol = "HBAR";
      steps.add(new Resolution("Asset", asset == null ? "(none)" : asset, "HBAR", "default asset"));
    } else if (asset.matches(CreatePaymentRequest.ACCOUNT_ID)) {
      tokenId = asset;
      symbol = asset;
      steps.add(new Resolution("Asset", asset, asset, "token id as given"));
    } else {
      Optional<TokenHolding> held = heldBySymbol(asset);
      if (held.isPresent()) {
        tokenId = held.get().tokenId();
        symbol = held.get().symbol();
        steps.add(new Resolution("Asset", asset, tokenId, "tokens held by the paying account (Mirror Node)"));
      } else {
        steps.add(new Resolution("Asset", asset, null, "tokens held by the paying account (Mirror Node)"));
        problems.add("The paying account holds no token with symbol " + asset);
      }
    }

    String envelope = trim(intent.envelope());
    if (envelope != null) {
      if (envelope.matches("(?i)RENT|ESSENTIALS|EMERGENCY")) {
        envelope = envelope.toUpperCase(Locale.ROOT);
        steps.add(new Resolution("Envelope", intent.envelope(), envelope, "as given"));
      } else {
        problems.add("The envelope must be rent, essentials or emergency");
      }
    }

    String keep = trim(intent.keepAtLeast());
    if (keep != null) {
      if (keep.matches(CreatePaymentRequest.DECIMAL)) {
        steps.add(new Resolution("Your condition", keep, "keep at least " + keep + " " + symbol,
            "as given, checked against the real balance before sending"));
      } else {
        problems.add("\"Keep at least\" must be a number");
      }
    }

    if (!problems.isEmpty()) {
      return new IntentUnderstanding(false, null, recipientName, symbol, steps, problems);
    }
    return new IntentUnderstanding(
        true,
        new CreatePaymentRequest(destination, amount, tokenId, envelope, trim(intent.memo()), keep),
        recipientName,
        symbol,
        steps,
        List.of());
  }

  private Optional<TokenHolding> heldBySymbol(String symbol) {
    String payer = gateway.payerAccount();
    if (payer == null) {
      return Optional.empty();
    }
    BalanceLookup balances = mirror.findBalances(payer);
    if (balances.state() == LookupState.UNAVAILABLE) {
      throw new MirrorNodeUnavailableException("Mirror Node could not be reached to resolve " + symbol);
    }
    if (balances.state() != LookupState.FOUND) {
      return Optional.empty();
    }
    return balances.balances().tokens().stream()
        .filter(t -> symbol.equalsIgnoreCase(t.symbol()))
        .findFirst();
  }

  private static boolean isZero(String amount) {
    return new java.math.BigDecimal(amount).signum() == 0;
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
