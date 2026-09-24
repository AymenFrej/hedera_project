package com.hedera.agentplatform.accounts;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MainMigrationUpgradeTest {
    @Test void existingMainDatabaseUpgradesWithoutLosingPolicyData() throws Exception {
        String url = "jdbc:h2:mem:upgrade_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("5").load().migrate();
        try (var connection = DriverManager.getConnection(url,"sa",""); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE envelope_balances SET balance = 321 WHERE envelope = 'RENT'");
        }
        var flyway = Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url,"sa",""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT balance FROM envelope_balances WHERE envelope = 'RENT'")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(321L);
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(4);
            }
            statement.executeQuery("SELECT encrypted_private_key FROM accounts").close();
        }
    }
}
