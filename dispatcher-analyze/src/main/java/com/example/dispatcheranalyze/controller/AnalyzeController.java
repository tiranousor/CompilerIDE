// src/main/java/com/example/dispatcheranalyze/controller/AnalyzeController.java
package com.example.dispatcheranalyze.controller;

import com.example.dispatcheranalyze.dto.AnalyzeRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/analyze")
@RequiredArgsConstructor
public class AnalyzeController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeController.class);

    private final SimpMessagingTemplate ws;
    private final RestTemplate         http = new RestTemplate();

    /* ---------- пул workers ---------- */
    @Value("${ANALYZE_WORKER_URLS}")
    private String workerUrls;                       // "http://w1:8079,http://w2:8079"
    private final Map<String,Boolean> workerFree = new ConcurrentHashMap<>();

    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private final ExecutorService     exec  = Executors.newCachedThreadPool();

    @PostConstruct
    void init() {
        Arrays.stream(workerUrls.split(","))
                .map(String::trim)
                .forEach(url -> workerFree.put(url, true));

        new Thread(this::dispatchLoop, "analyze-dispatcher").start();
        log.info("Analyze‑dispatcher started. Workers: {}", workerFree.keySet());
    }

    /* ---------- REST endpoint ---------- */
    @PostMapping
    public Map<String,String> submit(@RequestBody AnalyzeRequest req) {
        String id = UUID.randomUUID().toString();
        queue.add(new Task(id, req));
        return Map.of("taskId", id);                 // клиент сразу знает websocket‑канал
    }

    /* ---------- основная очередь ---------- */
    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) try {
            Task t = queue.take();                   // ждём задачу
            String worker = awaitFreeWorker();       // ждём доступный worker
            workerFree.put(worker, false);

            exec.submit(() -> callWorker(worker, t));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void callWorker(String worker, Task t) {
        try {
            ResponseEntity<List> resp = http.exchange(
                    worker + "/analyze", HttpMethod.POST,
                    new HttpEntity<>(t.payload, json()),
                    List.class);

            ws.convertAndSend("/topic/analyze/" + t.id, resp.getBody());
        } catch (Exception ex) {
            ws.convertAndSend("/topic/analyze/" + t.id,
                    Map.of("error", ex.getMessage()));
            log.error("Analyze task {} failed: {}", t.id, ex.toString());
        } finally {
            workerFree.put(worker, true);
        }
    }

    /* ---------- helpers ---------- */
    private HttpHeaders json() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }

    private String awaitFreeWorker() throws InterruptedException {
        while (true) {
            for (var e : workerFree.entrySet())
                if (e.getValue()) return e.getKey();
            Thread.sleep(120);                       // маленькая задержка
        }
    }

    private record Task(String id, AnalyzeRequest payload) {}
}
