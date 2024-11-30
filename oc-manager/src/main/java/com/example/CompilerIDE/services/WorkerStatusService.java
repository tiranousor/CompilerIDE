package com.example.CompilerIDE.services;

import com.example.CompilerIDE.client.CompileClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WorkerStatusService {
    private final CompileClient compileClient;

    private final RestTemplate restTemplate;
    @Value("${dispatcher.url}")
    private String dispatcherUrl;
    @Autowired
    public WorkerStatusService(CompileClient compileClient, RestTemplate restTemplate) {
        this.compileClient = compileClient;
        this.restTemplate = restTemplate;
    }
    public ResponseEntity<Map<String, Boolean>> getWorkerStatus() {
        String url = dispatcherUrl + "/admin/status";
        return restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
    }
//    public Map<String, Boolean> getWorkerStatus() {
//        return compileClient.getWorkerStatus();
//    }
}

