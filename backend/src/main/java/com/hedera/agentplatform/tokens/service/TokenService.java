package com.hedera.agentplatform.tokens.service;

import com.hedera.agentplatform.audit.service.AuditService;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.AccountLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.BalanceLookup;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.LookupState;
import com.hedera.agentplatform.payments.mirror.PaymentMirrorClient.TokenHolding;
import com.hedera.agentplatform.shared.config.HederaProperties;
import com.hedera.agentplatform.shared.model.Actor;
import com.hedera.agentplatform.shared.security.ActorResolver;
import com.hedera.agentplatform.tokens.dto.TokenDraft;
import com.hedera.agentplatform.tokens.dto.TokenViews.Check;
import com.hedera.agentplatform.tokens.dto.TokenViews.HolderView;
import com.hedera.agentplatform.tokens.dto.TokenViews.MintPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Operation;
import com.hedera.agentplatform.tokens.dto.TokenViews.Passport;
import com.hedera.agentplatform.tokens.dto.TokenViews.Portfolio;
import com.hedera.agentplatform.tokens.dto.TokenViews.Receivability;
import com.hedera.agentplatform.tokens.dto.TokenViews.TokenCard;
import com.hedera.agentplatform.tokens.dto.TokenViews.TokenPreview;
import com.hedera.agentplatform.tokens.dto.TokenViews.Verification;
import com.hedera.agentplatform.tokens.entity.TokenOperationEntity;
import com.hedera.agentplatform.tokens.entity.TokenOperationEntity.Kind;
import com.hedera.agentplatform.tokens.entity.TokenOperationEntity.Status;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.Outcome;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.TokenPlan;
import com.hedera.agentplatform.tokens.hedera.HederaTokenGateway.TokenResult;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.FactsLookup;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.Holder;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.HoldersLookup;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenFacts;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TokenKeys;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TransactionFacts;
import com.hedera.agentplatform.tokens.mirror.TokenMirrorClient.TransactionLookup;
import com.hedera.agentplatform.tokens.repository.TokenOperationRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The Tokens capability: design a token, create it, mint more, and read back what the ledger says.
 *
 * <p>Every operation follows the same path as a payment: validated and previewed first, committed
 * with its transaction id before anything is sent (so a crash is recoverable from the ledger),
 * sent once per idempotency key, audited on HCS, and verified against the Mirror Node. Nothing
 * shown about a token is written by hand: supply, holders, keys and fees are read from the ledger.
 */
@Service
public class TokenService {

  public static final String AGENT = "TokenAgent";
  static final String ACCOUNT_ID = "^\\d+\\.\\d+\\.\\d+$";
  static final String KEY = "^[A-Za-z0-9_:-]{8,64}$";
  static final int MAX_DECIMALS = 8;

  private final TokenOperationRepository operations;
  private final HederaTokenGateway gateway;
  private final TokenMirrorClient mirror;
  private final PaymentMirrorClient accounts;
  private final AuditService audit;
  private final ActorResolver actors;
  private final HederaProperties properties;

  public TokenService(
      TokenOperationRepository operations,
      HederaTokenGateway gateway,
      TokenMirrorClient mirror,
      PaymentMirrorClient accounts,
      AuditService audit,
      ActorResolver actors,
      HederaProperties properties) {
    this.operations = operations;
    this.gateway = gateway;
    this.mirror = mirror;
    this.accounts = accounts;
    this.audit = audit;
    this.actors = actors;
    this.properties = properties;
  }

  // --- Studio -------------------------------------------------------------------------------------

  /** Validates a design and says, before anything is sent, what the token will and will not allow. */
  public TokenPreview preview(TokenDraft draft) {
    Validated v = validate(draft);
    List<String> warnings = new ArrayList<>();
    if (v.plan != null && gateway.isLive()) {
      // Symbols are not unique on Hedera: say so rather than let two "BEAN" tokens be confused.
      BalanceLookup held = accounts.findBalances(gateway.treasuryAccount());
      if (held.state() == LookupState.FOUND) {
        held.balances().tokens().stream()
            .filter(t -> v.plan.symbol().equalsIgnoreCase(t.symbol()))
            .findFirst()
            .ifPresent(t -> warnings.add("The treasury already holds a token with symbol " + t.symbol()
                + " (" + t.tokenId() + "). Symbols are not unique on Hedera: holders will tell them"
                + " apart by id only."));
      }
    }
    if (v.plan == null) {
      return new TokenPreview(false, v.problems, warnings, clean(draft.name()), upper(draft.symbol()),
          0, null, null, null, false, null, gateway.treasuryAccount(), gateway.isLive(), List.of(), List.of());
    }
    TokenPlan plan = v.plan;
    String initial = whole(plan.initialSupplyUnits(), plan.decimals());
    String max = plan.maxSupplyUnits() == null ? null : whole(plan.maxSupplyUnits(), plan.decimals());
    String supplyType = plan.maxSupplyUnits() == null ? "INFINITE" : "FINITE";
    TokenKeys keys = new TokenKeys(null, plan.mintable() ? "planned" : null, null, null, null, null, null);
    List<String> steps = new ArrayList<>();
    String treasury = gateway.isLive() ? gateway.treasuryAccount() : "the treasury (simulated)";
    steps.add("Hedera creates " + plan.name() + " (" + plan.symbol() + ") with " + treasury + " as treasury.");
    steps.add("All " + initial + " " + plan.symbol() + " land in the treasury; from there they move with Payments.");
    steps.add("The network fee is paid in HBAR by the treasury, then read back from the Mirror Node.");
    steps.add("The creation is written to the audit trail on HCS.");
    return new TokenPreview(true, List.of(), warnings, plan.name(), plan.symbol(), plan.decimals(), initial,
        max, supplyType, plan.mintable(), plan.memo(), gateway.treasuryAccount(), gateway.isLive(),
        TokenPromises.of(keys, supplyType, initial, max, plan.symbol(), 0, null, null, true), steps);
  }

  public Operation create(TokenDraft draft, String idempotencyKey) {
    Validated v = validate(draft);
    if (v.plan == null) {
      throw new IllegalArgumentException(String.join("; ", v.problems));
    }
    Actor requester = actors.currentActor();
    String key = scopedKey(idempotencyKey, requester);
    Optional<TokenOperationEntity> earlier = key == null ? Optional.empty() : operations.findByIdempotencyKey(key);
    if (earlier.isPresent()) {
      return view(earlier.get());
    }

    TokenPlan plan = v.plan;
    TokenOperationEntity op = newOperation(Kind.CREATE, requester, key);
    op.name = plan.name();
    op.symbol = plan.symbol();
    op.decimals = plan.decimals();
    op.amountUnits = plan.initialSupplyUnits();
    op.maxSupplyUnits = plan.maxSupplyUnits();
    op.mintable = plan.mintable();
    op.memo = plan.memo();
    op.treasury = gateway.treasuryAccount();
    op.transactionId = gateway.newTransactionId();
    try {
      op = operations.saveAndFlush(op);
    } catch (DataIntegrityViolationException e) {
      // The same key arrived twice at once: the other request is the one that creates the token.
      return operations.findByIdempotencyKey(key).map(this::view).orElseThrow(() -> e);
    }

    TokenResult result = gateway.create(op.transactionId, plan);
    apply(op, result);
    if (result.outcome() == Outcome.SUCCESS) {
      op.tokenId = result.tokenId();
      op.totalSupplyAfter = plan.initialSupplyUnits();
    }
    op = save(op);
    record("TOKEN_CREATE", op);
    return view(op);
  }

  // --- Mint ---------------------------------------------------------------------------------------

  public MintPreview previewMint(String tokenId, String amount) {
    requireTokenId(tokenId);
    TokenFacts facts = factsOrThrow(tokenId);
    List<String> problems = new ArrayList<>();
    String cannot = mintBlocker(facts);
    if (cannot != null) {
      problems.add(cannot);
    }
    Long units = null;
    try {
      units = toUnits(amount, facts.decimals(), "The amount");
      if (units <= 0) {
        problems.add("The amount must be greater than zero");
      }
    } catch (IllegalArgumentException e) {
      problems.add(e.getMessage());
    }
    long before = facts.totalSupply() == null ? 0 : facts.totalSupply();
    Long after = units == null ? null : before + units;
    if (after != null && isCapped(facts) && after > facts.maxSupply()) {
      problems.add("That would make " + whole(after, facts.decimals()) + " " + facts.symbol()
          + ", above its cap of " + whole(facts.maxSupply(), facts.decimals()));
    }
    return new MintPreview(problems.isEmpty(), problems, tokenId, facts.symbol(), facts.decimals(),
        units == null ? amount : whole(units, facts.decimals()), whole(before, facts.decimals()),
        after == null ? null : whole(after, facts.decimals()),
        isCapped(facts) ? whole(facts.maxSupply(), facts.decimals()) : null, gateway.isLive());
  }

  public Operation mint(String tokenId, String amount, String idempotencyKey) {
    MintPreview preview = previewMint(tokenId, amount);
    if (!preview.valid()) {
      throw new IllegalArgumentException(String.join("; ", preview.problems()));
    }
    Actor requester = actors.currentActor();
    String key = scopedKey(idempotencyKey, requester);
    Optional<TokenOperationEntity> earlier = key == null ? Optional.empty() : operations.findByIdempotencyKey(key);
    if (earlier.isPresent()) {
      return view(earlier.get());
    }
    long units = toUnits(amount, preview.decimals(), "The amount");
    TokenOperationEntity op = newOperation(Kind.MINT, requester, key);
    op.tokenId = tokenId;
    op.symbol = preview.symbol();
    op.decimals = preview.decimals();
    op.amountUnits = units;
    op.treasury = gateway.treasuryAccount();
    op.transactionId = gateway.newTransactionId();
    try {
      op = operations.saveAndFlush(op);
    } catch (DataIntegrityViolationException e) {
      return operations.findByIdempotencyKey(key).map(this::view).orElseThrow(() -> e);
    }

    TokenResult result = gateway.mint(op.transactionId, tokenId, units);
    apply(op, result);
    if (result.outcome() == Outcome.SUCCESS) {
      op.totalSupplyAfter = result.totalSupply();
    }
    op = save(op);
    record("TOKEN_MINT", op);
    return view(op);
  }

  /** Why this platform cannot mint the token; null when it can. Read from the ledger's keys. */
  private String mintBlocker(TokenFacts facts) {
    if (facts.keys().supply() == null) {
      return facts.symbol() + " has no supply key: its supply is fixed forever";
    }
    if (!gateway.isLive()) {
      return "Minting needs Hedera credentials (simulated mode)";
    }
    if (!facts.keys().supply().equalsIgnoreCase(gateway.supplyPublicKey())) {
      return "Only the holder of " + facts.symbol() + "'s supply key can mint it, and that is not this platform";
    }
    if (isCapped(facts) && facts.totalSupply() != null && facts.totalSupply() >= facts.maxSupply()) {
      return facts.symbol() + " has reached its cap of " + whole(facts.maxSupply(), facts.decimals());
    }
    return null;
  }

  // --- What the ledger says -----------------------------------------------------------------------

  public Portfolio portfolio() {
    if (!gateway.isLive()) {
      return new Portfolio(false, null, null, true, List.of());
    }
    String treasury = gateway.treasuryAccount();
    BalanceLookup held = accounts.findBalances(treasury);
    if (held.state() != LookupState.FOUND) {
      return new Portfolio(true, treasury, null, false, List.of());
    }
    Set<String> createdHere =
        operations.findTop50ByOrderByCreatedAtDesc().stream()
            .filter(o -> o.kind == Kind.CREATE && o.tokenId != null)
            .map(o -> o.tokenId)
            .collect(Collectors.toSet());
    List<TokenCard> cards = new ArrayList<>();
    for (TokenHolding t : held.balances().tokens()) {
      cards.add(new TokenCard(t.tokenId(), t.symbol(), t.name(), t.decimals(),
          whole(t.balance(), t.decimals()), createdHere.contains(t.tokenId())));
    }
    return new Portfolio(true, treasury, held.balances().timestamp(), true, cards);
  }

  public Passport passport(String tokenId, List<Operation> visibleOperations) {
    requireTokenId(tokenId);
    TokenFacts f = factsOrThrow(tokenId);
    HoldersLookup holders = mirror.holders(tokenId, 25);
    long total = f.totalSupply() == null ? 0 : f.totalSupply();
    List<HolderView> views = new ArrayList<>();
    double treasuryShare = 0;
    long shown = 0;
    for (Holder h : holders.holders()) {
      double share = total == 0 ? 0 : (double) h.balance() / total;
      boolean isTreasury = h.account().equals(f.treasury());
      if (isTreasury) {
        treasuryShare = share;
      }
      shown += h.balance();
      views.add(new HolderView(h.account(), whole(h.balance(), f.decimals()), share, isTreasury));
    }
    String max = isCapped(f) ? whole(f.maxSupply(), f.decimals()) : null;
    String totalText = whole(total, f.decimals());
    String blocker = mintBlocker(f);
    return new Passport(
        f.tokenId(), f.name(), f.symbol(), f.decimals(), f.type(), totalText, max, f.supplyType(),
        f.treasury(), f.memo(), timestamp(f.createdTimestamp()), f.pauseStatus(),
        TokenPromises.of(f.keys(), f.supplyType(), totalText, max, f.symbol(), f.customFees(),
            f.pauseStatus(), gateway.supplyPublicKey(), false),
        views, holders.state() == LookupState.FOUND && shown >= total, treasuryShare,
        blocker == null, blocker, visibleOperations, tokenUrl(tokenId));
  }

  /** Whether an account can receive the token now, and if not, what it needs. Read from the ledger. */
  public Receivability receivability(String tokenId, String accountId) {
    requireTokenId(tokenId);
    if (accountId == null || !accountId.trim().matches(ACCOUNT_ID)) {
      throw new IllegalArgumentException("Write an account id like 0.0.12345");
    }
    String account = accountId.trim();
    TokenFacts f = factsOrThrow(tokenId);
    if (account.equals(f.treasury())) {
      return new Receivability(account, tokenId, "CAN_RECEIVE", true, account + " is the treasury of " + f.symbol() + ".");
    }
    AccountLookup found = accounts.findAccount(account);
    if (found.state() == LookupState.UNAVAILABLE) {
      throw new TokenMirrorUnavailableException("Mirror Node could not be reached to check " + account);
    }
    if (found.state() == LookupState.NOT_FOUND) {
      return new Receivability(account, tokenId, "NO_ACCOUNT", false, "There is no account " + account + " on " + network() + ".");
    }
    if (found.account().deleted()) {
      return new Receivability(account, tokenId, "DELETED", false, account + " has been deleted.");
    }
    LookupState associated = accounts.findAssociation(account, tokenId);
    if (associated == LookupState.UNAVAILABLE) {
      throw new TokenMirrorUnavailableException("Mirror Node could not be reached to check " + account);
    }
    if (associated == LookupState.FOUND) {
      String frozen = f.freezeDefault() ? " (this token starts frozen for new holders)" : "";
      return new Receivability(account, tokenId, "CAN_RECEIVE", true,
          account + " is associated with " + f.symbol() + " and can receive it now" + frozen + ".");
    }
    int slots = found.account().maxAutomaticTokenAssociations();
    if (slots != 0) {
      return new Receivability(account, tokenId, "AUTO_ASSOCIATES", true,
          account + " is not associated yet, but accepts automatic associations ("
              + (slots < 0 ? "unlimited" : "up to " + slots) + "): the first transfer associates it"
              + " while a slot is free.");
    }
    return new Receivability(account, tokenId, "NEEDS_ASSOCIATION", false,
        account + " must first associate with " + f.symbol() + " (signed by its own key). Until then"
            + " a transfer fails with TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.");
  }

  // --- Operations and verification ----------------------------------------------------------------

  public List<Operation> recent(String requestedById) {
    List<TokenOperationEntity> list = requestedById == null
        ? operations.findTop50ByOrderByCreatedAtDesc()
        : operations.findTop50ByRequestedByIdOrderByCreatedAtDesc(requestedById);
    return list.stream().map(this::view).toList();
  }

  public List<Operation> forToken(String tokenId) {
    return operations.findByTokenIdOrderByCreatedAtDesc(tokenId).stream().map(this::view).toList();
  }

  public Operation get(String id) {
    return operations.findById(id).map(this::view)
        .orElseThrow(() -> new java.util.NoSuchElementException("Unknown token operation: " + id));
  }

  /**
   * Compares the operation with what the Mirror Node recorded, reads the fee really charged, and
   * settles an operation left without a receipt from the ledger's answer.
   */
  public Verification verify(String id) {
    TokenOperationEntity op = operations.findById(id)
        .orElseThrow(() -> new java.util.NoSuchElementException("Unknown token operation: " + id));
    if (op.transactionId == null) {
      return new Verification("NOT_SUBMITTED",
          op.status == Status.SIMULATED ? "Simulated: nothing was sent to Hedera" : "Nothing was sent to Hedera",
          List.of(), null, null, null);
    }
    TransactionLookup lookup = mirror.transaction(op.transactionId);
    String txUrl = transactionUrl(op.transactionId);
    if (lookup.state() == LookupState.UNAVAILABLE) {
      return new Verification("UNAVAILABLE", "Mirror Node could not be reached: try again", List.of(), null, null, txUrl);
    }
    if (lookup.state() == LookupState.NOT_FOUND) {
      return new Verification("PENDING", "The Mirror Node has no record of this transaction yet", List.of(), null, null, txUrl);
    }
    TransactionFacts tx = lookup.transaction();
    settle(op, tx);

    List<Check> checks = new ArrayList<>();
    boolean success = "SUCCESS".equals(tx.result());
    checks.add(new Check("Result", success, "The network answered " + tx.result()));
    if (op.kind == Kind.CREATE && success) {
      checks.add(new Check("Token created", op.tokenId != null && op.tokenId.equals(tx.entityId()),
          "The transaction created " + tx.entityId()));
      FactsLookup facts = mirror.facts(tx.entityId());
      if (facts.state() == LookupState.FOUND) {
        TokenFacts f = facts.facts();
        checks.add(new Check("Name and symbol", op.name.equals(f.name()) && op.symbol.equals(f.symbol()),
            "On the ledger: " + f.name() + " (" + f.symbol() + ")"));
        checks.add(new Check("Decimals", op.decimals == f.decimals(), "On the ledger: " + f.decimals()));
        checks.add(new Check("Initial supply", op.amountUnits.equals(f.initialSupply()),
            "On the ledger: " + whole(f.initialSupply() == null ? 0 : f.initialSupply(), f.decimals())));
        boolean noSupplyKey = f.keys().supply() == null;
        checks.add(new Check("Supply key", Boolean.TRUE.equals(op.mintable) != noSupplyKey,
            noSupplyKey ? "No supply key: fixed forever" : "Has a supply key: mintable"));
        checks.add(new Check("No admin key", f.keys().admin() == null,
            f.keys().admin() == null ? "Nobody can change or delete it" : "It has an admin key"));
      }
    }
    if (op.kind == Kind.MINT && success) {
      long minted = tx.tokenChanges().stream()
          .filter(c -> c.tokenId().equals(op.tokenId) && c.account().equals(op.treasury))
          .mapToLong(c -> c.amount()).sum();
      checks.add(new Check("Minted to the treasury", op.amountUnits != null && minted == op.amountUnits,
          whole(minted, op.decimals == null ? 0 : op.decimals) + " " + op.symbol + " arrived in " + op.treasury));
    }
    boolean all = checks.stream().allMatch(Check::passed);
    String fee = hbar(tx.chargedFeeTinybars());
    return new Verification(all ? "VERIFIED" : "MISMATCH",
        all ? "The ledger matches this operation" : "The ledger does not match: see the failed checks",
        checks, fee, timestamp(tx.consensusTimestamp()), txUrl);
  }

  /** Records the fee and consensus time, and settles an operation that had no receipt. */
  private void settle(TokenOperationEntity op, TransactionFacts tx) {
    boolean changed = false;
    if (op.feeTinybars == null) {
      op.feeTinybars = tx.chargedFeeTinybars();
      op.consensusTimestamp = tx.consensusTimestamp();
      changed = true;
    }
    if (op.status == Status.UNKNOWN || op.status == Status.SUBMITTING) {
      boolean success = "SUCCESS".equals(tx.result());
      op.status = success ? Status.CONFIRMED : Status.FAILED;
      op.networkStatus = tx.result();
      if (success && op.kind == Kind.CREATE) {
        op.tokenId = tx.entityId();
        op.totalSupplyAfter = op.amountUnits;
      }
      if (!success) {
        op.failureReason = "Hedera refused it: " + tx.result();
      }
      changed = true;
      save(op);
      record("TOKEN_SETTLED", op);
      return;
    }
    if (changed) {
      save(op);
    }
  }

  // --- Validation ---------------------------------------------------------------------------------

  private record Validated(TokenPlan plan, List<String> problems) {}

  Validated validate(TokenDraft d) {
    List<String> problems = new ArrayList<>();
    if (d == null) {
      return new Validated(null, List.of("Describe the token"));
    }
    String name = clean(d.name());
    if (name == null) {
      problems.add("Give the token a name");
    } else if (name.length() > 100) {
      problems.add("The name can be at most 100 characters");
    }
    String symbol = upper(d.symbol());
    if (symbol == null) {
      problems.add("Give the token a symbol, e.g. BEAN");
    } else if (!symbol.matches("^[A-Z0-9]{1,10}$")) {
      problems.add("The symbol must be 1 to 10 letters or digits, e.g. BEAN");
    }
    Integer decimals = null;
    String dec = clean(d.decimals());
    if (dec == null) {
      problems.add("Say how many decimals (0 for whole units only, up to " + MAX_DECIMALS + ")");
    } else if (!dec.matches("^\\d{1,2}$") || Integer.parseInt(dec) > MAX_DECIMALS) {
      problems.add("Decimals must be a whole number from 0 to " + MAX_DECIMALS);
    } else {
      decimals = Integer.parseInt(dec);
    }
    String policy = clean(d.supplyPolicy()) == null ? null : d.supplyPolicy().trim().toUpperCase(Locale.ROOT);
    if (policy == null || !Set.of("FIXED", "CAPPED", "UNLIMITED").contains(policy)) {
      problems.add("Choose a supply: fixed forever, capped or unlimited");
      policy = null;
    }
    String memo = clean(d.memo());
    if (memo != null && memo.getBytes(StandardCharsets.UTF_8).length > 100) {
      problems.add("The memo can be at most 100 bytes");
    }

    Long initial = null;
    Long max = null;
    if (decimals != null) {
      try {
        initial = toUnits(d.initialSupply(), decimals, "The initial supply");
      } catch (IllegalArgumentException e) {
        problems.add(e.getMessage());
      }
      if ("CAPPED".equals(policy)) {
        try {
          max = toUnits(d.maxSupply(), decimals, "The cap");
          if (max <= 0) {
            problems.add("The cap must be greater than zero");
          } else if (initial != null && initial > max) {
            problems.add("The initial supply cannot be above the cap");
          }
        } catch (IllegalArgumentException e) {
          problems.add(e.getMessage());
        }
      }
    }
    if (initial != null && initial == 0 && "FIXED".equals(policy)) {
      problems.add("A fixed supply of zero could never hold anything: give an initial supply");
    }
    if (!problems.isEmpty()) {
      return new Validated(null, problems);
    }
    // FIXED: no supply key and a cap equal to the initial supply, so the ledger itself says "fixed".
    Long cap = switch (policy) {
      case "FIXED" -> initial;
      case "CAPPED" -> max;
      default -> null;
    };
    return new Validated(
        new TokenPlan(name, symbol, decimals, initial, cap, !"FIXED".equals(policy), memo), List.of());
  }

  /** "12.5" with 2 decimals is 1250; finer than the smallest unit or above Hedera's limit is refused. */
  static long toUnits(String amount, int decimals, String what) {
    String text = amount == null ? "" : amount.trim().replace("_", "");
    if (text.isEmpty()) {
      throw new IllegalArgumentException(what + " is missing");
    }
    BigDecimal value;
    try {
      value = new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(what + " must be a number, e.g. 1000000");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException(what + " cannot be negative");
    }
    BigDecimal units = value.movePointRight(decimals);
    if (units.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException(what + " has more decimals than the token (" + decimals + ")");
    }
    BigInteger exact = units.toBigIntegerExact();
    if (exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException(what + " is above what Hedera can count");
    }
    return exact.longValueExact();
  }

  static String whole(long units, int decimals) {
    return BigDecimal.valueOf(units).movePointLeft(decimals).stripTrailingZeros().toPlainString();
  }

  // --- Helpers ------------------------------------------------------------------------------------

  private TokenOperationEntity newOperation(Kind kind, Actor requester, String key) {
    TokenOperationEntity op = new TokenOperationEntity();
    op.id = "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    op.kind = kind;
    op.status = gateway.isLive() ? Status.SUBMITTING : Status.SIMULATED;
    op.idempotencyKey = key;
    op.requestedByType = requester.type() == null ? null : requester.type().name();
    op.requestedById = requester.id();
    op.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    op.updatedAt = op.createdAt;
    return op;
  }

  private void apply(TokenOperationEntity op, TokenResult result) {
    op.networkStatus = result.networkStatus();
    op.status = switch (result.outcome()) {
      case SUCCESS -> Status.CONFIRMED;
      case FAILED -> Status.FAILED;
      case UNKNOWN -> Status.UNKNOWN;
      case SIMULATED -> Status.SIMULATED;
    };
    if (result.outcome() == Outcome.FAILED) {
      op.failureReason = "Hedera refused it: " + result.networkStatus();
    } else if (result.outcome() == Outcome.UNKNOWN) {
      op.failureReason = "No receipt from Hedera yet (" + result.networkStatus() + "): verify to settle it from the ledger";
    }
  }

  private TokenOperationEntity save(TokenOperationEntity op) {
    op.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    return operations.saveAndFlush(op);
  }

  private void record(String action, TokenOperationEntity op) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("operationId", op.id);
    put(metadata, "tokenId", op.tokenId);
    put(metadata, "symbol", op.symbol);
    put(metadata, "name", op.name);
    if (op.amountUnits != null && op.decimals != null) {
      metadata.put("amount", whole(op.amountUnits, op.decimals));
    }
    if (op.kind == Kind.CREATE) {
      metadata.put("supply", op.maxSupplyUnits == null ? "UNLIMITED"
          : Boolean.TRUE.equals(op.mintable) ? "CAPPED" : "FIXED");
    }
    put(metadata, "treasury", op.treasury);
    put(metadata, "transactionId", op.transactionId);
    put(metadata, "networkStatus", op.networkStatus);
    audit.record(AGENT, action, op.status.name(), metadata);
  }

  Operation view(TokenOperationEntity o) {
    int dec = o.decimals == null ? 0 : o.decimals;
    return new Operation(
        o.id, o.kind.name(), o.status.name(), o.tokenId, o.name, o.symbol, o.decimals,
        o.amountUnits == null ? null : whole(o.amountUnits, dec),
        o.maxSupplyUnits == null ? null : whole(o.maxSupplyUnits, dec),
        o.mintable, o.memo, o.treasury, o.transactionId, o.networkStatus,
        o.totalSupplyAfter == null ? null : whole(o.totalSupplyAfter, dec),
        o.failureReason, o.feeTinybars == null ? null : hbar(o.feeTinybars),
        o.consensusTimestamp == null ? null : timestamp(o.consensusTimestamp),
        o.requestedById, o.createdAt == null ? null : o.createdAt.toString(),
        o.transactionId == null || o.status == Status.SIMULATED ? null : transactionUrl(o.transactionId),
        o.tokenId == null ? null : tokenUrl(o.tokenId));
  }

  private TokenFacts factsOrThrow(String tokenId) {
    FactsLookup lookup = mirror.facts(tokenId);
    if (lookup.state() == LookupState.UNAVAILABLE) {
      throw new TokenMirrorUnavailableException("Mirror Node could not be reached to read " + tokenId);
    }
    if (lookup.state() == LookupState.NOT_FOUND || lookup.facts().deleted()) {
      throw new java.util.NoSuchElementException("There is no token " + tokenId + " on " + network());
    }
    return lookup.facts();
  }

  private static boolean isCapped(TokenFacts f) {
    return "FINITE".equals(f.supplyType()) && f.maxSupply() != null && f.maxSupply() > 0;
  }

  private static void requireTokenId(String tokenId) {
    if (tokenId == null || !tokenId.matches(ACCOUNT_ID)) {
      throw new IllegalArgumentException("A token id looks like 0.0.12345");
    }
  }

  private static String scopedKey(String key, Actor requester) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (!key.matches(KEY)) {
      throw new IllegalArgumentException("Idempotency-Key must be 8 to 64 letters, digits, '-', '_' or ':'");
    }
    return requester.id() + ":" + key;
  }

  private String network() {
    return properties.getNetwork() == null ? "testnet" : properties.getNetwork().toLowerCase(Locale.ROOT);
  }

  String transactionUrl(String transactionId) {
    return "https://hashscan.io/" + network() + "/transaction/" + transactionId;
  }

  String tokenUrl(String tokenId) {
    return "https://hashscan.io/" + network() + "/token/" + tokenId;
  }

  private static String hbar(long tinybars) {
    return BigDecimal.valueOf(tinybars).movePointLeft(8).stripTrailingZeros().toPlainString();
  }

  /** Consensus timestamps are "seconds.nanos"; shown as ISO time. */
  private static String timestamp(String consensus) {
    if (consensus == null || consensus.isBlank()) {
      return null;
    }
    try {
      String[] parts = consensus.split("\\.");
      return Instant.ofEpochSecond(Long.parseLong(parts[0]), parts.length > 1 ? Long.parseLong(parts[1]) : 0).toString();
    } catch (NumberFormatException e) {
      return consensus;
    }
  }

  private static String clean(String value) {
    if (value == null) {
      return null;
    }
    String v = value.replaceAll("\\p{Cntrl}", "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String upper(String value) {
    String v = clean(value);
    return v == null ? null : v.toUpperCase(Locale.ROOT);
  }

  private static void put(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
