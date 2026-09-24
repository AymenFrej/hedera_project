package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
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

    /**
     * decidedBy is the username resolved from the session, so the approvals row names whoever the
     * walkthrough told the presenter to log in as. The walkthrough logs in as admin@example.com,
     * seeded in V7 as admin_demo, so naming any other seeded demo user describes a run nobody is
     * told how to perform. Matching the username rather than a whole sentence keeps a reworded
     * walkthrough from reading as a regression.
     */
    @Test
    void theWalkthroughNamesTheApproverItsOwnLoginProduces() throws IOException {
        String text = String.join("\n", lines());
        assertThat(text).contains("admin@example.com");

        List<String> approversNamed = Pattern.compile("[a-z]+_demo")
                .matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .toList();

        assertThat(approversNamed)
                .as("demo usernames named in demo/policies.md for the documented admin login")
                .isNotEmpty()
                .containsExactly("admin_demo");
    }
}
