package com.hedera.agentplatform.tokens.agent;

import com.hedera.agentplatform.shared.agent.AgentCapability;
import com.hedera.agentplatform.shared.model.AgentAction;
import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentResult;
import com.hedera.agentplatform.shared.model.AgentStatus;
import com.hedera.agentplatform.tokens.dto.TokenDraft;
import com.hedera.agentplatform.tokens.dto.TokenViews.Operation;
import com.hedera.agentplatform.tokens.dto.TokenViews.Promise;
import com.hedera.agentplatform.tokens.dto.TokenViews.TokenPreview;
import com.hedera.agentplatform.tokens.service.TokenService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entry point for the future orchestrator. It takes the Studio's fields in the request context
 * ({@code name}, {@code symbol}, {@code decimals}, {@code initialSupply}, {@code supplyPolicy},
 * optional {@code maxSupply}, {@code memo}), never free text, and goes through exactly the same
 * validation, preview, creation, audit and verification as the Tokens page.
 */
@Component
public class TokenAgent implements AgentCapability {

  static final String CREATE = "CREATE_TOKEN";
  static final String REQUEST_ID = "requestId";
  private static final List<String> FIELDS =
      List.of("name", "symbol", "decimals", "initialSupply", "supplyPolicy", "maxSupply", "memo");

  private final TokenService service;

  public TokenAgent(TokenService service) {
    this.service = service;
  }

  @Override
  public boolean supports(AgentIntent intent) {
    return intent == AgentIntent.CREATE_TOKEN;
  }

  @Override
  public AgentPlan plan(AgentRequest request) {
    Map<String, Object> context = request.context() == null ? Map.of() : request.context();
    TokenDraft draft = draft(context);
    TokenPreview preview = service.preview(draft);
    if (!preview.valid()) {
      return new AgentPlan(UUID.randomUUID().toString(), TokenService.AGENT, AgentStatus.FAILED,
          List.of(new AgentAction("INVALID_REQUEST", String.join("; ", preview.problems()), Map.of())));
    }
    List<AgentAction> actions = new ArrayList<>();
    for (Promise p : preview.promises()) {
      actions.add(new AgentAction("PROMISE", p.kind() + ": " + p.title(), Map.of()));
    }
    Map<String, Object> parameters = new HashMap<>();
    for (String f : FIELDS) {
      if (context.get(f) != null) {
        parameters.put(f, context.get(f).toString());
      }
    }
    if (request.requestId() != null) {
      parameters.put(REQUEST_ID, request.requestId());
    }
    actions.add(new AgentAction(CREATE,
        "Create " + preview.name() + " (" + preview.symbol() + "), " + preview.initialSupply() + " to the treasury",
        Map.copyOf(parameters)));
    return new AgentPlan(UUID.randomUUID().toString(), TokenService.AGENT, AgentStatus.READY, actions);
  }

  @Override
  public AgentResult execute(AgentPlan plan) {
    AgentAction create = plan.actions().stream().filter(a -> CREATE.equals(a.type())).findFirst().orElse(null);
    if (plan.status() != AgentStatus.READY || create == null) {
      return new AgentResult(plan.planId(), AgentStatus.FAILED, "Plan is not executable", Map.of());
    }
    Object requestId = create.parameters().get(REQUEST_ID);
    // One agent request creates one token, however many times the orchestrator retries it.
    Operation op = service.create(draft(create.parameters()), requestId == null ? null : "agent-" + requestId);
    AgentStatus status = switch (op.status()) {
      case "FAILED" -> AgentStatus.FAILED;
      default -> AgentStatus.COMPLETED;
    };
    Map<String, Object> data = new HashMap<>();
    data.put("operationId", op.id());
    data.put("operationStatus", op.status());
    if (op.tokenId() != null) {
      data.put("tokenId", op.tokenId());
    }
    if (op.transactionId() != null) {
      data.put("transactionId", op.transactionId());
    }
    return new AgentResult(plan.planId(), status,
        op.failureReason() != null ? op.failureReason() : "Token " + op.status().toLowerCase(), data);
  }

  private static TokenDraft draft(Map<String, Object> c) {
    return new TokenDraft(s(c.get("name")), s(c.get("symbol")), s(c.get("decimals")), s(c.get("initialSupply")),
        s(c.get("supplyPolicy")), s(c.get("maxSupply")), s(c.get("memo")));
  }

  private static String s(Object value) {
    return value == null ? null : value.toString();
  }
}
