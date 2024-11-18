package com.example.dispatcher.controller;

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
    private String workerUrlsString;

    // Карта для отслеживания состояния Worker'ов
    private final Map<String, Boolean> workerStatus = new ConcurrentHashMap<>();

    // Очередь задач
    private final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();

    // Пул потоков для обработки задач
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @PostConstruct
    public void init() {
        List<String> workerUrls = Arrays.asList(workerUrlsString.split(","));
        logger.info("Dispatcher инициализирован с Worker URL: {}", workerUrls);

        restTemplate = createRestTemplateWithTimeouts();

        for (String workerUrl : workerUrls) {
            workerStatus.put(workerUrl, true);
        }

        new Thread(this::dispatchTasks).start();
    }

    @PostMapping("/compile")
    public DeferredResult<ResponseEntity<?>> submitTask(@RequestBody Map<String, String> payload) {
        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(60000L);
        logger.info("Получен запрос на компиляцию с payload: {}", payload);

        deferredResult.onTimeout(() -> {
            logger.error("Время ожидания результата задачи истекло");
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body("Время ожидания результата задачи истекло"));
        });

        String taskId = UUID.randomUUID().toString();

        CompletableFuture<Map<String, Object>> futureResult = new CompletableFuture<>();

        futureResult.thenAccept(result -> {
            deferredResult.setResult(ResponseEntity.ok(result));
        }).exceptionally(ex -> {
            logger.error("Ошибка при обработке задачи: {}", ex.getMessage(), ex);
            deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при обработке задачи"));
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
                // Получаем следующую задачу из очереди
                Task task = taskQueue.take();

                // Ищем свободный Worker
                String freeWorkerUrl = null;
                while (freeWorkerUrl == null) {
                    for (Map.Entry<String, Boolean> entry : workerStatus.entrySet()) {
                        if (entry.getValue()) {
                            freeWorkerUrl = entry.getKey();
                            break;
                        }
                    }
                    if (freeWorkerUrl == null) {
                        Thread.sleep(500);
                    }
                }

                // Отмечаем Worker как занятый
                workerStatus.put(freeWorkerUrl, false);
                String workerCompileUrl = freeWorkerUrl + "/compile";

                logger.info("Отправка задачи с taskId: {} на Worker: {}", task.getTaskId(), freeWorkerUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> request = new HttpEntity<>(task.getPayload(), headers);

                final String workerUrlForLambda = freeWorkerUrl;

                // Отправляем задачу на Worker и получаем результат асинхронно
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
                        workerStatus.put(workerUrlForLambda, true);
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

    @GetMapping("/admin/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(workerStatus);
    }

    private static class Task {
        private final String taskId;
        private final Map<String, String> payload;
        private final CompletableFuture<Map<String, Object>> futureResult;

        public Task(String taskId, Map<String, String> payload, CompletableFuture<Map<String, Object>> futureResult) {
            this.taskId = taskId;
            this.payload = payload;
            this.futureResult = futureResult;
        }

        public String getTaskId() {
            return taskId;
        }

        public Map<String, String> getPayload() {
            return payload;
        }

        public CompletableFuture<Map<String, Object>> getFutureResult() {
            return futureResult;
        }
    }

}
