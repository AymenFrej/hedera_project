package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the walkthrough a judge is told to copy-paste.
 *
 * <p>Every call to /api/v1/policies now goes through the Accounts interceptor, which answers 401
 * without a bearer token and 403 for a role that is not ADMIN or PLATFORM. A demo script that omits
 * the login step fails on its first command, so the omission is a defect in the document itself.
 */
class DemoScriptAuthTest {

    private static final Path DEMO = Path.of("..", "demo", "policies.md");

    private List<String> lines() throws IOException {
        return Files.readAllLines(DEMO);
    }

    @Test
    void everyDocumentedPoliciesCurlSendsAnAuthorizationHeader() throws IOException {
        List<String> offenders = lines().stream()
                .filter(line -> line.contains("curl"))
                .filter(line -> line.contains("$API") || line.contains("/api/v1/policies"))
                .filter(line -> !line.contains("-H \"$AUTH\""))
                .toList();

        assertThat(offenders)
                .as("policies curl commands in demo/policies.md missing the auth header")
                .isEmpty();
    }

    @Test
    void theScriptLogsInBeforeCallingThePolicyApi() throws IOException {
        String text = String.join("\n", lines());

        assertThat(text).contains("/auth/login");
        assertThat(text).contains("Authorization: Bearer");
    }

    @Test
    void theScriptNamesARoleThatMayReachThePolicyApi() throws IOException {
        String text = String.join("\n", lines());

        assertThat(text).containsAnyOf("admin@example.com", "platform@example.com");
    }
}
