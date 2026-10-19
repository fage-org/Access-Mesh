package cn.ac.fage.accessmesh.e2e;

import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Independent example database on the existing E2E PostgreSQL container. */
final class E2eExampleDatabase {
    private final Map<String, String> environment;

    private E2eExampleDatabase(Map<String, String> environment) {
        this.environment = environment;
    }

    static E2eExampleDatabase create(PostgreSQLContainer postgres) throws Exception {
        String password = UUID.randomUUID().toString();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            // Only a locally generated UUID enters this test-only DDL literal.
            statement.execute("CREATE ROLE example_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + password + "'");
            statement.execute("CREATE DATABASE example_db OWNER example_app");
            try (var rows = statement.executeQuery("SELECT has_table_privilege('example_app', 'sys_user', 'SELECT')")) {
                rows.next();
                if (rows.getBoolean(1)) throw new IllegalStateException("example account can read IAM facts");
            }
        }
        String url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
            + "/example_db?stringtype=unspecified";
        try (var connection = DriverManager.getConnection(url, "example_app", password);
             var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("..", "docs", "design", "schema", "example-service.sql")));
        }
        return new E2eExampleDatabase(Map.of(
            "EXAMPLE_DB_HOST", postgres.getHost(), "EXAMPLE_DB_PORT", postgres.getMappedPort(5432).toString(),
            "EXAMPLE_DB_NAME", "example_db", "EXAMPLE_DB_USERNAME", "example_app", "EXAMPLE_DB_PASSWORD", password));
    }

    Map<String, String> environment(Map<String, String> serviceEnvironment) {
        var result = new HashMap<>(serviceEnvironment);
        result.putAll(environment);
        return Map.copyOf(result);
    }
}
