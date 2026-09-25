package com.hedera.agentplatform.accounts.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hedera.agentplatform.accounts.dto.ManagedUserResponse;
import com.hedera.agentplatform.accounts.service.AdminUserService;
import com.hedera.agentplatform.shared.model.AgentIntent;
import com.hedera.agentplatform.shared.model.AgentRequest;
import com.hedera.agentplatform.shared.model.AgentStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountAgentTest {
    @Test
    void understands_a_name_change_and_resolves_the_matching_user() {
        var service = mock(AdminUserService.class);
        when(service.list("Bearer admin-token")).thenReturn(List.of(
                new ManagedUserResponse("user_1", "aymen@example.com", "Aymen Frej", "USER", "acct_1", "0.0.123")));
        var agent = new AccountAgent(service);

        var plan = agent.planForAdmin(new AgentRequest("r1", AgentIntent.CREATE_ACCOUNT, "make Aymen's name Ahmed", Map.of()), "Bearer admin-token");

        assertThat(plan.status()).isEqualTo(AgentStatus.READY);
        assertThat(plan.actions().get(0).type()).isEqualTo("PROFILE");
        assertThat(plan.actions().get(0).parameters()).containsEntry("id", "user_1").containsEntry("displayName", "Ahmed");
    }

    @Test
    void refuses_an_ambiguous_person_instead_of_changing_the_wrong_account() {
        var service = mock(AdminUserService.class);
        when(service.list("Bearer admin-token")).thenReturn(List.of(
                new ManagedUserResponse("user_1", "aymen.one@example.com", "Aymen One", "USER", "acct_1", "0.0.123"),
                new ManagedUserResponse("user_2", "aymen.two@example.com", "Aymen Two", "USER", "acct_2", "0.0.124")));
        var agent = new AccountAgent(service);

        var plan = agent.planForAdmin(new AgentRequest("r1", AgentIntent.CREATE_ACCOUNT, "make Aymen's name Ahmed", Map.of()), "Bearer admin-token");

        assertThat(plan.status()).isEqualTo(AgentStatus.FAILED);
        verify(service, never()).updateProfile(anyString(), anyString(), any());
    }
}
