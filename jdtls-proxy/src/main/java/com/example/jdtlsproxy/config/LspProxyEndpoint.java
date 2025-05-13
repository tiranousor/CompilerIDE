//package com.example.jdtlsproxy.endpoint;
//
//import com.google.gson.*;
//import jakarta.websocket.*;
//import jakarta.websocket.server.ServerEndpoint;
//import lombok.extern.slf4j.Slf4j;
//import org.eclipse.lsp4j.*;
//import org.eclipse.lsp4j.jsonrpc.Launcher;
//import org.eclipse.lsp4j.services.*;
//import org.eclipse.lsp4j.websocket.jakarta.WebSocketEndpoint;
//import org.springframework.stereotype.Component;
//
//import java.io.*;
//import java.nio.file.*;
//import java.util.*;
//import java.util.concurrent.*;
//
//@Slf4j
//@Component
//@ServerEndpoint("/lsp/java")
//public class LspProxyEndpoint extends WebSocketEndpoint<LanguageClient> {
//
//    private Process jdtlsProcess;
//    private ExecutorService executorService;
//    private LanguageServer languageServer;
//    private Session session;
//
//    @OnOpen
//    public void onOpen(Session session, EndpointConfig config) {
//        this.session = session;
//        this.executorService = Executors.newCachedThreadPool();
//
//        try {
//            // 1. Создаем workspace
//            Path workspace = Paths.get("/workspace");
//            Files.createDirectories(workspace);
//
//            // 2. Запускаем JDT LS
//            jdtlsProcess = new ProcessBuilder(
//                    "java",
//                    "-jar", "/jdtls/plugins/org.eclipse.equinox.launcher_*.jar",
//                    "-configuration", "/jdtls/config_linux",
//                    "-data", workspace.toString()
//            ).start();
//
//            // 3. Создаем LanguageClient
//            LanguageClient client = new LanguageClient() {
//                @Override public void publishDiagnostics(PublishDiagnosticsParams params) {
//                    sendJsonResponse("diagnostics", params);
//                }
//                @Override public void telemetryEvent(Object object) {}
//                @Override public void showMessage(MessageParams messageParams) {}
//                @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
//                    return CompletableFuture.completedFuture(new MessageActionItem("OK"));
//                }
//                @Override public void logMessage(MessageParams message) {}
//            };
//
//            // 4. Настраиваем соединение
//            Launcher<LanguageServer> launcher = Launcher.createIoLauncher(
//                    LanguageServer.class,
//                    client,
//                    jdtlsProcess.getInputStream(),
//                    jdtlsProcess.getOutputStream(),
//                    executorService,
//                    this::configureLauncher
//            );
//
//            this.languageServer = launcher.getRemoteProxy();
//            launcher.startListening();
//
//            // 5. Инициализируем сервер
//            InitializeParams params = new InitializeParams();
//            params.setRootUri(workspace.toUri().toString());
//
//            languageServer.initialize(params).thenAccept(res -> {
//                log.info("JDT LS initialized");
//                sendJsonResponse("status", "JDT LS initialized");
//            });
//
//        } catch (Exception e) {
//            log.error("Failed to initialize JDT LS", e);
//            sendError("Initialization failed: " + e.getMessage());
//        }
//    }
//
//    @OnMessage
//    public void onMessage(String message) {
//        try {
//            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
//
//            if (json.has("code")) {
//                String code = json.get("code").getAsString();
//                String uri = "file:///workspace/Test.java";
//
//                // Открываем документ
//                DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams();
//                TextDocumentItem document = new TextDocumentItem();
//                document.setText(code);
//                document.setUri(uri);
//                document.setLanguageId("java");
//                document.setVersion(1);
//                openParams.setTextDocument(document);
//                languageServer.getTextDocumentService().didOpen(openParams);
//
//                // Запрашиваем диагностику
//                Thread.sleep(500); // Даем время на анализ
//
//                // Запрашиваем автофиксы
//                CodeActionParams actionParams = new CodeActionParams();
//                actionParams.setTextDocument(new TextDocumentIdentifier(uri));
//                actionParams.setRange(new Range(new Position(0, 0), new Position(100, 0)));
//                actionParams.setContext(new CodeActionContext(List.of()));
//
//                languageServer.getTextDocumentService()
//                        .codeAction(actionParams)
//                        .thenAccept(actions -> {
//                            JsonObject response = new JsonObject();
//                            response.addProperty("code", code);
//                            response.add("actions", JsonParser.parseString(new Gson().toJson(actions)));
//                            sendJsonResponse("analysis", response);
//                        });
//            }
//        } catch (Exception e) {
//            log.error("Error processing message", e);
//            sendError("Processing error: " + e.getMessage());
//        }
//    }
//
//    @OnClose
//    public void onClose(CloseReason reason) {
//        log.info("Closing LSP connection");
//        if (jdtlsProcess != null) jdtlsProcess.destroy();
//        if (executorService != null) executorService.shutdown();
//    }
//
//    @OnError
//    public void onError(Throwable throwable) {
//        log.error("WebSocket error", throwable);
//        sendError("WebSocket error: " + throwable.getMessage());
//    }
//
//    private void configureLauncher(Launcher.Builder<LanguageServer> builder) {
//        builder.setExecutorService(executorService);
//    }
//
//    private void sendJsonResponse(String type, Object data) {
//        try {
//            JsonObject response = new JsonObject();
//            response.addProperty("type", type);
//            response.add("data", JsonParser.parseString(new Gson().toJson(data)));
//            session.getBasicRemote().sendText(response.toString());
//        } catch (Exception e) {
//            log.error("Failed to send response", e);
//        }
//    }
//
//    private void sendError(String message) {
//        try {
//            JsonObject error = new JsonObject();
//            error.addProperty("error", message);
//            session.getBasicRemote().sendText(error.toString());
//        } catch (Exception e) {
//            log.error("Failed to send error", e);
//        }
//    }
//}