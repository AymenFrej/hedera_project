package com.hedera.agentplatform.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.hedera.HederaAccountGateway;
import com.hedera.agentplatform.accounts.repository.AccountRepository;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountsAccessTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionService sessions;
    @MockitoBean HederaAccountGateway gateway;

    @BeforeEach void mockWallet() {
        when(gateway.createAccount("0")).thenAnswer(invocation ->
            new HederaAccountGateway.AccountGatewayResult("0.0.12345", "ACTIVE", true, "test-private-key"));
    }
    JsonNode register(String role) throws Exception {
        String body = json.writeValueAsString(Map.of("email", UUID.randomUUID()+"@example.test", "password", "OriginalPass123!", "displayName", "Test User"));
        JsonNode result = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var user = users.findById(result.get("userId").asText()).orElseThrow();
        user.role = role; users.saveAndFlush(user);
        return result;
    }
    String auth(JsonNode user) { return "Bearer "+user.get("token").asText(); }

    @Test void roleMatrixAndWalletIsolation() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER","AUDITOR","ADMIN","PLATFORM"}) {
            var user = register(role);
            mvc.perform(get("/api/v1/accounts").header("Authorization",auth(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(user.get("account").get("id").asText()))
                .andExpect(jsonPath("$[0].encryptedPrivateKey").doesNotExist());
            boolean manager = role.equals("ADMIN") || role.equals("PLATFORM");
            mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(user)))
                .andExpect(status().is(manager ? 200 : 403));
            mvc.perform(get("/api/v1/audit").header("Authorization",auth(user)))
                .andExpect(status().is(role.equals("USER") ? 403 : 200));
            mvc.perform(get("/api/v1/payments").header("Authorization",auth(user)))
                .andExpect(status().is(role.equals("USER") || role.equals("ADMIN") ? 200 : 403));
            mvc.perform(get("/api/v1/tokens").header("Authorization",auth(user)))
                .andExpect(status().is(role.equals("USER") || role.equals("ADMIN") ? 200 : 403));
            if (!manager) {
                mvc.perform(get("/api/v1/policies").header("Authorization",auth(user))).andExpect(status().isForbidden());
                mvc.perform(post("/api/v1/audit").header("Authorization",auth(user)).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            }
        }
    }
    @Test void profilePasswordAndLogout() throws Exception {
        var user = register("USER");
        String token = auth(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(user.get("token").asText()));
        mvc.perform(put("/api/v1/auth/me/profile").header("Authorization",token).contentType("application/json")
            .content("{\"email\":\"updated@example.test\",\"displayName\":\"Updated\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Updated"));
        mvc.perform(put("/api/v1/auth/me/password").header("Authorization",token).contentType("application/json")
            .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"ChangedPass123!\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/auth/me/password").header("Authorization",token).contentType("application/json")
            .content("{\"currentPassword\":\"OriginalPass123!\",\"newPassword\":\"ChangedPass123!\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        var login = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"email\":\" UPDATED@example.test \",\"password\":\"ChangedPass123!\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization",auth(login))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",auth(login))).andExpect(status().isUnauthorized());
    }
    @Test void accountClosureRetainsWalletAndRevokesEverySession() throws Exception {
        var user = register("USER");
        var entity = users.findById(user.get("userId").asText()).orElseThrow();
        String extra = "Bearer "+sessions.create(entity);
        mvc.perform(delete("/api/v1/auth/me").header("Authorization",auth(user))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",extra)).andExpect(status().isUnauthorized());
        var wallet = accounts.findById(entity.accountId).orElseThrow();
        assertThat(wallet.status).isEqualTo("CLOSED");
        assertThat(wallet.encryptedPrivateKey).isNotBlank().isNotEqualTo("test-private-key");
        assertThat(users.findById(entity.id).orElseThrow().role).isEqualTo("DISABLED");
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of(
            "email", entity.email, "password", "OriginalPass123!")))).andExpect(status().isBadRequest());
    }
    @Test void adminChangesRevokeSessionsAndRejectSelfChanges() throws Exception {
        var admin = register("ADMIN");
        var target = register("ADMIN");
        mvc.perform(put("/api/v1/admin/users/"+target.get("userId").asText()+"/role").header("Authorization",auth(admin))
            .contentType("application/json").content("{\"role\":\"USER\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization",auth(target))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/admin/users/"+admin.get("userId").asText()+"/role").header("Authorization",auth(admin))
            .contentType("application/json").content("{\"role\":\"USER\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/users/"+admin.get("userId").asText()).header("Authorization",auth(admin))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/auth/me").header("Authorization",auth(admin))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/"+target.get("userId").asText()).header("Authorization",auth(admin))).andExpect(status().isOk());
        assertThat(users.findById(target.get("userId").asText()).orElseThrow().role).isEqualTo("DISABLED");
    }
    @Test void duplicateEmailAndInvalidRoleDoNotCreateWallet() throws Exception {
        var admin = register("ADMIN");
        clearInvocations(gateway);
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of(
            "email", " "+admin.get("email").asText().toUpperCase()+" ", "password","OriginalPass123!")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/users").header("Authorization",auth(admin)).contentType("application/json")
            .content("{\"email\":\"other@example.test\",\"password\":\"OriginalPass123!\",\"role\":\"SUPERADMIN\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(gateway);
    }
    @Test void adminCanCreateEachSupportedRole() throws Exception {
        var admin = register("ADMIN");
        for (String role : new String[]{"USER", "AUDITOR", "ADMIN", "PLATFORM"}) {
            String email = UUID.randomUUID()+"@example.test";
            mvc.perform(post("/api/v1/admin/users").header("Authorization",auth(admin)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email",email,"password","CreatedPass123!","displayName","Created user","role",role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.hederaAccountId").value("0.0.12345"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email",email,"password","CreatedPass123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
        }
    }
}
