// src/main/java/com/example/jdtls/services/AnalyzeService.java
package com.example.jdtls.services;

import com.example.jdtls.diagnostics.DiagnosticCollector;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AnalyzeService {

    private final LanguageServer server;
    private final DiagnosticCollector collector;

    public AnalyzeService(LanguageServer server,
                          DiagnosticCollector collector) {
        this.server    = server;
        this.collector = collector;
    }

    /**
     * @param sources – map: URI → полный текст файла
     */
    public Map<String, List<org.eclipse.lsp4j.Diagnostic>> analyze(Map<String,String> sources) {
        // 1) очистить старые diagnostics
        collector.clear();

        // 2) для каждого файла дать open+change
        sources.forEach((uri, text) -> {
            DidOpenTextDocumentParams open = new DidOpenTextDocumentParams();
            open.setTextDocument(new TextDocumentItem(uri, "java", 1, text));
            server.getTextDocumentService().didOpen(open);

            // (необязательно) можно отправить change, если JDT LS не сразу проиндексировал open
            DidChangeTextDocumentParams change = new DidChangeTextDocumentParams();
            change.setTextDocument(new TextDocumentIdentifier(uri));
            change.setContentChanges(
                    Collections.singletonList(new TextDocumentContentChangeEvent(text))
            );
            server.getTextDocumentService().didChange(change);
        });

        // 3) дать JDT LS чуть времени, чтобы он прислал publishDiagnostics
        try { TimeUnit.MILLISECONDS.sleep(200); } catch (InterruptedException ignored){}

        // 4) вернуть snapshot
        return collector.snapshot();
    }
}
