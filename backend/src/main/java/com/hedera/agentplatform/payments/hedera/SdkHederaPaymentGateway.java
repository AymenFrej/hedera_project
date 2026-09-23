package com.hedera.agentplatform.payments.hedera;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real transfers through the Hedera SDK. Which account pays, and who signs, is delegated to the
 * {@link PaymentSigner}.
 */
public class SdkHederaPaymentGateway implements HederaPaymentGateway {

  private static final Logger log = LoggerFactory.getLogger(SdkHederaPaymentGateway.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  private final Client client;
  private final PaymentSigner signer;

  public SdkHederaPaymentGateway(Client client, PaymentSigner signer) {
    this.client = client;
    this.signer = signer;
  }

  @Override
  public PaymentResult transferHbar(String destination, long tinybars, String memo) {
    AccountId source = signer.payer(client);
    TransferTransaction transaction =
        new TransferTransaction()
            .addHbarTransfer(source, Hbar.fromTinybars(-tinybars))
            .addHbarTransfer(AccountId.fromString(destination), Hbar.fromTinybars(tinybars));
    return submit(transaction, source, memo);
  }

  @Override
  public PaymentResult transferToken(String tokenId, String destination, long units, String memo) {
    AccountId source = signer.payer(client);
    TokenId token = TokenId.fromString(tokenId);
    TransferTransaction transaction =
        new TransferTransaction()
            .addTokenTransfer(token, source, -units)
            .addTokenTransfer(token, AccountId.fromString(destination), units);
    return submit(transaction, source, memo);
  }

  @Override
  public boolean isLive() {
    return true;
  }

  private PaymentResult submit(TransferTransaction transaction, AccountId source, String memo) {
    if (memo != null && !memo.isBlank()) {
      transaction.setTransactionMemo(memo);
    }
    String transactionId = null;
    try {
      TransactionResponse response = signer.prepare(transaction, client).execute(client, TIMEOUT);
      transactionId = response.transactionId.toString();
      // Throws ReceiptStatusException when the network refuses the transfer, e.g.
      // INSUFFICIENT_ACCOUNT_BALANCE or TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.
      var receipt = response.getReceipt(client, TIMEOUT);
      return new PaymentResult(
          true, transactionId, receipt.status.toString(), source.toString(), false);
    } catch (ReceiptStatusException e) {
      log.warn("Transfer {} refused by the network: {}", transactionId, e.receipt.status);
      return new PaymentResult(
          false, transactionId, e.receipt.status.toString(), source.toString(), false);
    } catch (Exception e) {
      // Precheck failures (e.g. INVALID_ACCOUNT_ID) and timeouts end up here.
      log.warn("Transfer {} could not be submitted", transactionId, e);
      String status = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return new PaymentResult(false, transactionId, status, source.toString(), false);
    }
  }
}
