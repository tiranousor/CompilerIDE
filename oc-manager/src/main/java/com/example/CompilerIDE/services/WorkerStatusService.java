package com.example.CompilerIDE.services;

import com.example.CompilerIDE.dto.WorkerMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkerStatusService {
    private static final Logger logger = LoggerFactory.getLogger(WorkerStatusService.class);

    private final RestTemplate restTemplate;

    @Value("${dispatcher.url}")
    private String dispatcherUrl;
    @Value("${prometheus.url}")
    private String prometheusUrl;

    @Autowired
    public WorkerStatusService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Boolean> getWorkerStatus() {
        String url = dispatcherUrl + "/admin/status";
        logger.info("Запрос статуса воркеров по URL: {}", url);
        try {
            ResponseEntity<Map<String, Boolean>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Boolean>>() {}
            );
            logger.info("Получен ответ от Dispatcher: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            logger.error("Ошибка при получении статуса воркеров: {}", e.getMessage(), e);
            return Map.of(); // Возвращаем пустую карту вместо Map.of("error", false)
        }
    }

    public List<WorkerMetrics> getAllWorkerMetrics() {
        // Получаем статусы воркеров
        Map<String, Boolean> workerStatus = getWorkerStatus();

        // Получаем список URL воркеров
        Set<String> workerUrls = workerStatus.keySet();

        List<WorkerMetrics> metricsList = new ArrayList<>();

        for (String url : workerUrls) {
            WorkerMetrics metrics = new WorkerMetrics();
            metrics.setUrl(url);
            metrics.setAvailable(workerStatus.get(url));

            String containerName = getContainerNameFromUrl(url);

            if (containerName == null) {
                metrics.setCpuUsage(0.0);
                metrics.setMemoryUsage(0.0);
            } else {
                double cpuUsage = getCpuUsage(containerName);
                double memoryUsage = getMemoryUsage(containerName);
                metrics.setCpuUsage(cpuUsage);
                metrics.setMemoryUsage(memoryUsage);
            }

            metricsList.add(metrics);
        }

        return metricsList;
    }

    private String getContainerNameFromUrl(String url) {
        // Извлекаем имя контейнера из URL
        // Предполагается, что URL имеет вид http://worker1:5000, извлекаем 'worker1'
        try {
            String withoutProtocol = url.substring(url.indexOf("//") + 2);
            String containerName = withoutProtocol.split(":")[0];
            return containerName;
        } catch (Exception e) {
            logger.error("Ошибка при извлечении имени контейнера из URL '{}': {}", url, e.getMessage());
            return null;
        }
    }

    private double getCpuUsage(String containerName) {
        // Prometheus-запрос для получения использования CPU в процентах
        String query = String.format("sum(rate(container_cpu_usage_seconds_total{container_name=\"%s\"}[1m]))*100", containerName);
        return queryPrometheusMetric(query);
    }

    private double getMemoryUsage(String containerName) {
        // Prometheus-запрос для получения использования памяти в MB
        String query = String.format("container_memory_usage_bytes{container_name=\"%s\"}/1024/1024", containerName);
        return queryPrometheusMetric(query);
    }
    private double queryPrometheusMetric(String query) {
        // Используем UriComponentsBuilder для корректного кодирования параметров
        String url = UriComponentsBuilder.fromHttpUrl(prometheusUrl)
                .path("/api/v1/query")
                .queryParam("query", query)
                .toUriString();

        logger.info("Запрос метрики Prometheus по URL: {}", url);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                String status = (String) body.get("status");
                if ("success".equals(status)) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    List<Map<String, Object>> result = (List<Map<String, Object>>) data.get("result");
                    if (result.isEmpty()) {
                        return 0.0;
                    }
                    Map<String, Object> firstResult = result.get(0);
                    List<Object> values = (List<Object>) firstResult.get("value");
                    if (values.size() < 2) {
                        return 0.0;
                    }
                    String valueStr = (String) values.get(1);
                    return Double.parseDouble(valueStr);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при запросе метрики Prometheus: {}", e.getMessage(), e);
        }
        return 0.0;
    }

}
