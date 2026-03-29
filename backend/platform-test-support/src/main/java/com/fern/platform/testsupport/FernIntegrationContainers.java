package com.fern.platform.testsupport;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class FernIntegrationContainers {
    private static final String DB_NAME = "fern_master";
    private static final String DB_USER = "fern";

    private static final Process POSTGRES_PROCESS;
    private static final Process REDIS_PROCESS;
    private static final int POSTGRES_PORT;
    private static final int REDIS_PORT;

    static {
        try {
            POSTGRES_PORT = randomPort();
            REDIS_PORT = randomPort();
            POSTGRES_PROCESS = startPostgres();
            REDIS_PROCESS = startRedis();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                POSTGRES_PROCESS.destroy();
                REDIS_PROCESS.destroy();
            }));
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Unable to start local integration services", exception);
        }
    }

    private FernIntegrationContainers() {
    }

    public static String masterJdbcUrl(String currentSchema) {
        return "jdbc:postgresql://127.0.0.1:" + POSTGRES_PORT + "/" + DB_NAME + "?currentSchema=" + currentSchema;
    }

    public static String jdbcUsername() {
        return DB_USER;
    }

    public static String jdbcPassword() {
        return "";
    }

    public static String redisHost() {
        return "127.0.0.1";
    }

    public static int redisPort() {
        return REDIS_PORT;
    }

    private static Process startPostgres() throws IOException, InterruptedException {
        Path postgresDir = Files.createTempDirectory("fern-postgres-data");
        Path toolsDir = preparePostgresToolsDir();
        runCommand(List.of(toolsDir.resolve("initdb").toString(), "-D", postgresDir.toString(), "-A", "trust", "-U", DB_USER, "--no-locale"));

        Process process = new ProcessBuilder(
                toolsDir.resolve("postgres").toString(),
                "-D", postgresDir.toString(),
                "-p", Integer.toString(POSTGRES_PORT),
                "-h", "127.0.0.1"
        ).redirectErrorStream(true).start();

        waitForCommand(List.of(
                resolveCommand("pg_isready", "/opt/homebrew/bin/pg_isready"),
                "-h", "127.0.0.1",
                "-p", Integer.toString(POSTGRES_PORT),
                "-U", DB_USER
        ), Duration.ofSeconds(20));

        runCommand(List.of(
                resolveCommand("createdb", "/opt/homebrew/bin/createdb"),
                "-h", "127.0.0.1",
                "-p", Integer.toString(POSTGRES_PORT),
                "-U", DB_USER,
                DB_NAME
        ));
        return process;
    }

    private static Process startRedis() throws IOException, InterruptedException {
        Path redisDir = Files.createTempDirectory("fern-redis-data");
        Process process = new ProcessBuilder(
                resolveCommand("redis-server", "/opt/homebrew/bin/redis-server"),
                "--port", Integer.toString(REDIS_PORT),
                "--save", "",
                "--appendonly", "no",
                "--dir", redisDir.toString()
        ).redirectErrorStream(true).start();

        waitForCommand(List.of(
                resolveCommand("redis-cli", "/opt/homebrew/bin/redis-cli"),
                "-p", Integer.toString(REDIS_PORT),
                "PING"
        ), Duration.ofSeconds(20));
        return process;
    }

    private static void waitForCommand(List<String> command, Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (process.waitFor() == 0) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Command did not become ready in time: " + command);
    }

    private static void runCommand(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Command failed: " + command);
        }
    }

    private static String resolveCommand(String binaryName, String fallbackPath) {
        if (Files.isExecutable(Path.of(fallbackPath))) {
            return fallbackPath;
        }
        return binaryName;
    }

    private static Path preparePostgresToolsDir() throws IOException {
        Path toolsDir = Files.createTempDirectory("fern-postgres-tools");
        Path initdb = Path.of(resolveCommand("initdb", "/opt/homebrew/bin/initdb"));
        Path postgres = Path.of(resolveCommand("postgres", "/opt/homebrew/bin/postgres"));
        Files.createSymbolicLink(toolsDir.resolve("initdb"), initdb);
        Files.createSymbolicLink(toolsDir.resolve("postgres"), postgres);
        return toolsDir;
    }

    private static int randomPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
