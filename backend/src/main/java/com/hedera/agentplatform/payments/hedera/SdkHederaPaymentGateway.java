package com.hedera.agentplatform.payments.hedera;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionId;
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

  /** Generated for the payer, so the payer is also the account charged the network fee. */
  @Override
  public String newTransactionId() {
    return TransactionId.generate(signer.payer(client)).toString();
  }

  @Override
  public PaymentResult transferHbar(
      String transactionId, String destination, long tinybars, String memo) {
    AccountId source = signer.payer(client);
    TransferTransaction transaction =
        new TransferTransaction()
            .addHbarTransfer(source, Hbar.fromTinybars(-tinybars))
            .addHbarTransfer(AccountId.fromString(destination), Hbar.fromTinybars(tinybars));
    return submit(transaction, transactionId, source, memo);
  }

  @Override
  public PaymentResult transferToken(
      String transactionId, String tokenId, String destination, long units, String memo) {
    AccountId source = signer.payer(client);
    TokenId token = TokenId.fromString(tokenId);
    TransferTransaction transaction =
        new TransferTransaction()
            .addTokenTransfer(token, source, -units)
            .addTokenTransfer(token, AccountId.fromString(destination), units);
    return submit(transaction, transactionId, source, memo);
  }

  @Override
  public boolean isLive() {
    return true;
  }

  private PaymentResult submit(
      TransferTransaction transaction, String transactionId, AccountId source, String memo) {
    transaction.setTransactionId(TransactionId.fromString(transactionId));
    if (memo != null && !memo.isBlank()) {
      transaction.setTransactionMemo(memo);
    }
    try {
      TransactionResponse response = signer.prepare(transaction, client).execute(client, TIMEOUT);
      // Throws ReceiptStatusException when the network refuses the transfer, e.g.
      // INSUFFICIENT_ACCOUNT_BALANCE or TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.
      var receipt = response.getReceipt(client, TIMEOUT);
      return new PaymentResult(
          Outcome.SUCCESS, transactionId, receipt.status.toString(), source.toString(), false);
    } catch (ReceiptStatusException e) {
      log.warn("Transfer {} refused by the network: {}", transactionId, e.receipt.status);
      return new PaymentResult(
          Outcome.FAILED, transactionId, e.receipt.status.toString(), source.toString(), false);
    } catch (PrecheckStatusException e) {
      // Refused by the node before consensus (e.g. INVALID_ACCOUNT_ID): nothing was executed.
      log.warn("Transfer {} refused at precheck: {}", transactionId, e.status);
      return new PaymentResult(
          Outcome.FAILED, transactionId, e.status.toString(), source.toString(), false);
    } catch (Exception e) {
      // Timeout or connection loss: the transfer may or may not have reached consensus.
      log.warn("Transfer {} has no receipt, outcome unknown", transactionId, e);
      String status = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return new PaymentResult(
          Outcome.UNKNOWN, transactionId, status, source.toString(), false);
    }
  }
}
