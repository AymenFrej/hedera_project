package com.hedera.agentplatform.tokens.hedera;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real token operations through the Hedera SDK. The platform operator is the treasury and pays the
 * fees. Tokens are created without an admin, freeze, wipe, KYC, pause or fee-schedule key: nobody,
 * this platform included, can later change or delete them, freeze or wipe a holder, or add fees.
 * The only key that may be kept is the supply key, and only when the person asks for a mintable
 * token.
 */
public class SdkHederaTokenGateway implements HederaTokenGateway {

  private static final Logger log = LoggerFactory.getLogger(SdkHederaTokenGateway.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  // Creating a token costs about 1 USD in HBAR, above the client's default 2 HBAR fee ceiling.
  private static final Hbar CREATE_MAX_FEE = new Hbar(50);

  private final Client client;

  public SdkHederaTokenGateway(Client client) {
    this.client = client;
  }

  @Override
  public boolean isLive() {
    return true;
  }

  @Override
  public String treasuryAccount() {
    return client.getOperatorAccountId().toString();
  }

  @Override
  public String supplyPublicKey() {
    return client.getOperatorPublicKey().toStringRaw();
  }

  @Override
  public String newTransactionId() {
    return TransactionId.generate(client.getOperatorAccountId()).toString();
  }

  @Override
  public TokenResult create(String transactionId, TokenPlan plan) {
    AccountId treasury = client.getOperatorAccountId();
    TokenCreateTransaction transaction =
        new TokenCreateTransaction()
            .setTokenName(plan.name())
            .setTokenSymbol(plan.symbol())
            .setDecimals(plan.decimals())
            .setInitialSupply(plan.initialSupplyUnits())
            .setTreasuryAccountId(treasury)
            .setAutoRenewAccountId(treasury)
            .setTokenType(TokenType.FUNGIBLE_COMMON)
            .setSupplyType(
                plan.maxSupplyUnits() == null ? TokenSupplyType.INFINITE : TokenSupplyType.FINITE);
    if (plan.maxSupplyUnits() != null) {
      transaction.setMaxSupply(plan.maxSupplyUnits());
    }
    if (plan.mintable()) {
      transaction.setSupplyKey(client.getOperatorPublicKey());
    }
    if (plan.memo() != null && !plan.memo().isBlank()) {
      transaction.setTokenMemo(plan.memo());
    }
    transaction.setMaxTransactionFee(CREATE_MAX_FEE);
    return submit(transaction, transactionId, null);
  }

  @Override
  public TokenResult mint(String transactionId, String tokenId, long units) {
    TokenMintTransaction transaction =
        new TokenMintTransaction().setTokenId(TokenId.fromString(tokenId)).setAmount(units);
    return submit(transaction, transactionId, tokenId);
  }

  private TokenResult submit(Transaction<?> transaction, String transactionId, String tokenId) {
    transaction.setTransactionId(TransactionId.fromString(transactionId));
    try {
      TransactionReceipt receipt = transaction.execute(client, TIMEOUT).getReceipt(client, TIMEOUT);
      String resultToken = receipt.tokenId != null ? receipt.tokenId.toString() : tokenId;
      Long supply = tokenId != null ? receipt.totalSupply : null;
      return new TokenResult(Outcome.SUCCESS, transactionId, receipt.status.toString(), resultToken, supply);
    } catch (ReceiptStatusException e) {
      log.warn("Token operation {} refused by the network: {}", transactionId, e.receipt.status);
      return new TokenResult(Outcome.FAILED, transactionId, e.receipt.status.toString(), tokenId, null);
    } catch (PrecheckStatusException e) {
      // Refused by the node before consensus: nothing was executed.
      log.warn("Token operation {} refused at precheck: {}", transactionId, e.status);
      return new TokenResult(Outcome.FAILED, transactionId, e.status.toString(), tokenId, null);
    } catch (Exception e) {
      // Timeout or connection loss: it may or may not have reached consensus.
      log.warn("Token operation {} has no receipt, outcome unknown", transactionId, e);
      String status = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return new TokenResult(Outcome.UNKNOWN, transactionId, status, tokenId, null);
    }
  }
}
