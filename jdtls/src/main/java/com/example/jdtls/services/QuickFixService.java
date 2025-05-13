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

    /**
     * @param uri   – URI дока, например "file:///tmp/jdtls-workspace/src/Main.java"
     * @param text  – полный текст
     */
    public CompletableFuture<List<org.eclipse.lsp4j.CodeAction>> getQuickFixes(String uri, String text) {
        // 1) открываем или обновляем документ
        DidOpenTextDocumentParams open = new DidOpenTextDocumentParams();
        TextDocumentItem item = new TextDocumentItem(uri, "java", 1, text);
        open.setTextDocument(item);
        server.getTextDocumentService().didOpen(open);

        // 2) готовим параметры для запроса codeAction
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        // в данном простом примере проверяем весь документ
        params.setRange(new Range(new Position(0,0), new Position( Integer.MAX_VALUE, 0 )));
        params.setContext(new CodeActionContext(Collections.emptyList()));

        // 3) шлём запрос
        CompletableFuture<List<Either<Command, CodeAction>>> future =
                server.getTextDocumentService().codeAction(params);

        // 4) конвертируем ответ в чистые LSP CodeAction’ы
        return future.thenApply(list ->
                list.stream()
                        .filter(Either::isRight)
                        .map(Either::getRight)
                        .collect(Collectors.toList())
        );
    }
}
