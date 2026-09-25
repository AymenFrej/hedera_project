package com.hedera.agentplatform.accounts.agent;

import com.hedera.agentplatform.accounts.dto.CreateManagedUserRequest;
import com.hedera.agentplatform.accounts.dto.ManagedUserResponse;
import com.hedera.agentplatform.accounts.dto.ProfileUpdateRequest;
import com.hedera.agentplatform.accounts.dto.RoleUpdateRequest;
import com.hedera.agentplatform.accounts.service.AdminUserService;
import com.hedera.agentplatform.payments.language.IntentExtractor;
import com.hedera.agentplatform.shared.agent.AgentCapability;
import com.hedera.agentplatform.shared.model.AgentAction;
import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentPlan;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentResult;
import com.hedera.agentplatform.shared.model.AgentStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** Admin account operations with the same authorization and wallet rules as the admin API. */
@Component
public class AccountAgent implements AgentCapability {
    private static final String OPERATION = "operation";
    private static final String AUTHORIZATION = "authorization";
    private static final Pattern NAME_CHANGE = Pattern.compile("(?i)^(?:make|change|update|set)\\s+(.+?)(?:'s|s)\\s+name\\s+(?:to\\s+)?(.+)$");
    private static final Pattern ROLE_CHANGE = Pattern.compile("(?i)^(?:make|change|update|set)\\s+(.+?)(?:'s|s)\\s+role\\s+(?:to\\s+)?(USER|ADMIN|AUDITOR|PLATFORM)$");
    private static final Pattern CLOSE = Pattern.compile("(?i)^(?:close|disable)\\s+(.+)$");
    private static final Pattern RESTORE = Pattern.compile("(?i)^(?:restore|enable)\\s+(.+)$");
    private final AdminUserService service;
    private final ObjectProvider<IntentExtractor> extractors;

    public AccountAgent(AdminUserService service) { this(service, null); }
    @Autowired
    public AccountAgent(AdminUserService service, ObjectProvider<IntentExtractor> extractors) { this.service = service; this.extractors = extractors; }
    @Override public boolean supports(AgentIntent intent) { return intent == AgentIntent.CREATE_ACCOUNT; }
    @Override public AgentPlan plan(AgentRequest request) { return planForAdmin(request, null); }

    public AgentPlan planForAdmin(AgentRequest request, String authorization) {
        Map<String, Object> context = interpret(request, authorization);
        String operation = string(context.get(OPERATION)).toUpperCase();
        if (!List.of("LIST", "CREATE", "ROLE", "CLOSE", "RESTORE", "PROFILE").contains(operation)) return failed("Use LIST, CREATE, ROLE, CLOSE, RESTORE or PROFILE.");
        if (context.containsKey("resolutionError")) return failed(string(context.get("resolutionError")));
        Map<String, Object> parameters = new HashMap<>(context);
        parameters.put(OPERATION, operation); parameters.put(AUTHORIZATION, authorization);
        return new AgentPlan(UUID.randomUUID().toString(), "AccountAgent", AgentStatus.READY,
                List.of(new AgentAction(operation, description(operation), Map.copyOf(parameters))));
    }

    @Override public AgentResult execute(AgentPlan plan) {
        if (plan == null || plan.status() != AgentStatus.READY || plan.actions().isEmpty()) return new AgentResult(plan == null ? UUID.randomUUID().toString() : plan.planId(), AgentStatus.FAILED, "Plan is not executable", Map.of());
        Map<String, Object> parameters = plan.actions().get(0).parameters();
        String authorization = string(parameters.get(AUTHORIZATION));
        if (authorization.isBlank()) return new AgentResult(plan.planId(), AgentStatus.FAILED, "An authenticated administrator session is required", Map.of());
        try {
            String operation = string(parameters.get(OPERATION));
            return switch (operation) {
                case "LIST" -> completed(plan, "Accounts loaded", Map.of("users", service.list(authorization)));
                case "CREATE" -> completed(plan, "Account created", Map.of("user", service.create(authorization, new CreateManagedUserRequest(string(parameters.get("email")), string(parameters.get("password")), string(parameters.get("displayName")), string(parameters.get("role"))))));
                case "ROLE" -> completed(plan, "Account role updated", Map.of("user", service.updateRole(authorization, string(parameters.get("id")), new RoleUpdateRequest(string(parameters.get("role"))))));
                case "CLOSE" -> { service.delete(authorization, string(parameters.get("id"))); yield completed(plan, "Account closed", Map.of("userId", string(parameters.get("id")))); }
                case "RESTORE" -> completed(plan, "Account restored", Map.of("user", service.restore(authorization, string(parameters.get("id")), new RoleUpdateRequest(string(parameters.get("role"))))));
                case "PROFILE" -> completed(plan, "Account profile updated", Map.of("user", service.updateProfile(authorization, string(parameters.get("id")), new ProfileUpdateRequest(string(parameters.get("email")), string(parameters.get("displayName"))))));
                default -> new AgentResult(plan.planId(), AgentStatus.FAILED, "Unsupported account operation", Map.of());
            };
        } catch (RuntimeException exception) { return new AgentResult(plan.planId(), AgentStatus.FAILED, exception.getMessage(), Map.of()); }
    }

    private static AgentResult completed(AgentPlan plan, String message, Map<String, Object> data) { return new AgentResult(plan.planId(), AgentStatus.COMPLETED, message, data); }
    private static AgentPlan failed(String message) { return new AgentPlan(UUID.randomUUID().toString(), "AccountAgent", AgentStatus.FAILED, List.of(new AgentAction("INVALID_REQUEST", message, Map.of()))); }
    private static String description(String operation) { return switch (operation) { case "LIST" -> "List managed accounts"; case "CREATE" -> "Create a real Hedera-backed account"; case "ROLE" -> "Change an account role"; case "CLOSE" -> "Close an account without deleting its wallet"; case "RESTORE" -> "Restore a closed account"; case "PROFILE" -> "Edit an account profile"; default -> operation; }; }
    private static String string(Object value) { return value == null ? "" : value.toString().trim(); }

    private Map<String, Object> interpret(AgentRequest request, String authorization) {
        Map<String, Object> context = new HashMap<>(request == null || request.context() == null ? Map.of() : request.context());
        context.put(AUTHORIZATION, authorization);
        if (!string(context.get(OPERATION)).isBlank()) return context;
        String prompt = string(request == null ? null : request.prompt());
        IntentExtractor extractor = extractors == null ? null : extractors.getIfAvailable();
        if (extractor != null && !prompt.isBlank()) {
            try {
                AccountCommand command = extractor.extractAs(
                        "Interpret this administrator account-management request. Return only one command. "
                                + "Allowed operations are LIST, CREATE, ROLE, CLOSE, RESTORE, PROFILE. "
                                + "For changing a person, put their name, email, or user ID in person. "
                                + "For PROFILE, displayName is the new name and email is the new email. "
                                + "For CREATE, include email, displayName, password, and role. "
                                + "Never invent missing values; use empty strings. Do not execute anything.",
                        prompt, AccountCommand.class);
                context.put(OPERATION, command.operation());
                put(context, "person", command.person()); put(context, "email", command.email());
                put(context, "displayName", command.displayName()); put(context, "password", command.password());
                put(context, "role", command.role());
                if (!"CREATE".equalsIgnoreCase(command.operation())) resolvePerson(context);
                return context;
            } catch (IntentExtractor.ExtractionException ignored) {
                // The deterministic parser below remains available when the model is unavailable.
            }
        }
        if (prompt.equalsIgnoreCase("list accounts") || prompt.equalsIgnoreCase("show accounts")) {
            context.put(OPERATION, "LIST");
            return context;
        }
        Matcher name = NAME_CHANGE.matcher(prompt);
        if (name.matches()) {
            context.put(OPERATION, "PROFILE");
            context.put("person", name.group(1).trim());
            context.put("displayName", name.group(2).trim());
            resolvePerson(context);
            return context;
        }
        Matcher role = ROLE_CHANGE.matcher(prompt);
        if (role.matches()) {
            context.put(OPERATION, "ROLE");
            context.put("person", role.group(1).trim());
            context.put("role", role.group(2).toUpperCase());
            resolvePerson(context);
            return context;
        }
        Matcher close = CLOSE.matcher(prompt);
        if (close.matches()) {
            context.put(OPERATION, "CLOSE"); context.put("person", close.group(1).trim()); resolvePerson(context); return context;
        }
        Matcher restore = RESTORE.matcher(prompt);
        if (restore.matches()) {
            context.put(OPERATION, "RESTORE"); context.put("person", restore.group(1).trim()); context.put("role", "USER"); resolvePerson(context); return context;
        }
        context.put(OPERATION, "UNKNOWN");
        return context;
    }

    private void resolvePerson(Map<String, Object> context) {
        String person = string(context.get("person")).toLowerCase();
        String authorization = string(context.get(AUTHORIZATION));
        if (authorization.isBlank()) return;
        List<ManagedUserResponse> matches = service.list(authorization).stream()
                .filter(user -> user.displayName().toLowerCase().contains(person) || user.email().toLowerCase().contains(person) || user.id().equalsIgnoreCase(person))
                .toList();
        if (matches.size() == 1) context.put("id", matches.get(0).id());
        else if (matches.isEmpty()) context.put("resolutionError", "No account matched '" + context.get("person") + "'.");
        else context.put("resolutionError", "More than one account matched '" + context.get("person") + "'. Use the email or user ID.");
    }

    private static void put(Map<String, Object> context, String key, String value) { if (value != null && !value.isBlank()) context.put(key, value.trim()); }
    public record AccountCommand(String operation, String person, String email, String displayName, String password, String role) {}
}
