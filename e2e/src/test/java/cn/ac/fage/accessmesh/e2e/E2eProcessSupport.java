package cn.ac.fage.accessmesh.e2e;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 共用进程机械操作；每条 E2E 仍独立持有进程、容器、身份及业务状态。 */
final class E2eProcessSupport {
    private E2eProcessSupport() {}

    static ServiceHandle launch(String name, String mainClass, List<String> args,
                                Map<String, String> env, String childClasspath) throws IOException {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        int port = parsePort(args);

        Path workDir = Path.of("target", "e2e", name + "-" + System.nanoTime());
        Files.createDirectories(workDir);
        Path logFile = workDir.resolve("process.log");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(childClasspath);
        command.add(mainClass);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        Process process = pb.start();

        ServiceHandle handle = new ServiceHandle(process, port, workDir);
        return handle;
    }

    static final class ServiceHandle {
        final Process process;
        final int port;
        final Path workDir;

        ServiceHandle(Process process, int port, Path workDir) {
            this.process = process;
            this.port = port;
            this.workDir = workDir;
        }

        int port() {
            return port;
        }

        void destroy() {
            process.destroy();
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            assertThat(process.isAlive()).as("子进程必须已退出").isFalse();
        }

        String logTail() {
            try {
                byte[] bytes = Files.readAllBytes(workDir.resolve("process.log"));
                String text = new String(bytes, StandardCharsets.UTF_8);
                List<String> lines = text.lines().toList();
                return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
            } catch (IOException e) {
                return "<log unreadable: " + e.getMessage() + ">";
            }
        }
    }

    static int parsePort(List<String> args) {
        return args.stream()
            .filter(a -> a.startsWith("--server.port="))
            .mapToInt(a -> Integer.parseInt(a.substring("--server.port=".length())))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("缺少 --server.port 参数"));
    }

    static String filterOutTestClasses(String classpath) {
        List<String> kept = new ArrayList<>();
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            String normalized = entry.replace('\\', '/');
            if (!normalized.endsWith("/test-classes")) {
                kept.add(entry);
            }
        }
        return String.join(java.io.File.pathSeparator, kept);
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }
    @FunctionalInterface
    interface ReadinessCheck { void await() throws InterruptedException; }

    static ServiceHandle readyOrDestroy(ServiceHandle handle, ReadinessCheck check) throws InterruptedException {
        try {
            check.await();
            return handle;
        } catch (RuntimeException | Error | InterruptedException failure) {
            try { handle.destroy(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
