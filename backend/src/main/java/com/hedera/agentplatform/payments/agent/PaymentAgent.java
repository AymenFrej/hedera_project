package com.hedera.agentplatform.payments.agent;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.dto.IntentUnderstanding;
import com.hedera.agentplatform.payments.dto.PaymentIntent;
import com.hedera.agentplatform.payments.dto.PaymentResponse;
import com.hedera.agentplatform.payments.service.IntentService;
import com.hedera.agentplatform.payments.service.PaymentService;
import com.hedera.agentplatform.shared.agent.AgentCapability;
import com.hedera.agentplatform.shared.model.AgentAction;
import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentResult;
import com.hedera.agentplatform.shared.model.AgentStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Entry point for the future orchestrator. It expects structured parameters in the request context
 * ({@code destination}, {@code amount}, optional {@code tokenId}, {@code envelope}, {@code memo}),
 * never free text: whatever turns a sentence into those fields, the payment goes through exactly the
 * same validation, policy check and audit as one created from the UI.
 */
@Component
public class PaymentAgent implements AgentCapability {

  static final String TRANSFER = "TRANSFER";
  static final String REQUEST_ID = "requestId";

  private final PaymentService service;
  private final Validator validator;
  private final IntentService intents;

  public PaymentAgent(PaymentService service, Validator validator, IntentService intents) {
    this.service = service;
    this.validator = validator;
    this.intents = intents;
  }

  @Override
  public boolean supports(AgentIntent intent) {
    return intent == AgentIntent.SEND_PAYMENT;
  }

  @Override
  public AgentPlan plan(AgentRequest request) {
    Map<String, Object> context = request.context() == null ? Map.of() : request.context();
    List<AgentAction> resolutions = new ArrayList<>();
    if (context.containsKey("recipient") && !context.containsKey("destination")) {
      // An intent (e.g. from the orchestrator): resolved exactly as the Payments page resolves it.
      IntentUnderstanding understood =
          intents.understand(
              new PaymentIntent(
                  string(context.get("recipient")),
                  string(context.get("amount")),
                  string(context.get("asset")),
                  string(context.get("envelope")),
                  string(context.get("memo")),
                  string(context.get("keepAtLeast"))));
      for (IntentUnderstanding.Resolution r : understood.steps()) {
        resolutions.add(
            new AgentAction(
                "RESOLVE",
                r.field() + ": " + r.input() + " -> " + (r.value() == null ? "not resolved" : r.value())
                    + " (" + r.source() + ")",
                Map.of()));
      }
      if (!understood.understood()) {
        List<AgentAction> actions = new ArrayList<>(resolutions);
        actions.add(new AgentAction("INVALID_REQUEST", String.join("; ", understood.problems()), Map.of()));
        return new AgentPlan(UUID.randomUUID().toString(), PaymentService.AGENT, AgentStatus.FAILED, actions);
      }
      CreatePaymentRequest r = understood.request();
      Map<String, Object> resolved = new HashMap<>();
      resolved.put("destination", r.destination());
      resolved.put("amount", r.amount());
      putIfPresent(resolved, "tokenId", r.tokenId());
      putIfPresent(resolved, "envelope", r.envelope());
      putIfPresent(resolved, "memo", r.memo());
      putIfPresent(resolved, "keepAtLeast", r.keepAtLeast());
      context = resolved;
    }
    CreatePaymentRequest payment = toPaymentRequest(context);
    String problems = validate(payment);
    if (problems != null) {
      return new AgentPlan(
          UUID.randomUUID().toString(),
          PaymentService.AGENT,
          AgentStatus.FAILED,
          List.of(new AgentAction("INVALID_REQUEST", problems, Map.of())));
    }

    Map<String, Object> parameters = new HashMap<>(context);
    if (request.requestId() != null) {
      parameters.put(REQUEST_ID, request.requestId());
    }
    String what =
        payment.amount()
            + " "
            + (payment.tokenId() == null ? "HBAR" : "of token " + payment.tokenId())
            + " to "
            + payment.destination();
    return new AgentPlan(
        UUID.randomUUID().toString(),
        PaymentService.AGENT,
        AgentStatus.READY,
        concat(
            resolutions,
            new AgentAction("CHECK_POLICY", "Check the payment policy for " + what, Map.of()),
            new AgentAction(TRANSFER, "Transfer " + what, Map.copyOf(parameters))));
  }

  @Override
  public AgentResult execute(AgentPlan plan) {
    AgentAction transfer =
        plan.actions().stream().filter(a -> TRANSFER.equals(a.type())).findFirst().orElse(null);
    if (plan.status() != AgentStatus.READY || transfer == null) {
      return new AgentResult(plan.planId(), AgentStatus.FAILED, "Plan is not executable", Map.of());
    }

    CreatePaymentRequest request = toPaymentRequest(transfer.parameters());
    String problems = validate(request);
    if (problems != null) {
      return new AgentResult(plan.planId(), AgentStatus.FAILED, problems, Map.of());
    }

    // One agent request is one payment, however many times the orchestrator retries it.
    PaymentResponse payment = service.create(request, transfer.parameters().get(REQUEST_ID) == null
        ? null : "agent:" + transfer.parameters().get(REQUEST_ID));
    AgentStatus status =
        switch (payment.status()) {
          case "AWAITING_APPROVAL" -> AgentStatus.APPROVAL_REQUIRED;
          case "REJECTED", "FAILED" -> AgentStatus.FAILED;
          default -> AgentStatus.COMPLETED;
        };
    String message =
        payment.failureReason() != null ? payment.failureReason() : payment.policyReason();

    Map<String, Object> data = new HashMap<>();
    data.put("paymentId", payment.id());
    data.put("paymentStatus", payment.status());
    if (payment.transactionId() != null) {
      data.put("transactionId", payment.transactionId());
    }
    return new AgentResult(plan.planId(), status, message, data);
  }

  private static CreatePaymentRequest toPaymentRequest(Map<String, Object> context) {
    Map<String, Object> c = context == null ? Map.of() : context;
    return new CreatePaymentRequest(
        string(c.get("destination")),
        string(c.get("amount")),
        string(c.get("tokenId")),
        string(c.get("envelope")),
        string(c.get("memo")),
        string(c.get("keepAtLeast")));
  }

  private static List<AgentAction> concat(List<AgentAction> first, AgentAction... rest) {
    List<AgentAction> all = new ArrayList<>(first);
    all.addAll(List.of(rest));
    return all;
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private String validate(CreatePaymentRequest request) {
    var violations = validator.validate(request);
    if (violations.isEmpty()) {
      return null;
    }
    return violations.stream()
        .map((ConstraintViolation<?> v) -> v.getPropertyPath() + " " + v.getMessage())
        .sorted()
        .collect(Collectors.joining("; "));
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }
}
