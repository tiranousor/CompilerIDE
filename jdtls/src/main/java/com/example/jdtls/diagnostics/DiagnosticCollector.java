// src/main/java/com/example/jdtls/diagnostics/DiagnosticCollector.java
package com.example.jdtls.diagnostics;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.services.LanguageClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DiagnosticCollector implements LanguageClient {
    // тут накапливаем самые свежие diagnostics по каждому URI
    private final Map<String, List<Diagnostic>> diagnostics = new ConcurrentHashMap<>();

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        diagnostics.put(params.getUri(), params.getDiagnostics());
    }

    // другие методы LanguageClient — пустые реализации
    @Override public void telemetryEvent(Object o) {}
    @Override public void showMessage(org.eclipse.lsp4j.MessageParams messageParams) {}
    @Override public java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem>
    showMessageRequest(org.eclipse.lsp4j.ShowMessageRequestParams params) { return null; }
    @Override public void logMessage(org.eclipse.lsp4j.MessageParams message) {}

    /** Сбрасываем всё перед новым анализом */
    public void clear() {
        diagnostics.clear();
    }
    /** Возвращаем snapshot */
    public Map<String, List<Diagnostic>> snapshot() {
        return new HashMap<>(diagnostics);
    }
}
