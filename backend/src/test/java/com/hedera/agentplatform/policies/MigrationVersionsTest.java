package com.hedera.agentplatform.policies;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Two migrations with the same version stop Flyway at Spring context startup, so every test that
 * boots the app errors at once and none of them says why. That is exactly how issue #40 surfaced:
 * the collision only appeared when two lanes were merged, hours of debugging away from the branch
 * that caused it.
 *
 * <p>This reads the migration directory directly, so a lane that adds a colliding version sees one
 * named failure on its own branch instead of a wall of context-load errors after the merge.
 */
class MigrationVersionsTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static String versionOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(1, name.indexOf("__"));
    }

    @Test
    void noTwoMigrationsShareAVersion() throws IOException {
        Map<String, List<String>> byVersion = new HashMap<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            files.filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .forEach(file ->
                            byVersion
                                    .computeIfAbsent(versionOf(file), key -> new ArrayList<>())
                                    .add(file.getFileName().toString()));
        }

        List<List<String>> collisions = new ArrayList<>();
        byVersion.values().stream().filter(names -> names.size() > 1).forEach(collisions::add);

        assertThat(collisions)
                .as("migrations sharing a version number: Flyway refuses to start the app")
                .isEmpty();
    }
}
