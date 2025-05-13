package com.example.jdtls.config;

import com.example.jdtls.diagnostics.DiagnosticCollector;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Configuration
public class LspConfiguration {

    @Value("${jdtls.home}")
    private String jdtlsHome;

    private Process jdtlsProcess;

    @Bean
    public Process jdtLsProcess() throws IOException {
        File root = Paths.get(jdtlsHome).toFile();
        File launcherJar = new File(root,
                "plugins/org.eclipse.equinox.launcher_1.7.0.v20250424-1814.jar");
        File configDir = new File(root,
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "config_win" : "config_linux");

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Declipse.application=org.eclipse.jdt.ls.core.id1",
                "-Dosgi.bundles.defaultStartLevel=4",
                "-Declipse.product=org.eclipse.jdt.ls.core.product",
                "-Xmx1G",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "-jar", launcherJar.getAbsolutePath(),
                "-configuration", configDir.getAbsolutePath(),
                "-data", System.getProperty("java.io.tmpdir") + "/jdtls-workspace"
        );
        pb.directory(root)
                .redirectError(Redirect.INHERIT)
                .redirectOutput(Redirect.INHERIT);

        this.jdtlsProcess = pb.start();
        return this.jdtlsProcess;
    }

    @Bean
    public LanguageClient languageClient(DiagnosticCollector collector) {
        return collector;
    }

    @Bean
    public LanguageServer languageServer(Process jdtLsProcess,
                                         LanguageClient client) throws IOException {
        InputStream in  = jdtLsProcess.getInputStream();
        OutputStream out = jdtLsProcess.getOutputStream();

        Launcher<LanguageServer> launcher =
                LSPLauncher.createClientLauncher(client, in, out);
        launcher.startListening();

        LanguageServer server = launcher.getRemoteProxy();

        // **не блокируемся** — запускаем initialize() в фоне:
        InitializeParams init = new InitializeParams();
        init.setRootUri(Paths.get(jdtlsHome).toUri().toString());
        init.setCapabilities(new ClientCapabilities());
        server.initialize(init)
                .whenComplete((res, err) -> {
                    if (err != null) {
                        System.err.println("LSP initialization failed: " + err);
                    } else {
                        System.out.println("LSP initialized: " + res.getCapabilities());
                    }
                });

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

