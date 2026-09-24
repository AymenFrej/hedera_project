package com.hedera.agentplatform.tokens.controller;

import com.hedera.agentplatform.tokens.dto.TokenDraft;
import com.hedera.agentplatform.tokens.dto.TokenViews.MintPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Operation;
import com.hedera.agentplatform.tokens.dto.TokenViews.Passport;
import com.hedera.agentplatform.tokens.dto.TokenViews.Portfolio;
import com.hedera.agentplatform.tokens.dto.TokenViews.Receivability;
import com.hedera.agentplatform.tokens.dto.TokenViews.TokenPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Verification;
import com.hedera.agentplatform.tokens.language.TokenSentenceService;
import com.hedera.agentplatform.tokens.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tokens: design and create a token, mint more, and read what the ledger says about any token.
 * Creating and minting take an {@code Idempotency-Key} header: sending the same request twice
 * returns the first operation instead of creating two tokens.
 */
@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController {

  private final TokenService tokens;
  private final TokenSentenceService sentences;
  private final TokenAccess access;

  public TokenController(TokenService tokens, TokenSentenceService sentences, TokenAccess access) {
    this.tokens = tokens;
    this.sentences = sentences;
    this.access = access;
  }

  /** Tokens held by the treasury, read from the Mirror Node. */
  @GetMapping
  public Portfolio portfolio() {
    return tokens.portfolio();
  }

  @PostMapping("/preview")
  public TokenPreview preview(@RequestBody TokenDraft draft) {
    return tokens.preview(draft);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Operation create(
      @RequestBody TokenDraft draft,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return tokens.create(draft, idempotencyKey);
  }

  /** The Token Agent reads a sentence into the Studio's fields. Nothing is created. */
  @PostMapping("/intent/interpret")
  public TokenSentenceService.Interpretation interpret(@RequestBody Map<String, String> body) {
    return sentences.interpret(body.get("text"));
  }

  @GetMapping("/operations")
  public List<Operation> operations(HttpServletRequest http) {
    return tokens.recent(access.scope(http));
  }

  @GetMapping("/operations/{id}")
  public Operation operation(@PathVariable String id, HttpServletRequest http) {
    Operation op = tokens.get(id);
    access.requireVisible(http, op);
    return op;
  }

  @GetMapping("/operations/{id}/verification")
  public Verification verify(@PathVariable String id, HttpServletRequest http) {
    access.requireVisible(http, tokens.get(id));
    return tokens.verify(id);
  }

  @GetMapping("/{tokenId}/passport")
  public Passport passport(@PathVariable String tokenId, HttpServletRequest http) {
    String scope = access.scope(http);
    List<Operation> visible = tokens.forToken(tokenId).stream()
        .filter(op -> scope == null || scope.equals(op.requestedById()))
        .toList();
    return tokens.passport(tokenId, visible);
  }

  @PostMapping("/{tokenId}/mint/preview")
  public MintPreview previewMint(@PathVariable String tokenId, @RequestBody Map<String, String> body) {
    return tokens.previewMint(tokenId, body.get("amount"));
  }

  @PostMapping("/{tokenId}/mint")
  @ResponseStatus(HttpStatus.CREATED)
  public Operation mint(
      @PathVariable String tokenId,
      @RequestBody Map<String, String> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return tokens.mint(tokenId, body.get("amount"), idempotencyKey);
  }

  @GetMapping("/{tokenId}/receivable/{accountId}")
  public Receivability receivable(@PathVariable String tokenId, @PathVariable String accountId) {
    return tokens.receivability(tokenId, accountId);
  }
}
