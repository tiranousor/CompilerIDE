// src/main/java/com/example/jdtls/QuickFixService.java
package com.example.jdtls.services;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class QuickFixService {

    private final LanguageServer server;

    public QuickFixService(LanguageServer server) {
        this.server = server;
    }

    public CompletableFuture<List<org.eclipse.lsp4j.CodeAction>> getQuickFixes(String uri, String text) {
        // 1) открываем или обновляем документ
        DidOpenTextDocumentParams open = new DidOpenTextDocumentParams();
        TextDocumentItem item = new TextDocumentItem(uri, "java", 1, text);
        open.setTextDocument(item);
        server.getTextDocumentService().didOpen(open);

        // 2) готовим параметры
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setRange(new Range(new Position(0, 0), new Position(Integer.MAX_VALUE, 0)));

        // передаём хотя бы пустой список diagnostics и разрешённые виды ("quickfix"):
        CodeActionContext ctx = new CodeActionContext(
                Collections.emptyList(),
                Collections.singletonList(CodeActionKind.QuickFix)
        );
        params.setContext(ctx);

        // 3) шлём запрос и ОБРАБАТЫВАЕМ ошибки!
        return server.getTextDocumentService().codeAction(params)
                // если LSP упал — просто вернём пустой список
                .exceptionally(ex -> {
                    System.err.println("LSP codeAction failed: " + ex.getMessage());
                    return Collections.emptyList();
                })
                // вытаскиваем из Either только правую часть
                .thenApply(list -> list.stream()
                        .filter(Either::isRight)
                        .map(Either::getRight)
                        .collect(Collectors.toList())
                );
    }
}

