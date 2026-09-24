package com.hedera.agentplatform.accounts;

import com.hedera.agentplatform.accounts.auth.AuthSessionService;
import com.hedera.agentplatform.accounts.repository.UserRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Uses a real session, so integration tests still exercise production authorization. */
public final class AuthenticatedMvc {
    private AuthenticatedMvc() {}
    public static MockMvc admin(WebApplicationContext context) {
        var user = context.getBean(UserRepository.class).findById("admin_demo").orElseThrow();
        String token = context.getBean(AuthSessionService.class).create(user);
        return MockMvcBuilders.webAppContextSetup(context)
            .defaultRequest(get("/").header("Authorization", "Bearer " + token)).build();
    }
}
