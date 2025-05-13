//package com.example.jdtlsproxy.service;
//
//import com.google.gson.Gson;
//import org.eclipse.lsp4j.*;
//import org.eclipse.lsp4j.jsonrpc.Launcher;
//import org.eclipse.lsp4j.services.LanguageClient;
//import org.eclipse.lsp4j.services.LanguageServer;
//import org.springframework.stereotype.Service;
//
//import jakarta.annotation.PostConstruct;
//import java.io.*;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//@Service
//public class JdtlsService {
//    private Process jdtlsProcess;
//    private LanguageServer languageServer;
//    private LanguageClient languageClient;
//    private final Gson gson = new Gson();
//
//    @PostConstruct
//    public void init() throws Exception {
//        // 1. Запускаем JDT LS (последний snapshot) :contentReference[oaicite:3]{index=3}
//        ProcessBuilder pb = new ProcessBuilder(
//                "java", "-jar", "/jdtls/plugins/jdt-language-server-latest.tar.gz",
//                "-configuration", "/jdtls/config_linux",
//                "-data", "/workspace"
//        );
//        jdtlsProcess = pb.start();
//
//        // 2. Создаём JSON-RPC мост
//        InputStream in = jdtlsProcess.getInputStream();
//        OutputStream out = jdtlsProcess.getOutputStream();
//        languageClient = new SimpleLanguageClient();
//        Launcher<LanguageServer> launcher = Launcher.createIoLauncher(
//                languageClient, LanguageServer.class, in, out, null
//        );
//        languageServer = launcher.getRemoteProxy();
//        CompletableFuture<Void> listening = launcher.startListening();
//
//        // 3. Инициализируем LSP-сессию
//        InitializeParams init = new InitializeParams();
//        InitializeResult result = languageServer.initialize(init).get();
//    }
//
//    /**
//     * Получаем авто-фиксы для переданного кода.
//     */
//    public FixResponse fix(FixRequest req) throws Exception {
//        // 1. Открыть документ
//        TextDocumentItem doc = new TextDocumentItem(
//                req.getUri(), "java", 1, req.getContent()
//        );
//        languageServer
//                .getTextDocumentService()
//                .didOpen(new DidOpenTextDocumentParams(doc));
//
//        // 2. Запросить codeAction
//        CodeActionParams params = new CodeActionParams(
//                new TextDocumentIdentifier(req.getUri()),
//                req.getRange(), // диапазон, например весь документ
//                new CodeActionContext(Collections.emptyList())
//        );
//        List<? extends CodeAction> actions =
//                languageServer.getTextDocumentService()
//                        .codeAction(params).get();
//
//        String fixed = req.getContent();
//        if (!actions.isEmpty() && actions.get(0).getEdit() != null) {
//            WorkspaceEdit edit = actions.get(0).getEdit();
//            List<TextEdit> edits = edit.getChanges().get(req.getUri());
//            fixed = applyEdits(fixed, edits);
//        }
//
//        // 3. Вернуть результат
//        return new FixResponse(fixed);
//    }
//
//    private String applyEdits(String original, List<TextEdit> edits) {
//        // Сортируем правки в обратном порядке и применяем
//        edits.sort((a, b) ->
//                Integer.compare(
//                        positionToOffset(original, b.getRange().getStart()),
//                        positionToOffset(original, a.getRange().getStart())
//                )
//        );
//        StringBuilder sb = new StringBuilder(original);
//        for (TextEdit te : edits) {
//            int s = positionToOffset(sb.toString(), te.getRange().getStart());
//            int e = positionToOffset(sb.toString(), te.getRange().getEnd());
//            sb.replace(s, e, te.getNewText());
//        }
//        return sb.toString();
//    }
//
//    private int positionToOffset(String text, Position pos) {
//        // Конвертируем LSP Position в смещение
//        int offset = 0, line = 0;
//        for (char c : text.toCharArray()) {
//            if (line == pos.getLine()) break;
//            if (c == '\n') line++;
//            offset++;
//        }
//        return offset + (int)pos.getCharacter();
//    }
//
//    /** Простейшая реализация клиента, логирующая ответы от сервера. */
//    private static class SimpleLanguageClient implements LanguageClient {
//        @Override public void telemetryEvent(Object o) {}
//        @Override public void publishDiagnostics(PublishDiagnosticsParams p) {
//            System.err.println("Diagnostics: " + p.getDiagnostics());
//        }
//        @Override public void showMessage(MessageParams m) {
//            System.out.println("Message: " + m.getMessage());
//        }
//        @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams p) {
//            return CompletableFuture.completedFuture(new MessageActionItem("OK"));
//        }
//        @Override public void logMessage(MessageParams m) {
//            System.out.println("Log: " + m.getMessage());
//        }
//    }
//
//    // DTO для запроса
//    public static class FixRequest {
//        private String uri;
//        private String content;
//        private Range range = new Range(new Position(0,0), new Position(Integer.MAX_VALUE, Integer.MAX_VALUE));
//        // геттеры/сеттеры...
//    }
//    // DTO для ответа
//    public static class FixResponse {
//        private final String fixedCode;
//        public FixResponse(String fixed) { this.fixedCode = fixed; }
//        public String getFixedCode() { return fixedCode; }
//    }
//}
