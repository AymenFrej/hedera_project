package com.hedera.agentplatform.accounts.service;

import com.hedera.agentplatform.shared.config.HederaProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Encrypts generated account keys at rest; the raw key is never returned by the API. */
@Service
public class AccountKeyProtector {
    private final SecretKeySpec key;
    public AccountKeyProtector(HederaProperties properties) {
        String configured = properties.getAccountKeyEncryptionSecret();
        String source = configured == null || configured.isBlank() ? properties.getOperatorPrivateKey() : configured;
        if (source == null || source.isBlank()) source = "local-development-only-account-key-secret";
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String encrypt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8)); byte[] combined = new byte[iv.length + encrypted.length]; System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length); return Base64.getEncoder().encodeToString(combined); }
        catch (Exception e) { throw new IllegalStateException("Unable to protect account key", e); }
    }
}
