package com.hedera.agentplatform.accounts.hedera;

/** Safe client-facing failure; never includes a signing key or SDK response. */
public class WalletProvisioningException extends RuntimeException {
    public WalletProvisioningException(String message) { super(message); }
    public WalletProvisioningException(String message, Throwable cause) { super(message, cause); }
}
