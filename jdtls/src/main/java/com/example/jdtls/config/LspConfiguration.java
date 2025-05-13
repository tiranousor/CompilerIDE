package com.example.jdtls.config;

import jakarta.annotation.PreDestroy;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Paths;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Configuration
public class LspConfiguration {

    /** В application.properties:
     *   jdtls.home=C:/jdt-language-server-latest
     */
    @Value("${jdtls.home}")
    private String jdtlsHome;

    private Process jdtlsProcess;

    @Bean
    public Process jdtLsProcess() throws IOException {
        File root = Paths.get(jdtlsHome).toFile();
        if (!root.isDirectory()) {
            throw new IOException("jdtls.home не папка: " + jdtlsHome);
        }

        // **1) Берём ваш конкретный launcher-jar по прямому пути**:
        File launcherJar = new File(root,
                "plugins/org.eclipse.equinox.launcher_1.7.0.v20250424-1814.jar"
        );
        if (!launcherJar.exists()) {
            throw new IOException("Не найден JAR: " + launcherJar);
        }

        // **2) Конфиг под Windows или Linux**:
        String os = System.getProperty("os.name").toLowerCase();
        String cfgName = os.contains("win") ? "config_win" : "config_linux";
        File configDir = new File(root, cfgName);
        if (!configDir.isDirectory()) {
            throw new IOException("Не найдена папка конфигурации: " + configDir);
        }

        // **3) Где jdt.ls будет держать workspace-данные**:
        String dataDir = System.getProperty("java.io.tmpdir") + "/jdtls-workspace";

        // **4) Собираем и запускаем процесс**:
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Declipse.application=org.eclipse.jdt.ls.core.id1",
                "-Dosgi.bundles.defaultStartLevel=4",
                "-Declipse.product=org.eclipse.jdt.ls.core.product",
                "-Xmx1G",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "-jar", launcherJar.getAbsolutePath(),
                "-configuration", configDir.getAbsolutePath(),
                "-data",       dataDir
        );
        pb.directory(root);
        pb.redirectError(Redirect.INHERIT);
        pb.redirectOutput(Redirect.INHERIT);

        this.jdtlsProcess = pb.start();
        return this.jdtlsProcess;
    }

    @Bean
    public LanguageClient languageClient() {
        return new LanguageClient() {
            @Override public void telemetryEvent(Object o) {}
            @Override public void publishDiagnostics(PublishDiagnosticsParams params) {
                System.out.println("DIAGS: " + params);
            }
            @Override public void showMessage(MessageParams messageParams) {}
            @Override public java.util.concurrent.CompletableFuture<MessageActionItem>
            showMessageRequest(ShowMessageRequestParams params) {
                return null;
            }
            @Override public void logMessage(MessageParams message) {}
        };
    }

    @Bean
    public LanguageServer languageServer(Process jdtLsProcess,
                                         LanguageClient client) throws IOException {
        InputStream  in  = jdtLsProcess.getInputStream();
        OutputStream out = jdtLsProcess.getOutputStream();

        Launcher<LanguageServer> launcher =
                LSPLauncher.createClientLauncher(client, in, out);
        Future<?> startFuture = launcher.startListening();

        LanguageServer server = launcher.getRemoteProxy();

        // initialize
        InitializeParams init = new InitializeParams();
        init.setRootUri(Paths.get(jdtlsHome).toUri().toString());
        init.setCapabilities(new ClientCapabilities());
        server.initialize(init).join();

        return server;
    }

    @PreDestroy
    public void stop() {
        if (jdtlsProcess != null && jdtlsProcess.isAlive()) {
            jdtlsProcess.destroyForcibly();
            try { jdtlsProcess.waitFor(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) {}
        }
    }
}
