package cn.ac.fage.accessmesh.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Shared PG lifecycle; service schemas and their explicit seed SQL remain caller-owned. */
public final class PgTestInfra {
    public static final String USERNAME = "perm";
    public static final String PASSWORD = "perm";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("it_hub").withUsername(USERNAME).withPassword(PASSWORD).withReuse(true);
    private static final Object CONTAINER_LOCK = new Object();

    private final Path ddl;
    private final String seedSql;
    private final String prefix;
    private final String template;
    private final Map<Class<?>, Binding> bindings = new HashMap<>();
    private boolean ready;

    private record Binding(String database, boolean fromTemplate) {}

    public PgTestInfra(String namespace, Path ddl, String seedSql) {
        if (namespace == null || !namespace.matches("[a-z][a-z0-9]{0,11}")) {
            throw new IllegalArgumentException("Invalid test database namespace");
        }
        this.ddl = ddl;
        this.seedSql = seedSql;
        this.prefix = "it_" + namespace + "_f" + TestForkSlot.number() + "_";
        this.template = prefix + "tpl";
    }

    public synchronized void start() {
        if (ready) return;
        synchronized (CONTAINER_LOCK) {
            if (!POSTGRES.isRunning()) POSTGRES.start();
        }
        try (Connection admin = admin()) {
            var stale = new ArrayList<String>();
            try (var ps = admin.prepareStatement("SELECT datname FROM pg_database WHERE datname LIKE ?")) {
                ps.setString(1, prefix.replace("_", "\\_") + "%");
                try (var rows = ps.executeQuery()) {
                    while (rows.next()) stale.add(rows.getString(1));
                }
            }
            for (String database : stale) {
                if (!database.startsWith(prefix)) throw new IllegalStateException("Unsafe database cleanup target");
                execute(admin, "DROP DATABASE IF EXISTS " + identifier(database) + " WITH (FORCE)");
            }
            execute(admin, "CREATE DATABASE " + identifier(template));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create PG test template " + template, e);
        }
        try (Connection connection = connect(template)) {
            execute(connection, Files.readString(ddl, StandardCharsets.UTF_8));
            if (seedSql != null && !seedSql.isBlank()) execute(connection, seedSql);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Cannot load original test schema " + ddl, e);
        }
        ready = true;
    }

    public synchronized void prepare(Class<?> testClass, boolean fromTemplate) {
        start();
        Binding previous = bindings.get(testClass);
        if (previous != null) {
            if (previous.fromTemplate() != fromTemplate) {
                throw new IllegalStateException("Inconsistent prepare/register schema mode: " + testClass.getName());
            }
            return;
        }
        String database = databaseName(testClass.getName());
        create(database, fromTemplate);
        bindings.put(testClass, new Binding(database, fromTemplate));
    }

    public void register(DynamicPropertyRegistry registry, Class<?> testClass) {
        register(registry, testClass, true);
    }

    public void register(DynamicPropertyRegistry registry, Class<?> testClass, boolean fromTemplate) {
        prepare(testClass, fromTemplate);
        registry.add("spring.datasource.url", () -> jdbcUrl(testClass));
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    public synchronized String jdbcUrl(Class<?> testClass) {
        Binding binding = bindings.get(testClass);
        if (binding == null) throw new IllegalStateException("Unregistered test class: " + testClass.getName());
        return jdbcUrl(binding.database());
    }

    public synchronized String createStandaloneDatabase(String nameHint) {
        start();
        String database = databaseName(nameHint);
        create(database, false);
        return jdbcUrl(database);
    }

    private void create(String database, boolean fromTemplate) {
        try (Connection admin = admin()) {
            execute(admin, "DROP DATABASE IF EXISTS " + identifier(database) + " WITH (FORCE)");
            execute(admin, "CREATE DATABASE " + identifier(database)
                + (fromTemplate ? " TEMPLATE " + identifier(template) : ""));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create isolated PG database " + database, e);
        }
    }

    private Connection admin() throws SQLException {
        return connect("it_hub");
    }

    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), USERNAME, PASSWORD);
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
            + "/" + database + "?stringtype=unspecified";
    }

    private String databaseName(String hint) {
        String readable = hint.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (readable.isEmpty()) throw new IllegalArgumentException("Empty test database name");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(hint.getBytes(StandardCharsets.UTF_8)), 0, 6);
            return prefix + readable.substring(0, Math.min(25, readable.length())) + "_" + hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String identifier(String name) {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
