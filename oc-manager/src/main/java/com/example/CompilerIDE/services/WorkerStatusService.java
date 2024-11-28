package com.example.CompilerIDE.services;

import com.example.CompilerIDE.client.CompileClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WorkerStatusService {
    private final CompileClient compileClient;

    public WorkerStatusService(CompileClient compileClient) {
        this.compileClient = compileClient;
    }

//    public Map<String, Boolean> getWorkerStatus() {
//        return compileClient.getWorkerStatus();
//    }
}

