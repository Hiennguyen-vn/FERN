package com.fern.reportservice.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class SnowflakeBootstrapCommand {

    private SnowflakeBootstrapCommand() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly 1 argument: <bootstrap-sql-file>");
        }

        var sqlFile = Path.of(args[0]).toAbsolutePath().normalize();
        var sql = Files.readString(sqlFile);
        var statements = splitStatements(applyPlaceholders(sql));

        Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
        try (Connection connection = DriverManager.getConnection(buildJdbcUrl(), buildConnectionProperties())) {
            for (String statement : statements) {
                System.out.println("[bootstrap] Executing: " + summarize(statement));
                try (Statement jdbcStatement = connection.createStatement()) {
                    jdbcStatement.execute(statement);
                }
            }
        }
    }

    private static String buildJdbcUrl() {
        var account = requireEnv("FERN_SNOWFLAKE_ACCOUNT");
        var role = requireEnv("FERN_SNOWFLAKE_ROLE");
        var jdbcOptions = env("FERN_SNOWFLAKE_JDBC_OPTIONS", "&JDBC_QUERY_RESULT_FORMAT=JSON");
        var authenticator = env("FERN_SNOWFLAKE_AUTHENTICATOR", "");

        var url = new StringBuilder()
            .append("jdbc:snowflake://")
            .append(account)
            .append(".snowflakecomputing.com/?role=")
            .append(role);

        if (!authenticator.isBlank()) {
            url.append("&authenticator=").append(authenticator);
        }

        if (!jdbcOptions.isBlank()) {
            if (jdbcOptions.charAt(0) == '&') {
                url.append(jdbcOptions);
            } else {
                url.append('&').append(jdbcOptions);
            }
        }

        return url.toString();
    }

    private static Properties buildConnectionProperties() {
        var properties = new Properties();
        properties.setProperty("user", requireEnv("FERN_SNOWFLAKE_USER"));
        var password = env("FERN_SNOWFLAKE_PASSWORD", "");
        if (!password.isBlank()) {
            properties.setProperty("password", password);
        }
        return properties;
    }

    private static String applyPlaceholders(String sql) {
        return sql
            .replace("${reportingDatabase}", requireEnv("FERN_SNOWFLAKE_DATABASE"))
            .replace("${ingestWarehouse}", requireEnv("FERN_SNOWFLAKE_WAREHOUSE"))
            .replace("${biWarehouse}", requireEnv("FERN_SNOWFLAKE_BI_WAREHOUSE"));
    }

    private static List<String> splitStatements(String sql) {
        var statements = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inSingleQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
            }

            if (ch == ';' && !inSingleQuote) {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        var statement = current.toString().trim();
        if (!statement.isBlank()) {
            statements.add(statement);
        }
    }

    private static String summarize(String statement) {
        var oneLine = statement.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 117) + "...";
    }

    private static String requireEnv(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String env(String name, String defaultValue) {
        var value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
