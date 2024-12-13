package com.example.dispatcher.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@RestController
public class DispatcherController {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

    private RestTemplate restTemplate;

    @Value("${WORKER_URLS}")
    private String javaWorkerUrlsString;

    @Value("${PYTHON_WORKER_URLS}")
    private String pythonWorkerUrlsString;

    // мапы для отслеживания состояния воркеров
    private final Map<String, Boolean> javaWorkerStatus = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pythonWorkerStatus = new ConcurrentHashMap<>();

    // Очередь задач
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    // Пул потоков для обработки задач
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);

    @PostConstruct
    public void init() {
        List<String> javaWorkerUrls = Arrays.asList(javaWorkerUrlsString.split(","));
        List<String> pythonWorkerUrls = Arrays.asList(pythonWorkerUrlsString.split(","));
        logger.info("Dispatcher инициализирован с Java Workers: {}", javaWorkerUrls);
        logger.info("Dispatcher инициализирован с Python Workers: {}", pythonWorkerUrls);

        restTemplate = createRestTemplateWithTimeouts();

        for (String workerUrl : javaWorkerUrls) {
            javaWorkerStatus.put(workerUrl.trim(), true);
        }

        for (String workerUrl : pythonWorkerUrls) {
            pythonWorkerStatus.put(workerUrl.trim(), true);
        }

        new Thread(this::dispatchTasks).start();
    }

    @PostMapping("/compile")
    public DeferredResult<ResponseEntity<?>> submitTask(@RequestBody Map<String, String> payload) {
        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(60000L);
        logger.info("Получен запрос на компиляцию с payload: {}", payload);

        deferredResult.onTimeout(() -> {
            logger.error("Время ожидания результата задачи истекло");
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("Время ожидания результата задачи истекло"));
        });

        String taskId = UUID.randomUUID().toString();

        CompletableFuture<Map<String, Object>> futureResult = new CompletableFuture<>();

        futureResult.thenAccept(result -> {
            deferredResult.setResult(ResponseEntity.ok(result));
        }).exceptionally(ex -> {
            logger.error("Ошибка при обработке задачи: {}", ex.getMessage(), ex);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обработке задачи"));
            return null;
        });

        Task task = new Task(taskId, payload, futureResult);

        taskQueue.add(task);
        logger.info("Получена новая задача с taskId: {}", taskId);

        return deferredResult;
    }

    private void dispatchTasks() {
        while (true) {
            try {
                Task task = taskQueue.take();

                String language = task.getPayload().get("language");
                if (language == null) {
                    logger.error("Отсутствует поле 'language' в задаче с taskId: {}", task.getTaskId());
                    task.getFutureResult().completeExceptionally(
                            new IllegalArgumentException("Отсутствует поле 'language' в задаче"));
                    continue;
                }

                Map<String, Boolean> targetWorkerStatus;
                List<String> targetWorkerUrls;

                if (language.equalsIgnoreCase("java")) {
                    targetWorkerStatus = javaWorkerStatus;
                    targetWorkerUrls = new ArrayList<>(javaWorkerStatus.keySet());
                } else if (language.equalsIgnoreCase("python")) {
                    targetWorkerStatus = pythonWorkerStatus;
                    targetWorkerUrls = new ArrayList<>(pythonWorkerStatus.keySet());
                } else {
                    logger.error("Неизвестный язык программирования: {}", language);
                    task.getFutureResult().completeExceptionally(
                            new IllegalArgumentException("Неизвестный язык программирования: " + language));
                    continue;
                }
                String freeWorkerUrl = null;
                while (freeWorkerUrl == null) {
                    for (String workerUrl : targetWorkerUrls) {
                        if (targetWorkerStatus.get(workerUrl)) {
                            freeWorkerUrl = workerUrl;
                            break;
                        }
                    }
                    if (freeWorkerUrl == null) {
                        Thread.sleep(500);
                    }
                }

                targetWorkerStatus.put(freeWorkerUrl, false);
                String workerCompileUrl = freeWorkerUrl + "/compile";

                logger.info("Отправка задачи с taskId: {} на Worker: {}", task.getTaskId(), freeWorkerUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> request = new HttpEntity<>(task.getPayload(), headers);

                final String workerUrlForLambda = freeWorkerUrl;

                CompletableFuture.runAsync(() -> {
                    try {
                        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                                workerCompileUrl,
                                HttpMethod.POST,
                                request,
                                new ParameterizedTypeReference<Map<String, Object>>() {}
                        );
                        logger.info("Получен ответ от Worker '{}': {}", workerUrlForLambda, response.getBody());
                        task.getFutureResult().complete(response.getBody());
                    } catch (Exception e) {
                        logger.error("Ошибка при отправке задачи на Worker '{}': {}", workerUrlForLambda, e.getMessage(), e);
                        task.getFutureResult().completeExceptionally(e);
                    } finally {
                        targetWorkerStatus.put(workerUrlForLambda, true);
                        logger.info("Worker освободился: {}", workerUrlForLambda);
                    }
                }, executorService);

            } catch (InterruptedException e) {
                logger.error("Ошибка в dispatchTasks: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private RestTemplate createRestTemplateWithTimeouts() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(60000);
        return new RestTemplate(requestFactory);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/admin/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Boolean> combinedStatus = new HashMap<>();
        combinedStatus.putAll(javaWorkerStatus);
        combinedStatus.putAll(pythonWorkerStatus);
        return ResponseEntity.ok(combinedStatus);
    }

    @Data
    @AllArgsConstructor
    private static class Task {
        private final String taskId;
        private final Map<String, String> payload;
        private final CompletableFuture<Map<String, Object>> futureResult;
    }

}